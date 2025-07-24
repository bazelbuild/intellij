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

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
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
import com.google.idea.blaze.cpp.copts.CoptsProcessor;
import com.google.idea.blaze.cpp.sync.VirtualIncludesCacheService;
import com.intellij.build.events.MessageEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
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
import com.jetbrains.cidr.lang.workspace.compiler.CachedTempFilesPool;
import com.jetbrains.cidr.lang.workspace.compiler.CompilerInfoCache;
import com.jetbrains.cidr.lang.workspace.compiler.CompilerInfoCache.Message;
import com.jetbrains.cidr.lang.workspace.compiler.CompilerInfoCache.Session;
import com.jetbrains.cidr.lang.workspace.compiler.CompilerSpecificSwitchBuilder;
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerKind;
import com.jetbrains.cidr.lang.workspace.compiler.TempFilesPool;
import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Main entry point for C/CPP configuration data. */
public final class BlazeCWorkspace implements ProjectComponent {

  // Required by OCWorkspaceImpl.ModifiableModel::commit
  // This component is never actually serialized, and this should not ever need to change
  private static final int SERIALIZATION_VERSION = 1;
  private static final Logger LOG = Logger.getInstance(BlazeCWorkspace.class);

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
      LOG.info(
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
                  LOG.info("Skipping update configurations -- no changes");
                } else {
                  Stopwatch s = Stopwatch.createStarted();
                  indicator.setIndeterminate(false);
                  indicator.setText("Updating Configurations...");
                  indicator.setFraction(0.0);
                  WorkspaceModel model =
                      calculateConfigurations(
                          blazeProjectData, workspaceRoot, newResult, indicator);
                  commit(SERIALIZATION_VERSION, context, model, workspaceRoot);
                  LOG.info(
                      String.format(
                          "Update configurations took %dms", s.elapsed(TimeUnit.MILLISECONDS)));
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
          data.compilerSettings().version(),
          config.getDisplayName()
      );

