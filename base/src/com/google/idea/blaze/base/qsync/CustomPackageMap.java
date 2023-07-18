/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.qsync;

import com.google.common.collect.ImmutableSetMultimap;
import com.google.idea.blaze.qsync.project.ProjectProto;
import java.util.Set;

/**
 * A map from custom package to in project package name. We need to access this data even after sync
 * completed, so store it separately. It should be updated after every sync.
 */
public class CustomPackageMap {
  private ImmutableSetMultimap<String, String> customPackageToPackageNameMap =
      ImmutableSetMultimap.of();

  public void setCustomPackageToPackageNameMap(ProjectProto.Project project) {
    ImmutableSetMultimap.Builder<String, String> builder = ImmutableSetMultimap.builder();
    for (ProjectProto.Module module : project.getModulesList()) {
      for (ProjectProto.CustomPackageInfo customPackageInfo :
          module.getAndroidCustomPackageInfosList()) {
        builder.putAll(
            customPackageInfo.getAndroidCustomPackage(),
            customPackageInfo.getOriginalPackagesList());
      }
    }
    customPackageToPackageNameMap = builder.build();
  }

  public Set<String> findPackageNames(String customPackage) {
    return customPackageToPackageNameMap.get(customPackage);
  }
}
