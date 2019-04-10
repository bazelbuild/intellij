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
package com.google.idea.sdkcompat.typescript;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.intellij.lang.typescript.library.TypeScriptLibraryProvider;
import com.intellij.lang.typescript.tsconfig.TypeScriptConfig;
import com.intellij.lang.typescript.tsconfig.TypeScriptConfigLibraryUpdater;
import com.intellij.lang.typescript.tsconfig.TypeScriptConfigService;
import com.intellij.lang.typescript.tsconfig.TypeScriptConfigServiceImpl;
import com.intellij.lang.typescript.tsconfig.TypeScriptConfigUtil;
import com.intellij.lang.typescript.tsconfig.TypeScriptConfigsChangedListener;
import com.intellij.lang.typescript.tsconfig.graph.TypeScriptConfigGraphCache;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import java.util.List;
import javax.annotation.Nullable;

/** Compatibility layer for {@link TypeScriptConfigService}. #api183 */
public interface TypeScriptConfigServiceCompat extends TypeScriptConfigService {
  static List<VirtualFile> getConfigFiles(TypeScriptConfigService service) {
    return service.getConfigFiles().stream()
        .map(TypeScriptConfig::getConfigFile)
        .collect(ImmutableList.toImmutableList());
  }

  @Override
  default List<TypeScriptConfig> getConfigFiles() {
    return getConfigs();
  }

  List<VirtualFile> doGetConfigFiles();

  static List<TypeScriptConfig> getConfigs(TypeScriptConfigService service) {
    return service.getConfigFiles();
  }

  List<TypeScriptConfig> getConfigs();

  static ModificationTracker getConfigTracker(
      TypeScriptConfigService service, @Nullable VirtualFile file) {
    return service.getTracker();
  }

  ModificationTracker getConfigTracker(@Nullable VirtualFile file);

  @Nullable
  static TypeScriptConfig getPreferableConfig(
      VirtualFile scopeFile, ImmutableMap<VirtualFile, TypeScriptConfig> configs) {
    return TypeScriptConfigUtil.getNearestParentConfig(scopeFile, configs.values());
  }

  static void fireListener(
      TypeScriptConfigsChangedListener listener,
      ImmutableMap<VirtualFile, TypeScriptConfig> configs) {
    listener.afterUpdate(configs.values());
  }

  static TypeScriptConfigServiceImpl newImpl(
      Project project,
      TypeScriptConfigLibraryUpdater updater,
      PsiManager manager,
      TypeScriptLibraryProvider libraryProvider,
      TypeScriptConfigGraphCache configGraphCache) {
    return new TypeScriptConfigServiceImpl(project, updater, configGraphCache);
  }
}
