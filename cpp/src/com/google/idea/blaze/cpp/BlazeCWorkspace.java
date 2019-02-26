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
import com.google.common.collect.Iterables;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.workspace.ExecutionRootPathResolver;
import com.google.idea.sdkcompat.cidr.OCWorkspaceModifiableModelAdapter;
import com.google.idea.sdkcompat.cidr.OCWorkspaceModifiableModelAdapter.PerFileCompilerOpts;
import com.google.idea.sdkcompat.cidr.OCWorkspaceModifiableModelAdapter.PerLanguageCompilerOpts;
import com.intellij.ide.actions.ShowFilePathAction;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.util.NullableFunction;
import com.jetbrains.cidr.lang.OCLanguageKind;
import com.jetbrains.cidr.lang.toolchains.CidrSwitchBuilder;
import com.jetbrains.cidr.lang.toolchains.CidrToolEnvironment;
import com.jetbrains.cidr.lang.workspace.OCWorkspace;
import com.jetbrains.cidr.lang.workspace.OCWorkspaceImpl;
import com.jetbrains.cidr.lang.workspace.OCWorkspaceModificationTrackers;
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerKind;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/** Main entry point for C/CPP configuration data. */
public final class BlazeCWorkspace implements ProjectComponent {
  // Required by OCWorkspaceImpl.ModifiableModel::commit
  // This component is never actually serialized, and this should not ever need to change
  private static final int SERIALIZATION_VERSION = 1;
  private static final Logger logger = Logger.getInstance(BlazeCWorkspace.class);

  private final BlazeConfigurationResolver configurationResolver;
  private BlazeConfigurationResolverResult resolverResult;
  private final ImmutableList<OCLanguageKind> supportedLanguages =
      ImmutableList.of(OCLanguageKind.C, OCLanguageKind.CPP);

  private final Project project;
  private final CidrToolEnvironment toolEnvironment = new CidrToolEnvironment();

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
                  NullableFunction<File, VirtualFile> fileMapper = new ConcurrentFileMapper();
                  OCWorkspaceImpl.ModifiableModel model =
                      calculateConfigurations(
                          blazeProjectData, workspaceRoot, newResult, indicator, fileMapper);
                  ImmutableList<String> issues =
                      OCWorkspaceModifiableModelAdapter.commit(
                          model, SERIALIZATION_VERSION, toolEnvironment, fileMapper);
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

