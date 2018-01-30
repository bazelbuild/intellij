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
import java.io.Serializable;
import javax.annotation.Nullable;

/**
 * Converts workspace-relative paths to absolute files with a minimum of file system calls
 * (typically none).
 */
public interface WorkspacePathResolver extends SimpleWorkspacePathResolver, Serializable {
  /** Resolves a workspace path to an absolute file. */
  default File resolveToFile(WorkspacePath workspacepath) {
    return resolveToFile(workspacepath.relativePath());
  }

  /**
   * This method should be used for directories. Returns all workspace files corresponding to the
   * given workspace path.
   */
  ImmutableList<File> resolveToIncludeDirectories(WorkspacePath relativePath);

  /**
   * Finds the workspace root directory that an absolute file lies under. Returns null if the file
   * is not in a known workspace.
   */
  @Nullable
  WorkspaceRoot findWorkspaceRoot(File absoluteFile);
}
