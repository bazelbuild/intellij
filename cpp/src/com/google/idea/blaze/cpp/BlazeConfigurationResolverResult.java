/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.ideinfo.CToolchainIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.targetmaps.SourceToTargetMap;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Resolve configuration maps, etc. obtained from running the {@link BlazeConfigurationResolver}.
 */
@Immutable
final class BlazeConfigurationResolverResult {

  private final Project project;

  // Multiple target keys may map to the same resolve configuration.
  private final ImmutableMap<TargetKey, BlazeResolveConfiguration> configurationMap;
  final ImmutableMap<BlazeResolveConfigurationData, BlazeResolveConfiguration>
      uniqueResolveConfigurations;
  final ImmutableMap<CToolchainIdeInfo, BlazeCompilerSettings> compilerSettings;

  BlazeConfigurationResolverResult(
      Project project,
      ImmutableMap<TargetKey, BlazeResolveConfiguration> configurationMap,
      ImmutableMap<BlazeResolveConfigurationData, BlazeResolveConfiguration>
          uniqueResolveConfigurations,
      ImmutableMap<CToolchainIdeInfo, BlazeCompilerSettings> compilerSettings) {
    this.project = project;
    this.configurationMap = configurationMap;
    this.uniqueResolveConfigurations = uniqueResolveConfigurations;
    this.compilerSettings = compilerSettings;
  }

  static Builder builder(Project project) {
    return new Builder(project);
  }

  static BlazeConfigurationResolverResult empty(Project project) {
    return builder(project).build();
  }

  @Nullable
  OCResolveConfiguration getConfigurationForFile(VirtualFile sourceFile) {
    SourceToTargetMap sourceToTargetMap = SourceToTargetMap.getInstance(project);
    ImmutableCollection<TargetKey> targetsForSourceFile =
        sourceToTargetMap.getRulesForSourceFile(VfsUtilCore.virtualToIoFile(sourceFile));
    if (targetsForSourceFile.isEmpty()) {
      return null;
    }

    // If a source file is in two different targets, we can't possibly show how it will be
    // interpreted in both contexts at the same time in the IDE, so just pick the "first" target.
    TargetKey targetKey = targetsForSourceFile.stream().min(TargetKey::compareTo).orElse(null);
    Preconditions.checkNotNull(targetKey);

    return configurationMap.get(targetKey);
  }

  ImmutableList<BlazeResolveConfiguration> getAllConfigurations() {
    return uniqueResolveConfigurations.values().asList();
  }

  static class Builder {
    final Project project;
    ImmutableMap<TargetKey, BlazeResolveConfiguration> configurationMap = ImmutableMap.of();
    ImmutableMap<BlazeResolveConfigurationData, BlazeResolveConfiguration> uniqueConfigurations =
        ImmutableMap.of();
    ImmutableMap<CToolchainIdeInfo, BlazeCompilerSettings> compilerSettings = ImmutableMap.of();

    public Builder(Project project) {
      this.project = project;
    }

    BlazeConfigurationResolverResult build() {
      return new BlazeConfigurationResolverResult(
          project, configurationMap, uniqueConfigurations, compilerSettings);
    }

    void setConfigurationMap(ImmutableMap<TargetKey, BlazeResolveConfiguration> configurationMap) {
      this.configurationMap = configurationMap;
    }

    void setUniqueConfigurations(
        ImmutableMap<BlazeResolveConfigurationData, BlazeResolveConfiguration>
            uniqueConfigurations) {
      this.uniqueConfigurations = uniqueConfigurations;
    }

    void setCompilerSettings(
        ImmutableMap<CToolchainIdeInfo, BlazeCompilerSettings> compilerSettings) {
      this.compilerSettings = compilerSettings;
    }
  }
}
