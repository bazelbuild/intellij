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
package com.google.idea.blaze.android.qsync;

import com.google.idea.blaze.base.qsync.QuerySyncManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.PackageIndex;
import com.intellij.openapi.roots.impl.DirectoryIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.MergeQuery;
import com.intellij.util.Query;

/**
 * Overrides {@link ProjectPackageIndexImpl}, adding a map from custom_package to its actual package
 * in the project.
 */
public class CustomPackageIndexExpander extends PackageIndex {
  private final Project project;

  public CustomPackageIndexExpander(Project project) {
    this.project = project;
  }

  @Override
  public VirtualFile[] getDirectoriesByPackageName(
      String packageName, boolean includeLibrarySources) {
    return getDirsByPackageName(packageName, includeLibrarySources)
        .toArray(VirtualFile.EMPTY_ARRAY);
  }

  @Override
  public Query<VirtualFile> getDirsByPackageName(
      String packageName, boolean includeLibrarySources) {
    DirectoryIndex directoryIndex = DirectoryIndex.getInstance(project);
    Query<VirtualFile> baseQuery =
        directoryIndex.getDirectoriesByPackageName(packageName, includeLibrarySources);
    QuerySyncManager querySyncManager = QuerySyncManager.getInstance(project);
    if (!querySyncManager.isProjectLoaded()) {
      return baseQuery;
    }

    for (String originalPackageName :
        querySyncManager.getCustomPackageMap().findPackageNames(packageName)) {
      baseQuery =
          new MergeQuery<>(
              baseQuery,
              directoryIndex.getDirectoriesByPackageName(
                  originalPackageName, includeLibrarySources));
    }
    return baseQuery;
  }

  // #api222: Add @Override
  public String getPackageNameByDirectory(VirtualFile directory) {
    return DirectoryIndex.getInstance(project).getPackageName(directory);
  }
}
