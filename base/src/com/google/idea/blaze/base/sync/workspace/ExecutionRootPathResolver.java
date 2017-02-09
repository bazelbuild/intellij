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
import com.intellij.openapi.util.io.FileUtil;
import java.io.File;
import java.util.List;

/**
 * Converts execution-root-relative paths to absolute files with a minimum of file system calls
 * (typically none).
 *
 * <p>Files which exist both underneath the execution root and within the workspace will be resolved
 * to workspace paths.
 */
public class ExecutionRootPathResolver {

  private final BlazeRoots blazeRoots;
  private final WorkspacePathResolver workspacePathResolver;

  public ExecutionRootPathResolver(
      BlazeRoots blazeRoots, WorkspacePathResolver workspacePathResolver) {
    this.blazeRoots = blazeRoots;
    this.workspacePathResolver = workspacePathResolver;
  }

  /**
   * This method should be used for directories. Returns all workspace files corresponding to the
   * given execution-root-relative path. If the file does not exist inside the workspace (e.g. for
   * blaze output files or external workspace files), returns the path rooted in the execution root.
   */
  public ImmutableList<File> resolveToIncludeDirectories(ExecutionRootPath path) {
    if (path.isAbsolute()) {
      return ImmutableList.of(path.getAbsoluteOrRelativeFile());
    }
    if (isInWorkspace(path)) {
      WorkspacePath workspacePath = new WorkspacePath(path.getAbsoluteOrRelativeFile().getPath());
      return workspacePathResolver.resolveToIncludeDirectories(workspacePath);
    }
    return ImmutableList.of(path.getFileRootedAt(blazeRoots.executionRoot));
  }

  private boolean isInWorkspace(ExecutionRootPath path) {
    boolean inOutputDir =
        ExecutionRootPath.isAncestor(blazeRoots.blazeBinExecutionRootPath, path, false)
            || ExecutionRootPath.isAncestor(blazeRoots.blazeGenfilesExecutionRootPath, path, false)
            || isExternalWorkspacePath(path);
    return !inOutputDir;
  }

  private static boolean isExternalWorkspacePath(ExecutionRootPath path) {
    List<String> pathComponents = FileUtil.splitPath(path.getAbsoluteOrRelativeFile().getPath());
    return pathComponents.size() > 1 && "external".equals(pathComponents.get(0));
  }
}