  private OCWorkspaceImpl.ModifiableModel calculateConfigurations(
      BlazeProjectData blazeProjectData,
      WorkspaceRoot workspaceRoot,
      BlazeConfigurationResolverResult newResult,
      ProgressIndicator indicator,
      NullableFunction<File, VirtualFile> fileMapper) {

    OCWorkspaceImpl.ModifiableModel workspaceModifiable =
        OCWorkspaceModifiableModelAdapter.getClearedModifiableModel(project);
    ImmutableList<BlazeResolveConfiguration> configurations = newResult.getAllConfigurations();
    ExecutionRootPathResolver executionRootPathResolver =
        new ExecutionRootPathResolver(
            Blaze.getBuildSystem(project),
            workspaceRoot,
            blazeProjectData.getBlazeInfo().getExecutionRoot(),
            blazeProjectData.getWorkspacePathResolver());

    int progress = 0;

    for (BlazeResolveConfiguration resolveConfiguration : configurations) {
      indicator.setText2(resolveConfiguration.getDisplayName(true));
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

        UnfilteredCompilerOptions coptsExtractor =
            UnfilteredCompilerOptions.builder()
                .registerSingleOrSplitOption("-I")
                .build(targetIdeInfo.getcIdeInfo().getLocalCopts());
        ImmutableList<String> plainLocalCopts =
            filterIncompatibleFlags(coptsExtractor.getUninterpretedOptions());
        ImmutableList<ExecutionRootPath> localIncludes =
            coptsExtractor.getExtractedOptionValues("-I").stream()
                .map(ExecutionRootPath::new)
                .collect(toImmutableList());

        // transitiveDefines are sourced from a target's (and transitive deps) "defines" attribute
        ImmutableList<String> transitiveDefineOptions =
            targetIdeInfo.getcIdeInfo().getTransitiveDefines().stream()
                .map(s -> "-D" + s)
                .collect(toImmutableList());

        // localIncludes are sourced from -I options in a target's "copts" attribute
        // transitiveIncludeDirectories are sourced from CcSkylarkApiProvider.include_directories
        // [see CcCompilationContextInfo::getIncludeDirs]
        ImmutableList<String> iOptionIncludeDirectories =
            Stream.concat(
                    localIncludes.stream(),
                    targetIdeInfo.getcIdeInfo().getTransitiveIncludeDirectories().stream())
                .flatMap(
                    executionRootPath ->
                        executionRootPathResolver.resolveToIncludeDirectories(executionRootPath)
                            .stream())
                .map(file -> "-I" + file.getAbsolutePath())
                .collect(toImmutableList());

        // transitiveQuoteIncludeDirectories are sourced from
        // CcSkylarkApiProvider.quote_include_directories
        // [see CcCompilationContextInfo::getQuoteIncludeDirs]
        ImmutableList<String> iquoteOptionIncludeDirectories =
            targetIdeInfo.getcIdeInfo().getTransitiveQuoteIncludeDirectories().stream()
                .flatMap(
                    executionRootPath ->
                        executionRootPathResolver.resolveToIncludeDirectories(executionRootPath)
                            .stream())
                .map(file -> "-iquote" + file.getAbsolutePath())
                .collect(toImmutableList());
        // transitiveSystemIncludeDirectories are sourced from
        // CcSkylarkApiProvider.system_include_directories
        // [see CcCompilationContextInfo::getSystemIncludeDirs]
        ImmutableList<String> isystemOptionIncludeDirectories =
            targetIdeInfo.getcIdeInfo().getTransitiveSystemIncludeDirectories().stream()
                .flatMap(
                    executionRootPath ->
                        executionRootPathResolver.resolveToIncludeDirectories(executionRootPath)
                            .stream())
                .map(file -> "-isystem" + file.getAbsolutePath())
                .collect(toImmutableList());

        for (VirtualFile vf : resolveConfiguration.getSources(targetKey)) {
          OCLanguageKind kind = resolveConfiguration.getDeclaredLanguageKind(vf);
          if (kind == null) {
            kind = OCLanguageKind.CPP;
          }

          CidrSwitchBuilder fileSpecificSwitchBuilder = new CidrSwitchBuilder();

          ImmutableList<String> baseSwitches = compilerSettings.getCompilerSwitches(kind, vf);
          fileSpecificSwitchBuilder.addAllRaw(baseSwitches);
          fileSpecificSwitchBuilder.addAllRaw(iOptionIncludeDirectories);
          fileSpecificSwitchBuilder.addAllRaw(iquoteOptionIncludeDirectories);
          fileSpecificSwitchBuilder.addAllRaw(isystemOptionIncludeDirectories);
          fileSpecificSwitchBuilder.addAllRaw(plainLocalCopts);
          fileSpecificSwitchBuilder.addAllRaw(transitiveDefineOptions);
          PerFileCompilerOpts perFileCompilerOpts =
              new PerFileCompilerOpts(kind, fileSpecificSwitchBuilder.build());
          configSourceFiles.put(vf, perFileCompilerOpts);
          if (!configLanguages.containsKey(kind)) {
            addConfigLanguageSwitches(
                configLanguages, compilerSettings,
                // If a file isn't found in configSourceFiles (newly created files), CLion uses the
                // configLanguages switches. We want some basic header search roots (genfiles),
                // which are part of every target's iquote directories. See:
                // https://github.com/bazelbuild/bazel/blob/2c493e8a2132d54f4b2fb8046f6bcef11e92cd22/src/main/java/com/google/devtools/build/lib/rules/cpp/CcCompilationHelper.java#L911
                iquoteOptionIncludeDirectories, kind);
          }
        }
      }

      for (OCLanguageKind language : supportedLanguages) {
        if (!configLanguages.containsKey(language)) {
          addConfigLanguageSwitches(
              configLanguages, compilerSettings, ImmutableList.of(), language);
        }
      }

      String id = resolveConfiguration.getDisplayName(false);
      String shortDisplayName = resolveConfiguration.getDisplayName(true);

      OCWorkspaceModifiableModelAdapter.addConfiguration(
          workspaceModifiable,
          id,
          id,
          shortDisplayName,
          workspaceRoot.directory(),
          configLanguages,
          configSourceFiles,
          toolEnvironment,
          fileMapper);
      progress++;
    }
    return workspaceModifiable;
  }

  private void addConfigLanguageSwitches(
      Map<OCLanguageKind, PerLanguageCompilerOpts> configLanguages,
      BlazeCompilerSettings compilerSettings,
      List<String> additionalSwitches,
      OCLanguageKind language) {
    OCCompilerKind compilerKind = compilerSettings.getCompiler(language);
    File executable = compilerSettings.getCompilerExecutable(language);
    CidrSwitchBuilder switchBuilder = new CidrSwitchBuilder();
    ImmutableList<String> switches = compilerSettings.getCompilerSwitches(language, null);
    switchBuilder.addAllRaw(switches);
    switchBuilder.addAllRaw(additionalSwitches);
    PerLanguageCompilerOpts perLanguageCompilerOpts =
        new PerLanguageCompilerOpts(compilerKind, executable, switchBuilder.build());
    configLanguages.put(language, perLanguageCompilerOpts);
  }

  private void incModificationTrackers() {
    TransactionGuard.submitTransaction(
        project,
        () -> {
          if (project.isDisposed()) {
            return;
          }
          OCWorkspaceModificationTrackers modTrackers =
              OCWorkspaceModificationTrackers.getInstance(project);
          modTrackers.getProjectFilesListTracker().incModificationCount();
          modTrackers.getSourceFilesListTracker().incModificationCount();
          modTrackers.getSelectedResolveConfigurationTracker().incModificationCount();
          modTrackers.getBuildSettingsChangesTracker().incModificationCount();
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

  private static class ConcurrentFileMapper implements NullableFunction<File, VirtualFile> {
    private static final LocalFileSystem localFileSystem = LocalFileSystem.getInstance();
    private static final ConcurrentHashMap<File, VirtualFile> cache = new ConcurrentHashMap<>();

    @Nullable
    @Override
    public VirtualFile fun(File file) {
      return cache.computeIfAbsent(file, localFileSystem::findFileByIoFile);
    }
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
}
