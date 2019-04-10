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
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.sync.BlazeSyncModificationTracker;
import com.google.idea.blaze.base.sync.SyncListener;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.SyncResult;
import com.intellij.lang.typescript.tsconfig.TypeScriptConfig;
import com.intellij.lang.typescript.tsconfig.TypeScriptConfigService;
import com.intellij.lang.typescript.tsconfig.TypeScriptConfigUtil;
import com.intellij.lang.typescript.tsconfig.TypeScriptConfigsChangedListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;

class BlazeTypeScriptConfigServiceImpl implements TypeScriptConfigService {
  private final Project project;
  private final List<TypeScriptConfigsChangedListener> listeners;

  private ImmutableList<TypeScriptConfig> configs;

  BlazeTypeScriptConfigServiceImpl(Project project) {
    this.project = project;
    this.listeners = new ArrayList<>();
    this.configs = parseConfigs(project);
  }

  void clear() {
    configs = ImmutableList.of();
  }

  void update() {
    configs = parseConfigs(project);
    for (TypeScriptConfigsChangedListener listener : listeners) {
      listener.afterUpdate(configs);
    }
  }

  private ImmutableList<TypeScriptConfig> parseConfigs(Project project) {
    ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
    if (projectViewSet == null) {
      return ImmutableList.of();
    }
    return getTsConfigTargets(projectViewSet).stream()
        .map(label -> BlazeTypeScriptConfig.getInstance(project, label))
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
    return TypeScriptConfigUtil.getNearestParentConfig(scopeFile, configs);
  }

  @Nullable
  @Override
  public TypeScriptConfig parseConfigFile(VirtualFile file) {
    return null;
  }

  @Override
  public List<TypeScriptConfig> getConfigFiles() {
    return configs;
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

  static class Updater implements SyncListener {
    @Override
    public void onSyncComplete(
        Project project,
        BlazeContext context,
        BlazeImportSettings importSettings,
        ProjectViewSet projectViewSet,
        BlazeProjectData blazeProjectData,
        SyncMode syncMode,
        SyncResult syncResult) {
      TypeScriptConfigService service = TypeScriptConfigService.Provider.get(project);
      if (!(service instanceof DelegatingTypeScriptConfigService)) {
        return;
      }
      if (!blazeProjectData
          .getWorkspaceLanguageSettings()
          .isLanguageActive(LanguageClass.TYPESCRIPT)) {
        ((DelegatingTypeScriptConfigService) service).clear();
      } else {
        ((DelegatingTypeScriptConfigService) service).update();
      }
    }
  }
}
