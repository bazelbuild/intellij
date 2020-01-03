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
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.PackageIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;

/** Compat class overriding {@link ProjectPackageIndexImpl} */
public final class ProjectPackageIndexImplCompat extends PackageIndex {
  private final ProjectPackageIndexImpl delegate;

  public ProjectPackageIndexImplCompat(
      @NotNull Project project, @NotNull DirectoryIndex directoryIndex) {
    delegate = new ProjectPackageIndexImpl(project);
  }

  @NotNull
  @Override
  public VirtualFile[] getDirectoriesByPackageName(
      @NotNull String packageName, boolean includeLibrarySources) {
    return delegate.getDirectoriesByPackageName(packageName, includeLibrarySources);
  }

  @NotNull
  @Override
  public Query<VirtualFile> getDirsByPackageName(@NotNull String s, boolean b) {
    return delegate.getDirsByPackageName(s, b);
  }
}
