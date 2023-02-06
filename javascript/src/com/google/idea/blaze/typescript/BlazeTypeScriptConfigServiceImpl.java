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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.base.Functions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.sync.BlazeSyncModificationTracker;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.lang.typescript.compiler.TypeScriptCompilerService;
import com.intellij.lang.typescript.tsconfig.TypeScriptConfig;
import com.intellij.lang.typescript.tsconfig.TypeScriptConfigService;
import com.intellij.lang.typescript.tsconfig.TypeScriptConfigServiceImpl;
import com.intellij.lang.typescript.tsconfig.TypeScriptConfigsChangedListener;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.diagnostic.Logger;
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
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;

class BlazeTypeScriptConfigServiceImpl implements TypeScriptConfigService {
  private static final Logger logger = Logger.getInstance(BlazeTypeScriptConfigServiceImpl.class);
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
   * <p>This uses multiple file operations to check timestamps and reload the files list, so should
   * not be called on the EDT or with a read lock.
   */
  void update(ImmutableMap<Label, File> tsconfigs) {
    Application application = ApplicationManager.getApplication();
    if (application.isDispatchThread() || application.isReadAccessAllowed()) {
      logger.error("Updating tsconfig files on EDT or with a read lock.");
      return;
    }
    configs =
        tsconfigs.entrySet().parallelStream()
            .map(
                entry ->
                    BlazeTypeScriptConfig.getInstance(project, entry.getKey(), entry.getValue()))
            .filter(Objects::nonNull)
            .collect(
                ImmutableMap.toImmutableMap(TypeScriptConfig::getConfigFile, Functions.identity()));
    for (TypeScriptConfigsChangedListener listener : listeners) {
      listener.afterUpdate(configs.keySet());
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
  public TypeScriptConfig getPreferableConfig(@Nullable VirtualFile scopeFile) {

    if (configs.isEmpty() || scopeFile == null || !scopeFile.isValid()) {
      return null;
    }
    for (VirtualFile configFile : getNearestParentTsConfigs(scopeFile, configs)) {
      TypeScriptConfig config = configs.get(configFile);
      if (config != null && this.configGraphIncludesFile(scopeFile, config)) {
        return config;
      }
    }
    return null;
  }

  @Nullable
  @Override
  public TypeScriptConfig getPreferableOrParentConfig(@Nullable VirtualFile scopeFile) {
    if (configs.isEmpty() || scopeFile == null) {
      return null;
    }
    TypeScriptConfig configForFile = getPreferableConfig(scopeFile);
    if (configForFile != null) {
      return configForFile;
    }
    return getNearestParentTsConfigs(scopeFile, configs).stream()
        .map(configs::get)
        .findFirst()
        .orElse(null);
  }

  @Nullable
  @Override
  public TypeScriptConfig getDirectIncludePreferableConfig(@Nullable VirtualFile scopeFile) {
    if (configs.isEmpty() || scopeFile == null) {
      return null;
    }
    for (VirtualFile configFile : getNearestParentTsConfigs(scopeFile, configs)) {
      TypeScriptConfig config = configs.get(configFile);
      if (config != null && config.getInclude().accept(scopeFile)) {
        return config;
      }
    }
    return null;
  }

  @Override
  public List<VirtualFile> getRootConfigFiles() {
    return configs.keySet().asList();
  }

  @Nullable
  @Override
  public TypeScriptConfig parseConfigFile(VirtualFile file) {
    return configs.get(file);
  }

  public List<TypeScriptConfig> getTypeScriptConfigs() {
    return configs.values().asList();
  }

  // #api223 remove this method
  public List<VirtualFile> getConfigFiles() {
    return this.configs.keySet().asList();
  }

  // #api223 remove this method
  public void addChangeListener(TypeScriptConfigsChangedListener listener) {
    listeners.add(listener);
  }

  // #api223 remove this method
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

  private static ImmutableList<? extends VirtualFile> getNearestParentTsConfigs(
      @Nullable VirtualFile scopeFile, ImmutableMap<VirtualFile, TypeScriptConfig> configs) {
    return TypeScriptConfigServiceImpl.getNearestParentTsConfigs(scopeFile, false).stream()
        .filter(configs::containsKey)
        .collect(toImmutableList());
  }
}
