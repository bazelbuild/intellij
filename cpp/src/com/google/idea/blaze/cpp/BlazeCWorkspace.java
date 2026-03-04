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
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.workspace.ExecutionRootPathResolver;
import com.google.idea.blaze.cpp.copts.CoptsProcessor;
import com.google.idea.blaze.cpp.sync.HeaderCacheService;
import com.intellij.build.events.MessageEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.MultiMap;
import com.jetbrains.cidr.lang.CLanguageKind;
import com.jetbrains.cidr.lang.OCLanguageKind;
import com.jetbrains.cidr.lang.toolchains.CidrCompilerSwitches;
import com.jetbrains.cidr.lang.toolchains.CidrToolEnvironment;
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration;
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
@Service(Service.Level.PROJECT)
public final class BlazeCWorkspace {

  // Required by OCWorkspaceImpl.ModifiableModel::commit
  // This component is never actually serialized, and this should not ever need to change
  private static final int SERIALIZATION_VERSION = 1;
  private static final Logger LOG = Logger.getInstance(BlazeCWorkspace.class);

  private final BlazeConfigurationResolver configurationResolver;
  private BlazeConfigurationResolverResult resolverResult;
  private final ImmutableList<OCLanguageKind> supportedLanguages =
      ImmutableList.of(CLanguageKind.C, CLanguageKind.CPP);

  private final Project project;

  private BlazeCWorkspace(Project project) {
    this.configurationResolver = new BlazeConfigurationResolver(project);
    this.resolverResult = BlazeConfigurationResolverResult.empty();
    this.project = project;
  }

  public static BlazeCWorkspace getInstance(Project project) {
    return project.getService(BlazeCWorkspace.class);
  }

  public ImmutableList<BlazeResolveConfiguration> getResolveConfigurations() {
    return resolverResult.getAllConfigurations();
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

                  final var model = calculateConfigurations(blazeProjectData, newResult, indicator);
                  commit(SERIALIZATION_VERSION, context, model, workspaceRoot);
                  LOG.info(String.format("Update configurations took %dms", s.elapsed(TimeUnit.MILLISECONDS)));
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
      ExecutionRootPathResolver resolver,
      OCLanguageKind language,
      ImmutableList<String> additionalSwitches
  ) {
    final var combinedBuilder = compilerSettings.createSwitchBuilder();
    combinedBuilder.withSwitches(builder.build());

    CoptsProcessor.apply(
        /* options = */ compilerSettings.getCompilerSwitches(language),
        /* kind = */ compilerSettings.getCompilerKind(),
        /* sink = */ combinedBuilder,
        /* resolver = */ resolver
    );
    CoptsProcessor.apply(
        /* options = */ additionalSwitches,
        /* kind = */ compilerSettings.getCompilerKind(),
        /* sink = */ combinedBuilder,
        /* resolver = */ resolver
    );

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
    final var data = config.getConfigurationData();

    final var includes = compiler.builtInIncludes().stream()
        .map((it) -> it.getAbsoluteOrRelativeFile().toString())
        .collect(Collectors.joining(", "));

    builder.append(String.format("-> compiler: %s (%s)\n", compiler.name(), compiler.version()));
    builder.append(String.format("-> c compiler: %s\n", compiler.cCompiler()));
    builder.append(String.format("-> c switches: %s\n", StringUtil.join(compiler.cSwitches(), ", ")));
    builder.append(String.format("-> cpp compiler: %s\n", compiler.cppCompiler()));
    builder.append(String.format("-> cpp switches: %s\n", StringUtil.join(compiler.cppSwitches(), ", ")));
    builder.append(String.format("-> builtin includes: %s\n", includes));
    builder.append(String.format("-> sysroot: %s\n", compiler.sysroot()));
    builder.append(String.format("-> configuration: %s\n", data.configurationId()));
    builder.append(String.format("-> local copts: %s\n", data.localCopts()));
    builder.append(String.format("-> local cxxopts: %s\n", data.localCxxopts()));
    builder.append(String.format("-> local conlyopts: %s\n", data.localConlyopts()));

    LOG.trace(builder.toString());
  }

