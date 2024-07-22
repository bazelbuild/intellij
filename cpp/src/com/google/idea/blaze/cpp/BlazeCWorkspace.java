/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.idea.blaze.cpp;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.errorprone.annotations.Keep;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.workspace.ExecutionRootPathResolver;
import com.google.idea.blaze.base.sync.workspace.VirtualIncludesHandler;
import com.intellij.ide.actions.ShowFilePathAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.jetbrains.cidr.lang.CLanguageKind;
import com.jetbrains.cidr.lang.OCLanguageKind;
import com.jetbrains.cidr.lang.toolchains.CidrCompilerSwitches;
import com.jetbrains.cidr.lang.toolchains.CidrSwitchBuilder;
import com.jetbrains.cidr.lang.toolchains.CidrToolEnvironment;
import com.jetbrains.cidr.lang.workspace.OCCompilerSettings;
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration;
import com.jetbrains.cidr.lang.workspace.OCResolveConfigurationImpl;
import com.jetbrains.cidr.lang.workspace.OCWorkspace;
import com.jetbrains.cidr.lang.workspace.OCWorkspaceEventImpl;
import com.jetbrains.cidr.lang.workspace.OCWorkspaceImpl;
import com.jetbrains.cidr.lang.workspace.OCWorkspaceModificationTrackersImpl;
import com.jetbrains.cidr.lang.workspace.compiler.AppleClangSwitchBuilder;
import com.jetbrains.cidr.lang.workspace.compiler.CachedTempFilesPool;
import com.jetbrains.cidr.lang.workspace.compiler.ClangSwitchBuilder;
import com.jetbrains.cidr.lang.workspace.compiler.CompilerInfoCache;
import com.jetbrains.cidr.lang.workspace.compiler.CompilerInfoCache.Message;
import com.jetbrains.cidr.lang.workspace.compiler.CompilerInfoCache.Session;
import com.jetbrains.cidr.lang.workspace.compiler.CompilerSpecificSwitchBuilder;
import com.jetbrains.cidr.lang.workspace.compiler.GCCSwitchBuilder;
import com.jetbrains.cidr.lang.workspace.compiler.MSVCSwitchBuilder;
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerKind;
import com.jetbrains.cidr.lang.workspace.compiler.TempFilesPool;
import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Stream;

/** Main entry point for C/CPP configuration data. */
public final class BlazeCWorkspace implements ProjectComponent {
  // Required by OCWorkspaceImpl.ModifiableModel::commit
  // This component is never actually serialized, and this should not ever need to change
  private static final int SERIALIZATION_VERSION = 1;
  private static final Logger logger = Logger.getInstance(BlazeCWorkspace.class);

  private final BlazeConfigurationResolver configurationResolver;
  private BlazeConfigurationResolverResult resolverResult;
  private final ImmutableList<OCLanguageKind> supportedLanguages =
      ImmutableList.of(CLanguageKind.C, CLanguageKind.CPP);

  private final Project project;

  @Keep // Instantiated as an IntelliJ project component.
  private BlazeCWorkspace(Project project) {
    this.configurationResolver = new BlazeConfigurationResolver(project);
    this.resolverResult = BlazeConfigurationResolverResult.empty();
    this.project = project;
  }

  public static BlazeCWorkspace getInstance(Project project) {
    return project.getComponent(BlazeCWorkspace.class);
  }

  @Override
  public void projectOpened() {
    if (Blaze.getProjectType(project) == ProjectType.QUERY_SYNC) {
      return;
    }
    CMakeWorkspaceOverride.undoCMakeModifications(project);
  }

  public void update(
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      BlazeProjectData blazeProjectData,
      SyncMode syncMode) {
    if (syncMode.equals(SyncMode.STARTUP)
        && !OCWorkspace.getInstance(project).getConfigurations().isEmpty()) {
      logger.info(
          String.format(
              "OCWorkspace already loaded %d configurations -- skipping update",
              OCWorkspace.getInstance(project).getConfigurations().size()));
      return;
    }
    BlazeConfigurationResolverResult oldResult = resolverResult;
    BlazeConfigurationResolverResult newResult =
        configurationResolver.update(
            context, workspaceRoot, projectViewSet, blazeProjectData, oldResult);
    BlazeCTargetInfoService.setState(project, calculatePersistentInformation(newResult));
    // calculateConfigurations is expensive, so run async without a read lock (b/78570947)
    ProgressManager.getInstance()
        .run(
            new Task.Backgroundable(project, "Configuration Sync", false) {
              @Override
              public void run(ProgressIndicator indicator) {
                if (!syncMode.equals(SyncMode.FULL)
                    && oldResult.isEquivalentConfigurations(newResult)) {
                  logger.info("Skipping update configurations -- no changes");
                } else {
                  Stopwatch s = Stopwatch.createStarted();
                  indicator.setIndeterminate(false);
                  indicator.setText("Updating Configurations...");
                  indicator.setFraction(0.0);
                  WorkspaceModel model =
                      calculateConfigurations(
                          blazeProjectData, workspaceRoot, newResult, indicator);
                  ImmutableList<String> issues =
                      commit(SERIALIZATION_VERSION, model, workspaceRoot);
                  logger.info(
                      String.format(
                          "Update configurations took %dms", s.elapsed(TimeUnit.MILLISECONDS)));
                  if (!issues.isEmpty()) {
                    showSetupIssues(issues, context);
                  }
                }
                resolverResult = newResult;
                incModificationTrackers();
              }
            });
  }

