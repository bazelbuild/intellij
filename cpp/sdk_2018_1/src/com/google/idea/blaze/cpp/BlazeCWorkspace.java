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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.workspace.ExecutionRootPathResolver;
import com.google.idea.sdkcompat.cidr.OCCompilerSettingsAdapter;
import com.google.idea.sdkcompat.cidr.OCWorkspaceModifiableModelAdapter;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.NullableFunction;
import com.jetbrains.cidr.lang.OCLanguageKind;
import com.jetbrains.cidr.lang.toolchains.CidrCompilerSwitches;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/** Main entry point for C/CPP configuration data. */
public final class BlazeCWorkspace implements ProjectComponent {
  // Required by OCWorkspaceImpl.ModifiableModel::commit
  // This component is never actually serialized, and this should not ever need to change
  private static final int SERIALIZATION_VERSION = 1;

  private final BlazeConfigurationResolver configurationResolver;
  private BlazeConfigurationResolverResult resolverResult;
  private ImmutableList<OCLanguageKind> supportedLanguages =
      ImmutableList.of(OCLanguageKind.C, OCLanguageKind.CPP);

  private final Project project;
  private CidrToolEnvironment toolEnvironment = new CidrToolEnvironment();

  private BlazeCWorkspace(Project project) {
    this.configurationResolver = new BlazeConfigurationResolver(project);
    this.resolverResult = BlazeConfigurationResolverResult.empty(project);
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
      BlazeProjectData blazeProjectData) {

    ProgressManager.getInstance()
        .run(
            new Task.Backgroundable(project, "Configuration Sync", false) {
              @Override
              public void run(ProgressIndicator indicator) {

                indicator.setIndeterminate(false);
                indicator.setText("Resolving Configurations...");
                indicator.setFraction(0.0);
                BlazeConfigurationResolverResult newResult =
                    resolveConfigurations(
                        context, workspaceRoot, projectViewSet, blazeProjectData, indicator);
                indicator.setText("Updating Configurations...");
                indicator.setFraction(0.0);
                CommitableConfiguration config =
                    calculateConfigurations(blazeProjectData, workspaceRoot, newResult, indicator);
                commitConfigurations(config);
                incModificationTrackers();
              }
            });
  }

  @VisibleForTesting
  BlazeConfigurationResolverResult resolveConfigurations(
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      BlazeProjectData blazeProjectData,
      @Nullable ProgressIndicator indicator) {
    BlazeConfigurationResolverResult oldResult = resolverResult;
    return configurationResolver.update(
        context, workspaceRoot, projectViewSet, blazeProjectData, oldResult);
  }