      for (final var target : config.getTargets()) {
        infoMap.put(target, info);
      }
    });

    return infoMap.build();
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

  private static void logResolveConfiguration(BlazeResolveConfiguration config) {
    if (!LOG.isTraceEnabled()) {
      return;
    }

    final var builder = new StringBuilder();
    builder.append(String.format("Configuring resolve configuration: %s\n", config.getDisplayName()));
    builder.append(String.format("-> targets: %s\n", StringUtil.join(config.getTargets(), ", ")));

    final var compiler = config.getCompilerSettings();

    final var includes = compiler.builtInIncludes().stream()
        .map((it) -> it.getAbsoluteOrRelativeFile().toString())
        .collect(Collectors.joining(", "));

    builder.append(String.format("-> compiler: %s (%s)\n", compiler.name(), compiler.version()));
    builder.append(String.format("-> c   compiler: %s\n", compiler.cCompiler()));
    builder.append(String.format("-> c   switches: %s\n", StringUtil.join(compiler.cSwitches(), ", ")));
    builder.append(String.format("-> cpp compiler: %s\n", compiler.cppCompiler()));
    builder.append(String.format("-> cpp switches: %s\n", StringUtil.join(compiler.cppSwitches(), ", ")));
    builder.append(String.format("-> builtin includes: %s\n", includes));
    builder.append(String.format("-> sysroot: %s\n", compiler.sysroot()));

    LOG.trace(builder.toString());
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
    final var executionRootPathResolver = ExecutionRootPathResolver.fromProjectData(project, blazeProjectData);

    int progress = 0;

    for (BlazeResolveConfiguration resolveConfiguration : configurations) {
      logResolveConfiguration(resolveConfiguration);

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
        final var compilerSwitchesBuilder = compilerSettings.createSwitchBuilder();

        CoptsProcessor.apply(
            /* options = */ targetIdeInfo.getcIdeInfo().localCopts(),
            /* kind = */ compilerSettings.getCompilerKind(),
            /* sink = */ compilerSwitchesBuilder,
            /* resolve = */ executionRootPathResolver
        );

        // transitiveDefines are sourced from a target's (and transitive deps) "defines" attribute
        targetIdeInfo.getcIdeInfo().transitiveDefines()
            .forEach(compilerSwitchesBuilder::withMacro);

        Function<ExecutionRootPath, Stream<File>> resolver;
        if (VirtualIncludesCacheService.getEnabled()) {
          resolver = (path) -> Stream.of(executionRootPathResolver.resolveExecutionRootPath(path));
        } else {
          resolver = (path) -> executionRootPathResolver.resolveToIncludeDirectories(path).stream();
        }

        // transitiveIncludeDirectories are sourced from CcSkylarkApiProvider.include_directories
        targetIdeInfo.getcIdeInfo().transitiveIncludeDirectories().stream()
            .flatMap(resolver)
            .filter(configResolveData::isValidHeaderRoot)
            .map(File::getAbsolutePath)
            .forEach(compilerSwitchesBuilder::withIncludePath);

        // transitiveQuoteIncludeDirectories are sourced from
        // CcSkylarkApiProvider.quote_include_directories
        final var quoteIncludePaths = targetIdeInfo.getcIdeInfo()
            .transitiveQuoteIncludeDirectories()
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
        targetIdeInfo.getcIdeInfo().transitiveSystemIncludeDirectories().stream()
            .flatMap(resolver)
            .filter(configResolveData::isValidHeaderRoot)
            .map(File::getAbsolutePath)
            .forEach(compilerSwitchesBuilder::withSystemIncludePath);

        // add includes from a custom sysroot as system includes
        // Note: Only add includes from a custom sysroot manually, CLion can derive the other
        // bulletin includes during the compiler info collection. Manually adding all builtin
        // includes can lead to headers being resolved into the wrong include directory.
        final var sysroot = compilerSettings.sysroot();
        if (sysroot != null) {
          compilerSettings.builtInIncludes().stream()
              .filter((it) -> ExecutionRootPath.isAncestor(sysroot, it, false))
              .flatMap(resolver)
              .map(File::getAbsolutePath)
              .forEach(compilerSwitchesBuilder::withSystemIncludePath);
        }

        VirtualIncludesCacheService.of(project).collectVirtualIncludes(targetIdeInfo)
            .forEach(compilerSwitchesBuilder::withQuoteIncludePath);

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
          blazeProjectData.getBlazeInfo().getExecutionRoot(),
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
    OCCompilerKind compilerKind = compilerSettings.getCompilerKind();
    File executable = compilerSettings.getCompilerExecutable(language);

    final var switchBuilder = compilerSettings.createSwitchBuilder();
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

  private void commit(
      int serialVersion,
      BlazeContext context,
      WorkspaceModel workspaceModel,
      WorkspaceRoot workspaceRoot) {
    collectCompilerSettingsInParallel(context, workspaceModel, workspaceRoot);

    workspaceModel.model.setClientVersion(serialVersion);
    workspaceModel.model.preCommit();

    TransactionGuard.getInstance().submitTransactionAndWait(
        () -> ApplicationManager.getApplication()
            .runWriteAction((Runnable) workspaceModel.model::commit)
    );
  }

  private void collectCompilerSettingsInParallel(
      BlazeContext context,
      WorkspaceModel workspaceModel,
      WorkspaceRoot workspaceRoot) {
    CompilerInfoCache compilerInfoCache = new CompilerInfoCache();
    TempFilesPool tempFilesPool = new CachedTempFilesPool();
    Session<Integer> session = compilerInfoCache.createSession(new EmptyProgressIndicator());

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

      final var frozenMessages =
        messages.freezeValues().values().stream()
          .flatMap(Collection::stream)
          .collect(ImmutableList.toImmutableList());

      for (final var message : frozenMessages) {
        final var kind = switch (message.getType()) {
          case ERROR -> MessageEvent.Kind.ERROR;
          case WARNING -> MessageEvent.Kind.WARNING;
        };

        IssueOutput.issue(kind, "COMPILER INFO COLLECTION")
          .withDescription(message.getText())
          .submit(context);
      }
    } catch (Error | RuntimeException e) {
      session.dispose(); // This calls tempFilesPool.clean();
      throw e;
    }
    tempFilesPool.clean();
  }
}
