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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.scopes.TimingScope;
import com.google.idea.blaze.base.scope.scopes.TimingScope.EventType;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.projectview.ProjectViewTargetImportFilter;
import com.google.idea.blaze.base.sync.workspace.ExecutionRootPathResolver;
import com.google.idea.blaze.base.sync.workspace.WorkspaceHelper;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.blaze.common.PrintOutput;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.registry.Registry;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.function.Predicate;

final class BlazeConfigurationResolver {
  static final String SYNC_EXTERNAL_TARGETS_FROM_DIRECTORIES_KEY = "bazel.cpp.sync.external.targets.from.directories";

  private static final Logger logger = Logger.getInstance(BlazeConfigurationResolver.class);

  private final Project project;

  BlazeConfigurationResolver(Project project) {
    this.project = project;
  }

  public BlazeConfigurationResolverResult update(
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      BlazeProjectData blazeProjectData,
      BlazeConfigurationResolverResult oldResult) {
    final var executionRootPathResolver = ExecutionRootPathResolver.fromProjectData(project, blazeProjectData);

    final var toolchainLookupMap = BlazeConfigurationToolchainResolver.buildToolchainLookupMap(
        context,
        blazeProjectData.targetMap()
    );

    final var xcodeSettings = BlazeConfigurationToolchainResolver.resolveXcodeCompilerSettings(
        context,
        project,
        blazeProjectData
    );

    final var compilerSettings = BlazeConfigurationToolchainResolver.buildCompilerSettingsMap(
        context,
        project,
        toolchainLookupMap,
        executionRootPathResolver,
        oldResult.getCompilerSettings(),
        xcodeSettings
    );

    final var projectViewFilter = new ProjectViewTargetImportFilter(
        Blaze.getBuildSystemName(project),
        workspaceRoot,
        projectViewSet
    );

    final var compilerLookupMap = BlazeConfigurationToolchainResolver.buildCompilerLookupMap(toolchainLookupMap, compilerSettings);
    final var targetFilter = getTargetFilter(projectViewFilter, project, blazeProjectData.workspacePathResolver());

    final var builder = BlazeConfigurationResolverResult.builder();
    buildBlazeConfigurationData(
        context,
        blazeProjectData,
        compilerLookupMap,
        targetFilter,
        builder
    );

    final var validHeaderRoots = HeaderRootTrimmer.getInstance(project).getValidHeaderRoots(
        context,
        blazeProjectData,
        compilerLookupMap,
        targetFilter,
        executionRootPathResolver
    );

    builder.setCompilerSettings(compilerSettings);
    builder.setValidHeaderRoots(validHeaderRoots);
    builder.setXcodeSettings(xcodeSettings);

    return builder.build();
  }

  private static Predicate<TargetIdeInfo> getTargetFilter(
      ProjectViewTargetImportFilter projectViewFilter,
      Project project,
      WorkspacePathResolver workspacePathResolver) {
    return target -> {
      WorkspacePath pathForExternalTarget = getWorkspacePathForExternalTarget(target, project, workspacePathResolver);

      boolean allowExternalTargetSync =
          Registry.is(SYNC_EXTERNAL_TARGETS_FROM_DIRECTORIES_KEY) && pathForExternalTarget != null;

      return target.getcIdeInfo() != null
          && (projectViewFilter.isSourceTarget(target) ||
            allowExternalTargetSync && projectViewFilter.containsWorkspacePath(pathForExternalTarget))
          && containsCompiledSources(target);
    };
  }

  private static WorkspacePath getWorkspacePathForExternalTarget(
      TargetIdeInfo target,
      Project project,
      WorkspacePathResolver workspacePathResolver) {
    if (target.toTargetInfo().getLabel().isExternal()) {
      WorkspaceRoot externalWorkspace = WorkspaceHelper.getExternalWorkspace(project,
          target.getKey().label().externalWorkspaceName());

      if (externalWorkspace != null) {
        try {
          Path externalWorkspaceRealPath = externalWorkspace.directory().toPath().toRealPath();
          return workspacePathResolver.getWorkspacePath(externalWorkspaceRealPath.toFile());
        } catch (IOException ioException) {
          logger.warn("Failed to resolve real external workspace location", ioException);
        }
      }
    }
    return null;
  }

  private static boolean containsCompiledSources(TargetIdeInfo target) {
    Predicate<ArtifactLocation> isCompiled = location -> {
      String locationExtension = FileUtilRt.getExtension(location.relativePath());
      return CFileExtensions.SOURCE_EXTENSIONS.contains(locationExtension);
    };

    final var cIdeInfo = target.getcIdeInfo();
    if (cIdeInfo == null) {
      return false;
    }

    return cIdeInfo.ruleContext().sources()
        .stream()
        .filter(ArtifactLocation::isSource)
        .anyMatch(isCompiled);
  }

  private void buildBlazeConfigurationData(
      BlazeContext parentContext,
      BlazeProjectData blazeProjectData,
      ImmutableMap<TargetKey, BlazeCompilerSettings> compilerSettings,
      Predicate<TargetIdeInfo> targetFilter,
      BlazeConfigurationResolverResult.Builder builder) {
    Scope.push(parentContext, context -> {
      context.push(new TimingScope("Build C configuration map", EventType.Other));

      final var targetToData = new HashMap<TargetKey, BlazeResolveConfigurationData>();
      blazeProjectData.targetMap().targets().stream().filter(targetFilter).forEach(target -> {
        final var data = createResolveConfiguration(target, compilerSettings);
        if (data != null) {
          targetToData.put(target.getKey(), data);
        }
      });

      findEquivalenceClasses(context, blazeProjectData, targetToData, builder);
    });
  }

  private static void findEquivalenceClasses(
      BlazeContext context,
      BlazeProjectData blazeProjectData,
      Map<TargetKey, BlazeResolveConfigurationData> targetToData,
      BlazeConfigurationResolverResult.Builder builder
  ) {
    final var dataEquivalenceClasses = ArrayListMultimap.<BlazeResolveConfigurationData, TargetKey>create();
    for (final var entry : targetToData.entrySet()) {
      final var target = entry.getKey();
      final var data = entry.getValue();
      dataEquivalenceClasses.put(data, target);
    }

    final var dataToConfiguration = ImmutableMap.<BlazeResolveConfigurationData, BlazeResolveConfiguration>builder();
    for (final var entry : dataEquivalenceClasses.asMap().entrySet()) {
      final var data = entry.getKey();
      final var targets = entry.getValue();
      dataToConfiguration.put(
          data,
          BlazeResolveConfiguration.create(blazeProjectData, data, targets)
      );
    }

    context.output(PrintOutput.log(String.format(
        "%s unique C configurations, %s C targets",
        dataEquivalenceClasses.keySet().size(),
        dataEquivalenceClasses.size()
    )));

    builder.setUniqueConfigurations(dataToConfiguration.build());
  }

  @Nullable
  private BlazeResolveConfigurationData createResolveConfiguration(
      TargetIdeInfo target,
      ImmutableMap<TargetKey, BlazeCompilerSettings> compilerSettingsMap) {
    final var cIdeInfo = target.getcIdeInfo();
    if (cIdeInfo == null) {
      return null;
    }
    final var compilerSettings = compilerSettingsMap.get(target.getKey());
    if (compilerSettings == null) {
      return null;
    }
    return BlazeResolveConfigurationData.create(cIdeInfo, compilerSettings);
  }
}
