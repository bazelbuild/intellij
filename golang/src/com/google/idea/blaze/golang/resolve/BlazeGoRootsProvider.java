/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.golang.resolve;

import com.goide.project.GoRootsProvider;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.sync.SyncCache;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Collection;
import javax.annotation.Nullable;

/** Provides a virtual GOROOT against which {@link BlazeVirtualGoPackage}s can be resolved. */
public class BlazeGoRootsProvider implements GoRootsProvider {

  @Override
  public Collection<VirtualFile> getGoPathRoots(
      @Nullable Project project, @Nullable Module module) {
    return ImmutableList.of();
  }

  @Override
  public Collection<VirtualFile> getGoPathSourcesRoots(
      @Nullable Project project, @Nullable Module module) {
    BlazeVirtualGoDirectory root = getGoPathSourceRoot(project);
    return root != null ? ImmutableList.of(root) : ImmutableList.of();
  }

  @Nullable
  static BlazeVirtualGoDirectory getGoPathSourceRoot(Project project) {
    return SyncCache.getInstance(project)
        .get(BlazeGoRootsProvider.class, (p, pd) -> BlazeVirtualGoDirectory.createRoot());
  }

  @Override
  public Collection<VirtualFile> getGoPathBinRoots(
      @Nullable Project project, @Nullable Module module) {
    return ImmutableList.of();
  }

  // Not in v171_4694_61
  // @Override
  public boolean isExternal() {
    return false;
  }
}
