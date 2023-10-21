/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.typescript;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.common.experiments.BoolExperiment;
import com.google.idea.sdkcompat.javascript.TypeScriptConfigServiceAdapter;
import com.intellij.lang.typescript.tsconfig.TypeScriptConfig;
import com.intellij.lang.typescript.tsconfig.TypeScriptConfigService;
import com.intellij.lang.typescript.tsconfig.TypeScriptConfigServiceImpl;
import com.intellij.lang.typescript.tsconfig.TypeScriptConfigsChangedListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Switches between {@link BlazeTypeScriptConfigServiceImpl} if the project is an applicable blaze
 * project, or {@link TypeScriptConfigServiceImpl} if it isn't.
 */
class DelegatingTypeScriptConfigService extends TypeScriptConfigServiceAdapter {
  private final TypeScriptConfigService impl;

  @Override
  public TypeScriptConfigService getImpl() {
    return impl;
  }

  private static final BoolExperiment useBlazeTypeScriptConfig =
      new BoolExperiment("use.blaze.typescript.config", true);

  DelegatingTypeScriptConfigService(Project project) {
    if (useBlazeTypeScriptConfig.getValue() && Blaze.isBlazeProject(project)) {
      this.impl = new BlazeTypeScriptConfigServiceImpl(project);
    } else {
      this.impl = new TypeScriptConfigServiceImpl(project);
    }
  }

  void update(ImmutableMap<Label, File> tsconfigs) {
    if (impl instanceof BlazeTypeScriptConfigServiceImpl) {
      ((BlazeTypeScriptConfigServiceImpl) impl).update(tsconfigs);
    }
  }

  @Override
  public boolean isAccessible(VirtualFile scope, VirtualFile referenced) {
    return impl.isAccessible(scope, referenced);
  }

  @Override
  public Condition<VirtualFile> getAccessScope(VirtualFile scope) {
    return impl.getAccessScope(scope);
  }

  @Override
  public IntPredicate getFilterId(VirtualFile scope) {
    return impl.getFilterId(scope);
  }

  @Nullable
  @Override
  public TypeScriptConfig getPreferableConfig(VirtualFile scopeFile) {
    return impl.getPreferableConfig(scopeFile);
  }

  @Nullable
  @Override
  public TypeScriptConfig getPreferableOrParentConfig(@Nullable VirtualFile virtualFile) {
    return impl.getPreferableOrParentConfig(virtualFile);
  }

  @Nullable
  @Override
  public TypeScriptConfig getDirectIncludePreferableConfig(@Nullable VirtualFile virtualFile) {
    return impl.getDirectIncludePreferableConfig(virtualFile);
  }

  @Override
  public List<VirtualFile> getRootConfigFiles() {
    return impl.getRootConfigFiles();
  }

  @Nullable
  @Override
  public TypeScriptConfig parseConfigFile(VirtualFile file) {
    return impl.parseConfigFile(file);
  }

  public List<TypeScriptConfig> getTypeScriptConfigs() {
    if (impl instanceof BlazeTypeScriptConfigServiceImpl) {
      return ((BlazeTypeScriptConfigServiceImpl) impl).getTypeScriptConfigs();
    }
    return ImmutableList.of();
  }

  @Override
  public ModificationTracker getTracker() {
    return impl.getTracker();
  }

  @Override
  public boolean isImplicitIncludedNodeModulesFile(
      Project project, VirtualFile file, VirtualFile topDirectory) {
    return impl.isImplicitIncludedNodeModulesFile(project, file, topDirectory);
  }

  @Override
  public Set<VirtualFile> getIncludedFiles(VirtualFile file) {
    return impl.getIncludedFiles(file);
  }
}