  private ImmutableMap<TargetKey, BlazeCTargetInfoService.TargetInfo> calculatePersistentInformation(
      BlazeConfigurationResolverResult resolverResult) {

    final var infoMap = new ImmutableMap.Builder<TargetKey, BlazeCTargetInfoService.TargetInfo>();
    resolverResult.getConfigurationMap().forEach((data, config) -> {
      final var info = new BlazeCTargetInfoService.TargetInfo(
          data.compilerSettings.getCompilerVersion(),
          config.getDisplayName()
      );

      for (final var target : config.getTargets()) {
        infoMap.put(target, info);
      }
    });

    return infoMap.build();
  }

  private static CompilerSpecificSwitchBuilder selectSwitchBuilder(
      BlazeCompilerSettings compilerSettings) {
    final var version = compilerSettings.getCompilerVersion();

    if (CompilerVersionUtil.isAppleClang(version)) {
      return new AppleClangSwitchBuilder();
    }
    if (CompilerVersionUtil.isClang(version)) {
      return new ClangSwitchBuilder();
    }
    if (CompilerVersionUtil.isMSVC(version)) {
      return new MSVCSwitchBuilder();
    }

    // default to gcc
    return new GCCSwitchBuilder();
  }

  private static CidrCompilerSwitches buildSwitchBuilder(
      BlazeCompilerSettings compilerSettings,
      CompilerSpecificSwitchBuilder builder,
      OCLanguageKind language) {
    final var combinedBuilder = new CidrSwitchBuilder();
    combinedBuilder.addAllRaw(compilerSettings.getCompilerSwitches(language, null));
    combinedBuilder.addAll(builder.build());

    return combinedBuilder.build();
  }

