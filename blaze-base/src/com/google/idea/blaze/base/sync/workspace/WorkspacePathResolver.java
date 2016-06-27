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
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;

import javax.annotation.Nullable;
import java.io.File;
import java.io.Serializable;

/**
 * Uses workspace root, blaze roots and git5 tracked directory information to
 * convert workspace-relative paths to absolute files with a minimum of file system calls (typically none).
 */
public interface WorkspacePathResolver extends Serializable {
  /**
   * Resolves a workspace path to an absolute file.
   */
  @Nullable
  default File resolveToFile(WorkspacePath workspacepath) {
    return resolveToFile(workspacepath.relativePath());
  }

  /**
   * Resolves a workspace relative path to an absolute file.
   */
  @Nullable
  default File resolveToFile(String workspaceRelativePath) {
    File packageRoot = findPackageRoot(workspaceRelativePath);
    return packageRoot != null ? new File(packageRoot, workspaceRelativePath) : null;
  }

  /**
   * This method should be used for directories. In the case that the directory is tracked, it returns the directory under the workspace
   * root. If the directory is partially tracked (a sub directory is tracked), then the directory in the workspace and the directory under
   * READONLY are returned in that order in a list. If the directory is untracked, the path is examined to see if this method should return
   * a file under the execution root or a file under READONLY.
   */
  ImmutableList<File> resolveToIncludeDirectories(ExecutionRootPath executionRootPath);

  /**
   * Finds the package root directory that a workspace relative path is in.
   */
  @Nullable
  File findPackageRoot(String relativePath);

  WorkspaceRoot getWorkspaceRoot();
}
