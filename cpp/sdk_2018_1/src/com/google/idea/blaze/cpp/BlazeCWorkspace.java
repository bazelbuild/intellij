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

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.sdkcompat.cidr.OCCompilerSettingsAdapter;
import com.google.idea.sdkcompat.cidr.OCWorkspaceAdapter;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.NullableFunction;
import com.jetbrains.cidr.lang.OCLanguageKind;
import com.jetbrains.cidr.lang.toolchains.CidrCompilerSwitches;
import com.jetbrains.cidr.lang.toolchains.CidrSwitchBuilder;
import com.jetbrains.cidr.lang.toolchains.CidrToolEnvironment;
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration;
import com.jetbrains.cidr.lang.workspace.OCWorkspaceImpl;
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerKind;
import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/** Main entry point for C/CPP configuration data. */
public final class BlazeCWorkspace extends OCWorkspaceAdapter implements ProjectComponent {
  private final BlazeConfigurationResolver configurationResolver;
  private BlazeConfigurationResolverResult resolverResult;

  private final Project project;

  private BlazeCWorkspace(Project project) {
    this.configurationResolver = new BlazeConfigurationResolver(project);
    this.resolverResult = BlazeConfigurationResolverResult.empty(project);
    this.project = project;
  }

  public static BlazeCWorkspace getInstance(Project project) {
    return project.getComponent(BlazeCWorkspace.class);
  }

  public void update(
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      BlazeProjectData blazeProjectData) {
    BlazeConfigurationResolverResult oldResult = resolverResult;
    resolverResult =
        configurationResolver.update(
            context, workspaceRoot, projectViewSet, blazeProjectData, oldResult);
    CidrToolEnvironment environment = new CidrToolEnvironment();

    NullableFunction<File, VirtualFile> fileMapper = OCWorkspaceImpl.createFileMapper();
    ImmutableList<OCLanguageKind> supportedLanguages =
        ImmutableList.of(OCLanguageKind.C, OCLanguageKind.CPP);

    OCWorkspaceImpl.ModifiableModel workspaceModifiable =
        OCWorkspaceImpl.getInstanceImpl(project).getModifiableModel();

    for (BlazeResolveConfiguration resolveConfiguration : resolverResult.getAllConfigurations()) {
      Map<OCLanguageKind, Trinity<OCCompilerKind, File, CidrCompilerSwitches>> configLanguages =
          new HashMap<>();
      OCCompilerSettingsAdapter compilerSettingsAdapter =
          resolveConfiguration.getCompilerSettingsAdapter();
      for (OCLanguageKind language : supportedLanguages) {
        OCCompilerKind kind = compilerSettingsAdapter.getCompiler(language);
        File executable = compilerSettingsAdapter.getCompilerExecutable(language);
        CidrCompilerSwitches switches = compilerSettingsAdapter.getCompilerSwitches(language, null);
        configLanguages.put(language, Trinity.create(kind, executable, switches));
      }

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
                        "-I" + executionRootPath.getAbsoluteOrRelativeFile().getPath())
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
                        "-iquote" + executionRootPath.getAbsoluteOrRelativeFile().getPath())
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
                        "-isystem" + executionRootPath.getAbsoluteOrRelativeFile().getPath())
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
          environment,
          fileMapper);
    }
    workspaceModifiable.commit();
  }

  @Override
  public Collection<VirtualFile> getLibraryFilesToBuildSymbols() {
    // This method should return all the header files themselves, not the head file directories.
    // (And not header files in the project; just the ones in the SDK and in any dependencies)
    return ImmutableList.of();
  }

  @Override
  public List<OCResolveConfiguration> getConfigurations() {
    return OCWorkspaceImpl.getInstanceImpl(project).getConfigurations();
  }

  @Override
  public List<OCResolveConfiguration> getConfigurationsForFile(@Nullable VirtualFile sourceFile) {
    return OCWorkspaceImpl.getInstanceImpl(project).getConfigurationsForFile(sourceFile);
  }
}