  private WorkspaceModel calculateConfigurations(
      BlazeProjectData blazeProjectData,
      WorkspaceRoot workspaceRoot,
      BlazeConfigurationResolverResult configResolveData,
      ProgressIndicator indicator) {

    OCWorkspaceImpl.ModifiableModel workspaceModifiable =
        OCWorkspaceImpl.getInstanceImpl(project)
            .getModifiableModel(OCWorkspace.LEGACY_CLIENT_KEY, true);
    Map<OCResolveConfiguration.ModifiableModel, CidrToolEnvironment> environmentMap = new HashMap<>();
    ImmutableList<BlazeResolveConfiguration> configurations =
        configResolveData.getAllConfigurations();
    ExecutionRootPathResolver executionRootPathResolver =
        new ExecutionRootPathResolver(
            Blaze.getBuildSystemProvider(project),
            workspaceRoot,
            blazeProjectData.getBlazeInfo().getExecutionRoot(),
            blazeProjectData.getBlazeInfo().getOutputBase(),
            blazeProjectData.getWorkspacePathResolver(),
            blazeProjectData.getTargetMap());

    int progress = 0;

    for (BlazeResolveConfiguration resolveConfiguration : configurations) {
      indicator.setText2(resolveConfiguration.getDisplayName());
      indicator.setFraction(((double) progress) / configurations.size());
      BlazeCompilerSettings compilerSettings = resolveConfiguration.getCompilerSettings();
      Map<OCLanguageKind, PerLanguageCompilerOpts> configLanguages = new HashMap<>();
      Map<VirtualFile, PerFileCompilerOpts> configSourceFiles = new HashMap<>();
      for (TargetKey targetKey : resolveConfiguration.getTargets()) {
        TargetIdeInfo targetIdeInfo = blazeProjectData.getTargetMap().get(targetKey);
        if (targetIdeInfo == null || targetIdeInfo.getcIdeInfo() == null) {
          continue;
        }

        // defines and include directories are the same for all sources in a given target, so lets
        // collect them once and reuse for each source file's options
        final var compilerSwitchesBuilder = selectSwitchBuilder(compilerSettings);

        // this parses user defined copts filed, later -I include paths are resolved using the
        // ExecutionRootPathResolver
        // TODO: this can either be dropped or we might need to add support for other include types
        UnfilteredCompilerOptions coptsExtractor = UnfilteredCompilerOptions.builder()
            .registerSingleOrSplitOption("-I")
            .build(targetIdeInfo.getcIdeInfo().getLocalCopts());

        // forward user defined switches either directly or filter them first
        final var plainLocalCopts = coptsExtractor.getUninterpretedOptions();
        if (Registry.is("bazel.cpp.sync.workspace.filter.out.incompatible.flags")) {
          compilerSwitchesBuilder.withSwitches(filterIncompatibleFlags(plainLocalCopts));
        } else {
          compilerSwitchesBuilder.withSwitches(plainLocalCopts);
        }

        // transitiveDefines are sourced from a target's (and transitive deps) "defines" attribute
        targetIdeInfo.getcIdeInfo().getTransitiveDefines()
            .forEach(compilerSwitchesBuilder::withMacro);

        Function<ExecutionRootPath, Stream<File>> resolver =
            executionRootPath ->
                executionRootPathResolver.resolveToIncludeDirectories(executionRootPath).stream();

        // localIncludes are sourced from -I options in a target's "copts" attribute. They can be
        // arbitrarily declared and may not exist in configResolveData.
        coptsExtractor.getExtractedOptionValues("-I").stream()
            .map(ExecutionRootPath::new)
            .flatMap(resolver)
            .map(File::getAbsolutePath)
            .forEach(compilerSwitchesBuilder::withIncludePath);

        // transitiveIncludeDirectories are sourced from CcSkylarkApiProvider.include_directories
        targetIdeInfo.getcIdeInfo().getTransitiveIncludeDirectories().stream()
            .flatMap(resolver)
            .filter(configResolveData::isValidHeaderRoot)
            .map(File::getAbsolutePath)
            .forEach(compilerSwitchesBuilder::withIncludePath);

        // transitiveQuoteIncludeDirectories are sourced from
        // CcSkylarkApiProvider.quote_include_directories
        final var quoteIncludePaths = targetIdeInfo.getcIdeInfo()
            .getTransitiveQuoteIncludeDirectories()
            .stream()
            .flatMap(resolver)
            .filter(configResolveData::isValidHeaderRoot)
            .map(File::getAbsolutePath)
            .collect(ImmutableList.toImmutableList());
        quoteIncludePaths.forEach(compilerSwitchesBuilder::withQuoteIncludePath);

        // transitiveSystemIncludeDirectories are sourced from
        // CcSkylarkApiProvider.system_include_directories
        // Note: We would ideally use -isystem here, but it interacts badly with the switches
        // that get built by ClangUtils::addIncludeDirectories (it uses -I for system libraries).
        targetIdeInfo.getcIdeInfo().getTransitiveSystemIncludeDirectories().stream()
            .flatMap(resolver)
            .filter(configResolveData::isValidHeaderRoot)
            .map(File::getAbsolutePath)
            .forEach(compilerSwitchesBuilder::withSystemIncludePath);

        if (VirtualIncludesHandler.useHints()) {
          compilerSwitchesBuilder.withSwitches(VirtualIncludesHandler.collectIncludeHints(
              workspaceRoot.directory().toPath(),
              targetKey,
              blazeProjectData,
              executionRootPathResolver,
              indicator));
        }

        final var cCompilerSwitches =
            buildSwitchBuilder(compilerSettings, compilerSwitchesBuilder, CLanguageKind.C);
        final var cppCompilerSwitches =
            buildSwitchBuilder(compilerSettings, compilerSwitchesBuilder, CLanguageKind.CPP);

        for (VirtualFile vf : resolveConfiguration.getSources(targetKey)) {
          OCLanguageKind kind = resolveConfiguration.getDeclaredLanguageKind(vf);

          final PerFileCompilerOpts perFileCompilerOpts;
          if (kind == CLanguageKind.C) {
            perFileCompilerOpts = new PerFileCompilerOpts(kind, cCompilerSwitches);
          } else {
            perFileCompilerOpts = new PerFileCompilerOpts(CLanguageKind.CPP, cppCompilerSwitches);
          }
          configSourceFiles.put(vf, perFileCompilerOpts);

          if (!configLanguages.containsKey(kind)) {
            addConfigLanguageSwitches(
                configLanguages, compilerSettings,
                // If a file isn't found in configSourceFiles (newly created files), CLion uses the
                // configLanguages switches. We want some basic header search roots (genfiles),
                // which are part of every target's iquote directories. See:
                // https://github.com/bazelbuild/bazel/blob/2c493e8a2132d54f4b2fb8046f6bcef11e92cd22/src/main/java/com/google/devtools/build/lib/rules/cpp/CcCompilationHelper.java#L911
                quoteIncludePaths, kind);
          }
        }
      }

      for (OCLanguageKind language : supportedLanguages) {
        if (!configLanguages.containsKey(language)) {
          addConfigLanguageSwitches(
              configLanguages, compilerSettings, ImmutableList.of(), language);
        }
      }

      String id = resolveConfiguration.getDisplayName();

      OCResolveConfiguration.ModifiableModel modelConfig = addConfiguration(
          workspaceModifiable,
          id,
          id,
          workspaceRoot.directory(),
          configLanguages,
          configSourceFiles);

      environmentMap.put(modelConfig, CppEnvironmentProvider.createEnvironment(compilerSettings));

      progress++;
    }

    return new WorkspaceModel(workspaceModifiable, environmentMap);
  }

