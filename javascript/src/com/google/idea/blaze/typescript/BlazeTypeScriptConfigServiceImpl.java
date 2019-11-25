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

import com.google.common.base.Functions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.sync.BlazeSyncModificationTracker;
import com.google.idea.common.experiments.BoolExperiment;
import com.google.idea.sdkcompat.typescript.TypeScriptConfigServiceCompat;
import com.intellij.lang.typescript.compiler.TypeScriptCompilerService;
import com.intellij.lang.typescript.tsconfig.TypeScriptConfig;
import com.intellij.lang.typescript.tsconfig.TypeScriptConfigsChangedListener;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;

class BlazeTypeScriptConfigServiceImpl implements TypeScriptConfigServiceCompat {
  private static final BoolExperiment restartTypeScriptService =
      new BoolExperiment("restart.typescript.service", true);

  private final Project project;
  private final List<TypeScriptConfigsChangedListener> listeners;

  private volatile ImmutableMap<VirtualFile, TypeScriptConfig> configs;
  private final AtomicInteger configsHash = new AtomicInteger(Objects.hash());

  BlazeTypeScriptConfigServiceImpl(Project project) {
    this.project = project;
    this.listeners = new ArrayList<>();
    this.configs = ImmutableMap.of();
  }

  /**
   * Checks for modifications to the tsconfig files for the project.
   *
   * <p>This calls {@link File#lastModified()}, so should not be called on the EDT or with a read
   * lock.
   */
  void update(BlazeProjectData projectData) {
    configs =
        parseConfigs(project, projectData).stream()
            .collect(
                ImmutableMap.toImmutableMap(TypeScriptConfig::getConfigFile, Functions.identity()));
    for (TypeScriptConfigsChangedListener listener : listeners) {
      TypeScriptConfigServiceCompat.fireListener(listener, configs);
    }
    restartServiceIfConfigsChanged();
  }

  private void restartServiceIfConfigsChanged() {
    if (!restartTypeScriptService.getValue()) {
      return;
    }
    int pathHash = Arrays.hashCode(configs.keySet().stream().map(VirtualFile::getPath).toArray());
    long contentTimestamp =
        configs.values().stream()
            .map(TypeScriptConfig::getDependencies)
            .flatMap(Collection::stream)
            .map(VfsUtil::virtualToIoFile)
            .map(File::lastModified)
            .max(Comparator.naturalOrder())
            .orElse(0L);
    int newConfigsHash = Objects.hash(pathHash, contentTimestamp);
    if (configsHash.getAndSet(newConfigsHash) != newConfigsHash) {
      TransactionGuard.getInstance()
          .submitTransactionLater(
              project, () -> TypeScriptCompilerService.restartServices(project, false));
    }
  }

  private static ImmutableList<TypeScriptConfig> parseConfigs(
      Project project, BlazeProjectData projectData) {
    ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
    if (projectViewSet == null) {
      return ImmutableList.of();
    }
    return getTsConfigTargets(projectViewSet).stream()
        .map(label -> BlazeTypeScriptConfig.getInstance(project, projectData, label))
        .filter(Objects::nonNull)
        .collect(ImmutableList.toImmutableList());
  }

  private static Set<Label> getTsConfigTargets(ProjectViewSet projectViewSet) {
    Set<Label> labels = new LinkedHashSet<>(projectViewSet.listItems(TsConfigRulesSection.KEY));
    projectViewSet.getScalarValue(TsConfigRuleSection.KEY).ifPresent(labels::add);
    return labels;
  }

  @Override
  public boolean isAccessible(VirtualFile scope, VirtualFile referenced) {
    return true;
  }

  @Override
  public Condition<VirtualFile> getAccessScope(VirtualFile scope) {
    return f -> true;
  }

  @Override
  public IntPredicate getFilterId(VirtualFile scope) {
    return i -> true;
  }

  @Nullable
  @Override
  public TypeScriptConfig getPreferableConfig(VirtualFile scopeFile) {
    return TypeScriptConfigServiceCompat.getPreferableConfig(scopeFile, configs);
  }

  @Nullable
  @Override
  public TypeScriptConfig parseConfigFile(VirtualFile file) {
    return null;
  }

  @Override
  public List<TypeScriptConfig> getConfigs() {
    return configs.values().asList();
  }

  @Override
  public List<VirtualFile> doGetConfigFiles() {
    return configs.keySet().asList();
  }

  @Override
  public void addChangeListener(TypeScriptConfigsChangedListener listener) {
    listeners.add(listener);
  }

  @Override
  public boolean hasConfigs() {
    return !configs.isEmpty();
  }

  @Override
  public ModificationTracker getConfigTracker(@Nullable VirtualFile file) {
    return BlazeSyncModificationTracker.getInstance(project);
  }

  @Override
  public ModificationTracker getTracker() {
    return BlazeSyncModificationTracker.getInstance(project);
  }

  @Override
  public boolean isImplicitIncludedNodeModulesFile(
      Project project, VirtualFile file, VirtualFile topDirectory) {
    return false;
  }

  @Override
  public Set<VirtualFile> getIncludedFiles(VirtualFile file) {
    return ImmutableSet.of();
  }
}