  @VisibleForTesting
  CommitableConfiguration calculateConfigurations(
      BlazeProjectData blazeProjectData,
      WorkspaceRoot workspaceRoot,
      BlazeConfigurationResolverResult newResult,
      ProgressIndicator indicator) {
    NullableFunction<File, VirtualFile> fileMapper = OCWorkspaceImpl.createFileMapper();

    OCWorkspaceImpl.ModifiableModel workspaceModifiable =
        OCWorkspaceImpl.getInstanceImpl(project).getModifiableModel();
    ImmutableList<BlazeResolveConfiguration> configurations = newResult.getAllConfigurations();
    ExecutionRootPathResolver executionRootPathResolver =
        new ExecutionRootPathResolver(
            Blaze.getBuildSystem(project),
            workspaceRoot,
            blazeProjectData.blazeInfo.getExecutionRoot(),
            blazeProjectData.workspacePathResolver);

    int progress = 0;
    for (BlazeResolveConfiguration resolveConfiguration : configurations) {
      indicator.setText2(resolveConfiguration.getDisplayName(true));
      indicator.setFraction(((double) progress) / configurations.size());
      OCCompilerSettingsAdapter compilerSettingsAdapter =
          resolveConfiguration.getCompilerSettingsAdapter();
      Map<OCLanguageKind, Trinity<OCCompilerKind, File, CidrCompilerSwitches>> configLanguages =
          new HashMap<>();
      Map<VirtualFile, Pair<OCLanguageKind, CidrCompilerSwitches>> configSourceFiles =
          new HashMap<>();
      for (TargetKey targetKey : resolveConfiguration.getTargets()) {
        TargetIdeInfo targetIdeInfo = blazeProjectData.targetMap.get(targetKey);
        if (targetIdeInfo == null || targetIdeInfo.cIdeInfo == null) {
          continue;
        }

        // defines and include directories are the same for all sources in a given target, so lets
        // collect them once and reuse for each source file's options

        // localDefines are sourced from -D options in a target's "copts" attribute
        List<String> localDefineOptions =
            targetIdeInfo
                .cIdeInfo
                .localDefines
                .stream()
                .map(s -> "-D" + s)
                .collect(Collectors.toList());
        // transitiveDefines are sourced from a target's (and transitive deps) "defines" attribute
        List<String> transitiveDefineOptions =
            targetIdeInfo
                .cIdeInfo
                .transitiveDefines
                .stream()
                .map(s -> "-D" + s)
                .collect(Collectors.toList());

        // localIncludeDirectories are sourced from -I options in a target's "copts" attribute
        // transitiveIncludeDirectories are sourced from CcSkylarkApiProvider.include_directories
        // [see CcCompilationContextInfo::getIncludeDirs]
        List<String> iOptionIncludeDirectories =
            Stream.concat(
                    targetIdeInfo.cIdeInfo.localIncludeDirectories.stream(),
                    targetIdeInfo.cIdeInfo.transitiveIncludeDirectories.stream())
                .map(
                    executionRootPath ->
                        "-I"
                            + executionRootPathResolver
                                .resolveExecutionRootPath(executionRootPath)
                                .getAbsolutePath())
                .collect(Collectors.toList());
        // transitiveQuoteIncludeDirectories are sourced from
        // CcSkylarkApiProvider.quote_include_directories
        // [see CcCompilationContextInfo::getQuoteIncludeDirs]
        List<String> iquoteOptionIncludeDirectories =
            targetIdeInfo
                .cIdeInfo
                .transitiveQuoteIncludeDirectories
                .stream()
                .map(
                    executionRootPath ->
                        "-iquote"
                            + executionRootPathResolver
                                .resolveExecutionRootPath(executionRootPath)
                                .getAbsolutePath())
                .collect(Collectors.toList());
        // transitiveQuoteIncludeDirectories are sourced from
        // CcSkylarkApiProvider.system_include_directories
        // [see CcCompilationContextInfo::getSystemIncludeDirs]
        List<String> isystemOptionIncludeDirectories =
            targetIdeInfo
                .cIdeInfo
                .transitiveSystemIncludeDirectories
                .stream()
                .map(
                    executionRootPath ->
                        "-isystem"
                            + executionRootPathResolver
                                .resolveExecutionRootPath(executionRootPath)
                                .getAbsolutePath())
                .collect(Collectors.toList());

        for (VirtualFile vf : resolveConfiguration.getSources(blazeProjectData, targetKey)) {
          OCLanguageKind kind = resolveConfiguration.getDeclaredLanguageKind(vf);
          if (kind == null) {
            kind = OCLanguageKind.CPP;
          }

          CidrSwitchBuilder fileSpecificSwitchBuilder = new CidrSwitchBuilder();

          CidrCompilerSwitches baseSwitches = compilerSettingsAdapter.getCompilerSwitches(kind, vf);
          fileSpecificSwitchBuilder.addAll(baseSwitches);
          fileSpecificSwitchBuilder.addAllRaw(iOptionIncludeDirectories);
          fileSpecificSwitchBuilder.addAllRaw(iquoteOptionIncludeDirectories);
          fileSpecificSwitchBuilder.addAllRaw(isystemOptionIncludeDirectories);
          fileSpecificSwitchBuilder.addAllRaw(localDefineOptions);
          fileSpecificSwitchBuilder.addAllRaw(transitiveDefineOptions);
          configSourceFiles.put(vf, Pair.create(kind, fileSpecificSwitchBuilder.build()));
          if (!configLanguages.containsKey(kind)) {
            addConfigLanguageSwitches(
                configLanguages, compilerSettingsAdapter,
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
              configLanguages, compilerSettingsAdapter, ImmutableList.of(), language);
        }
      }

      String id = resolveConfiguration.getDisplayName(false);
      String shortDisplayName = resolveConfiguration.getDisplayName(true);

      workspaceModifiable.addConfiguration(
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

    return new CommitableConfiguration(newResult, workspaceModifiable);
  }

  @VisibleForTesting
  void commitConfigurations(CommitableConfiguration config) {
    resolverResult = config.result;
    OCWorkspaceModifiableModelAdapter.commit(config.model, SERIALIZATION_VERSION);
  }

  private void addConfigLanguageSwitches(
      Map<OCLanguageKind, Trinity<OCCompilerKind, File, CidrCompilerSwitches>> configLanguages,
      OCCompilerSettingsAdapter compilerSettingsAdapter,
      List<String> additionalSwitches,
      OCLanguageKind language) {
    OCCompilerKind compilerKind = compilerSettingsAdapter.getCompiler(language);
    File executable = compilerSettingsAdapter.getCompilerExecutable(language);
    CidrSwitchBuilder switchBuilder = new CidrSwitchBuilder();
    CidrCompilerSwitches switches = compilerSettingsAdapter.getCompilerSwitches(language, null);
    switchBuilder.addAll(switches);
    switchBuilder.addAllRaw(additionalSwitches);
    configLanguages.put(language, Trinity.create(compilerKind, executable, switchBuilder.build()));
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

  /** Contains the configuration to be committed all-at-once */
  public static class CommitableConfiguration {
    private final BlazeConfigurationResolverResult result;
    private final OCWorkspaceImpl.ModifiableModel model;

    CommitableConfiguration(
        BlazeConfigurationResolverResult result, OCWorkspaceImpl.ModifiableModel model) {
      this.result = result;
      this.model = model;
    }
  }
}