  private static OCResolveConfiguration.ModifiableModel addConfiguration(
      OCWorkspaceImpl.ModifiableModel workspaceModifiable,
      String id,
      String displayName,
      File directory,
      Map<OCLanguageKind, PerLanguageCompilerOpts> configLanguages,
      Map<VirtualFile, PerFileCompilerOpts> configSourceFiles) {
    OCResolveConfigurationImpl.ModifiableModel config =
        workspaceModifiable.addConfiguration(
            id, displayName, null, OCResolveConfiguration.DEFAULT_FILE_SEPARATORS);
    for (Map.Entry<OCLanguageKind, PerLanguageCompilerOpts> languageEntry :
        configLanguages.entrySet()) {
      PerLanguageCompilerOpts configForLanguage = languageEntry.getValue();
      if (CppSupportChecker.isSupportedCppConfiguration(
          configForLanguage.switches, directory.toPath())) {
        OCCompilerSettings.ModifiableModel langSettings =
            config.getLanguageCompilerSettings(languageEntry.getKey());
        langSettings.setCompiler(configForLanguage.kind, configForLanguage.compiler, directory);
        langSettings.setCompilerSwitches(configForLanguage.switches);
      }
    }

    for (Map.Entry<VirtualFile, PerFileCompilerOpts> fileEntry : configSourceFiles.entrySet()) {
      PerFileCompilerOpts compilerOpts = fileEntry.getValue();
      if (CppSupportChecker.isSupportedCppConfiguration(
          compilerOpts.switches, directory.toPath())) {
        OCCompilerSettings.ModifiableModel fileCompilerSettings =
            config.addSource(fileEntry.getKey(), compilerOpts.kind);
        fileCompilerSettings.setCompilerSwitches(compilerOpts.switches);
      }
    }

    return config;
  }
  /** Group compiler options for a specific file. */
  private static class PerFileCompilerOpts {
    final OCLanguageKind kind;
    final CidrCompilerSwitches switches;

    private PerFileCompilerOpts(OCLanguageKind kind, CidrCompilerSwitches switches) {
      this.kind = kind;
      this.switches = switches;
    }
  }

  /** Group compiler options for a specific language. */
  private static class PerLanguageCompilerOpts {
    final OCCompilerKind kind;
    final File compiler;
    final CidrCompilerSwitches switches;

    private PerLanguageCompilerOpts(
        OCCompilerKind kind, File compiler, CidrCompilerSwitches switches) {
      this.kind = kind;
      this.compiler = compiler;
      this.switches = switches;
    }
  }

  private static class WorkspaceModel {

    final OCWorkspaceImpl.ModifiableModel model;
    final Map<OCResolveConfiguration.ModifiableModel, CidrToolEnvironment> environments;

    private WorkspaceModel(
        OCWorkspace.ModifiableModel model,
        Map<OCResolveConfiguration.ModifiableModel, CidrToolEnvironment> environments) {
      this.model = model;
      this.environments = environments;
    }
  }

