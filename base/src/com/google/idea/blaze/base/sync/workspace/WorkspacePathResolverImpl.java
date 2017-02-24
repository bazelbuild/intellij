/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.sync.workspace;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import java.io.File;
import javax.annotation.Nullable;

/** Uses the package path locations to resolve a workspace path. */
public class WorkspacePathResolverImpl implements WorkspacePathResolver {
  private static final long serialVersionUID = 3L;

  private final WorkspaceRoot workspaceRoot;

  public WorkspacePathResolverImpl(WorkspaceRoot workspaceRoot) {
    this.workspaceRoot = workspaceRoot;
  }

  @Override
  public ImmutableList<File> resolveToIncludeDirectories(WorkspacePath relativePath) {
    return ImmutableList.of(workspaceRoot.fileForPath(relativePath));
  }

  @Override
  public File findPackageRoot(String relativePath) {
    return workspaceRoot.directory();
  }

  @Nullable
  @Override
  public WorkspacePath getWorkspacePath(File absoluteFile) {
    return workspaceRoot.workspacePathForSafe(absoluteFile);
  }
}