  private WorkspaceModel calculateConfigurations(
      BlazeProjectData blazeProjectData,
      BlazeConfigurationResolverResult configResolveData,
      ProgressIndicator indicator
  ) {
    final var workspaceModifiable = OCWorkspaceImpl.getInstanceImpl(project)
        .getModifiableModel(OCWorkspace.LEGACY_CLIENT_KEY, true);

    final var environmentMap = new HashMap<OCResolveConfiguration.ModifiableModel, CidrToolEnvironment>();
    final var configurations = configResolveData.getAllConfigurations();
    final var executionRootPathResolver = ExecutionRootPathResolver.fromProjectData(project, blazeProjectData);

    int progress = 0;

    for (final var resolveConfiguration : configurations) {
      logResolveConfiguration(resolveConfiguration);

      indicator.setText2(resolveConfiguration.getDisplayName());
      indicator.setFraction(((double) progress) / configurations.size());

      final var compilerSettings = resolveConfiguration.getCompilerSettings();
      final var configLanguages = new HashMap<OCLanguageKind, PerLanguageCompilerOpts>();
      final var configSourceFiles = new HashMap<VirtualFile, PerFileCompilerOpts>();

      // All targets in a resolve configuration share the same flags, defines, and includes
      // (they are grouped by equivalence class). Compute switches once per configuration.
      final var configData = resolveConfiguration.getConfigurationData();
      final var compilerSwitchesBuilder = compilerSettings.createSwitchBuilder();

      CoptsProcessor.apply(
          /* options = */ configData.localCopts(),
          /* kind = */ compilerSettings.getCompilerKind(),
          /* sink = */ compilerSwitchesBuilder,
          /* resolver = */ executionRootPathResolver
      );

      // transitiveDefines are sourced from a target's (and transitive deps) "defines" attribute
      configData.transitiveDefines().forEach(compilerSwitchesBuilder::withMacro);

      final Function<ExecutionRootPath, Stream<File>> resolver;
      if (HeaderCacheService.getEnabled()) {
        final var includesCache = HeaderCacheService.of(project);

        resolver = executionRootPath -> Stream.of(includesCache
            .resolve(configData.configurationId(), executionRootPath)
            .map(Path::toFile)
            .orElseGet(() -> executionRootPathResolver.resolveExecutionRootPath(executionRootPath))
        );
      } else {
        // legacy resolver, use `resolveToIncludesDirectories` and filter with `HeaderRootsTrimmer`
        resolver = executionRootPath -> executionRootPathResolver
            .resolveToIncludeDirectories(executionRootPath)
            .stream()
            .filter(configResolveData::isValidHeaderRoot);
      }

      // transitiveIncludeDirectories are sourced from CcSkylarkApiProvider.include_directories
      configData.transitiveIncludeDirectories().stream()
          .flatMap(resolver)
          .map(File::getAbsolutePath)
          .forEach(compilerSwitchesBuilder::withIncludePath);

      // transitiveQuoteIncludeDirectories are sourced from CcSkylarkApiProvider.quote_include_directories
      final var quoteIncludePaths = configData.transitiveQuoteIncludeDirectories().stream()
          .flatMap(resolver)
          .map(File::getAbsolutePath)
          .collect(ImmutableList.toImmutableList());
      quoteIncludePaths.forEach(compilerSwitchesBuilder::withQuoteIncludePath);

      // transitiveSystemIncludeDirectories are sourced from CcSkylarkApiProvider.system_include_directories
      // Note: We would ideally use -isystem here, but it interacts badly with the switches that get built by
      // ClangUtils::addIncludeDirectories (it uses -I for system libraries).
      configData.transitiveSystemIncludeDirectories().stream()
          .flatMap(resolver)
          .map(File::getAbsolutePath)
          .forEach(compilerSwitchesBuilder::withSystemIncludePath);

      final var cCompilerSwitches = buildSwitchBuilder(
          compilerSettings,
          compilerSwitchesBuilder,
          executionRootPathResolver,
          CLanguageKind.C,
          configData.localConlyopts()
      );

      final var cppCompilerSwitches = buildSwitchBuilder(
          compilerSettings,
          compilerSwitchesBuilder,
          executionRootPathResolver,
          CLanguageKind.CPP,
          configData.localCxxopts()
      );

      for (final var target : resolveConfiguration.getTargets()) {
        for (final var vf : resolveConfiguration.getSources(target)) {
          final var kind = resolveConfiguration.getDeclaredLanguageKind(project, vf);

          final PerFileCompilerOpts perFileCompilerOpts;
          if (kind == CLanguageKind.C) {
            perFileCompilerOpts = new PerFileCompilerOpts(kind, cCompilerSwitches);
          } else {
            perFileCompilerOpts = new PerFileCompilerOpts(CLanguageKind.CPP, cppCompilerSwitches);
          }
          configSourceFiles.put(vf, perFileCompilerOpts);

          if (!configLanguages.containsKey(kind)) {
            // If a file isn't found in configSourceFiles (newly created files), CLion uses the
            // configLanguages switches. We want some basic header search roots (genfiles),
            // which are part of every target's iquote directories. See:
            // https://github.com/bazelbuild/bazel/blob/2c493e8a2132d54f4b2fb8046f6bcef11e92cd22/src/main/java/com/google/devtools/build/lib/rules/cpp/CcCompilationHelper.java#L911
            addConfigLanguageSwitches(configLanguages, compilerSettings, quoteIncludePaths, kind);
          }
        }
      }

      for (OCLanguageKind language : supportedLanguages) {
        if (!configLanguages.containsKey(language)) {
          addConfigLanguageSwitches(configLanguages, compilerSettings, ImmutableList.of(), language);
        }
      }

      final var id = resolveConfiguration.getDisplayName();
      final var modelConfig = addConfiguration(
          /* workspaceModifiable = */ workspaceModifiable,
          /* id = */ id,
          /* displayName = */ id,
          /* directory = */ blazeProjectData.blazeInfo().getExecutionRoot(),
          /* configLanguages = */ configLanguages,
          /* configSourceFiles = */ configSourceFiles
      );

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
      Map<VirtualFile, PerFileCompilerOpts> configSourceFiles
  ) {
    final var config = workspaceModifiable.addConfiguration(
        /* id = */ id,
        /* name = */ displayName,
        /* variant = */ null,
        /* fileSeparators = */ OCResolveConfiguration.DEFAULT_FILE_SEPARATORS
    );

    for (final var languageEntry : configLanguages.entrySet()) {
      final var configForLanguage = languageEntry.getValue();
      final var langSettings = config.getLanguageCompilerSettings(languageEntry.getKey());
      langSettings.setCompiler(configForLanguage.kind, configForLanguage.compiler, directory);
      langSettings.setCompilerSwitches(configForLanguage.switches);
    }

    for (final var fileEntry : configSourceFiles.entrySet()) {
      final var compilerOpts = fileEntry.getValue();
      final var fileCompilerSettings = config.addSource(fileEntry.getKey(), compilerOpts.kind);
      fileCompilerSettings.setCompilerSwitches(compilerOpts.switches);
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
    switchBuilder.withSwitches(compilerSettings.getCompilerSwitches(language));
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