  private void addConfigLanguageSwitches(
      Map<OCLanguageKind, PerLanguageCompilerOpts> configLanguages,
      BlazeCompilerSettings compilerSettings,
      List<String> quoteIncludePaths,
      OCLanguageKind language) {
    OCCompilerKind compilerKind = compilerSettings.getCompiler(language);
    File executable = compilerSettings.getCompilerExecutable(language);

    final var switchBuilder = selectSwitchBuilder(compilerSettings);
    switchBuilder.withSwitches(compilerSettings.getCompilerSwitches(language, null));
    quoteIncludePaths.forEach(switchBuilder::withQuoteIncludePath);

    PerLanguageCompilerOpts perLanguageCompilerOpts =
        new PerLanguageCompilerOpts(compilerKind, executable, switchBuilder.build());
    configLanguages.put(language, perLanguageCompilerOpts);
  }

  /**
   * Notifies the workspace of changes in inputs to the resolve configuration. See {@link
   * com.jetbrains.cidr.lang.workspace.OCWorkspaceListener.OCWorkspaceEvent}.
   */
  private void incModificationTrackers() {
    TransactionGuard.submitTransaction(
        project,
        () -> {
          if (project.isDisposed()) {
            return;
          }
          OCWorkspaceEventImpl event =
              new OCWorkspaceEventImpl(
                  /* resolveConfigurationsChanged= */ true,
                  /* sourceFilesChanged= */ true,
                  /* compilerSettingsChanged= */ true,
                  /* clientVersionChanged */ false);
          ((OCWorkspaceModificationTrackersImpl)
                  OCWorkspace.getInstance(project).getModificationTrackers())
              .fireWorkspaceChanged(event);
        });
  }

  public OCWorkspace getWorkspace() {
    return OCWorkspace.getInstance(project);
  }

  // Filter out any raw copts that aren't compatible with feature detection.
  private static ImmutableList<String> filterIncompatibleFlags(List<String> copts) {
    return copts.stream()
        // "-include somefile.h" doesn't seem to work for some reason. E.g.,
        // "-include cstddef" results in "clang: error: no such file or directory: 'cstddef'"
        .filter(opt -> !opt.startsWith("-include "))
        .collect(toImmutableList());
  }

  private static void showSetupIssues(ImmutableList<String> issues, BlazeContext context) {
    logger.warn(
        String.format(
            "Issues collecting info from C++ compiler. Showing first few out of %d:\n%s",
            issues.size(), Iterables.limit(issues, 25)));
    IssueOutput.warn("Issues collecting info from C++ compiler (click to see logs)")
        .navigatable(
            new Navigatable() {
              @Override
              public void navigate(boolean b) {
                ShowFilePathAction.openFile(new File(PathManager.getLogPath(), "idea.log"));
              }

              @Override
              public boolean canNavigate() {
                return true;
              }

              @Override
              public boolean canNavigateToSource() {
                return false;
              }
            })
        .submit(context);
  }

  private ImmutableList<String> commit(
      int serialVersion,
      WorkspaceModel workspaceModel,
      WorkspaceRoot workspaceRoot) {
    final var issues = collectCompilerSettingsInParallel(workspaceModel, workspaceRoot);

    workspaceModel.model.setClientVersion(serialVersion);
    workspaceModel.model.preCommit();

    TransactionGuard.getInstance().submitTransactionAndWait(
        () -> ApplicationManager.getApplication()
            .runWriteAction((Runnable) workspaceModel.model::commit)
    );

    return issues;
  }

  private ImmutableList<String> collectCompilerSettingsInParallel(
      WorkspaceModel workspaceModel,
      WorkspaceRoot workspaceRoot) {
    CompilerInfoCache compilerInfoCache = new CompilerInfoCache();
    TempFilesPool tempFilesPool = new CachedTempFilesPool();
    Session<Integer> session = compilerInfoCache.createSession(new EmptyProgressIndicator());
    ImmutableList.Builder<String> issues = ImmutableList.builder();

    try {
      int i = 0;
      for (OCResolveConfiguration.ModifiableModel config : workspaceModel.model.getConfigurations()) {
        session.schedule(
            i++,
            config,
            workspaceModel.environments.get(config),
            workspaceRoot.directory().getAbsolutePath());
      }
      MultiMap<Integer, Message> messages = new MultiMap<>();
      session.waitForAll(messages);
      for (Map.Entry<Integer, Collection<Message>> entry :
          ContainerUtil.sorted(messages.entrySet(), Comparator.comparingInt(Map.Entry::getKey))) {
        entry.getValue().stream()
            .filter(m -> m.getType().equals(Message.Type.ERROR))
            .map(Message::getText)
            .forEachOrdered(issues::add);
      }
    } catch (Error | RuntimeException e) {
      session.dispose(); // This calls tempFilesPool.clean();
      throw e;
    }
    tempFilesPool.clean();
    return issues.build();
  }
}
