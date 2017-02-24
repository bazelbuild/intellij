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
import com.google.idea.blaze.base.bazel.BuildSystemProvider;
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.settings.Blaze.BuildSystem;
import java.io.File;

/**
 * Converts execution-root-relative paths to absolute files with a minimum of file system calls
 * (typically none).
 *
 * <p>Files which exist both underneath the execution root and within the workspace will be resolved
 * to workspace paths.
 */
public class ExecutionRootPathResolver {

  private final ImmutableList<String> buildArtifactDirectories;
  private final File executionRoot;
  private final WorkspacePathResolver workspacePathResolver;

  public ExecutionRootPathResolver(
      BuildSystem buildSystem,
      WorkspaceRoot workspaceRoot,
      File executionRoot,
      WorkspacePathResolver workspacePathResolver) {
    this.buildArtifactDirectories = buildArtifactDirectories(buildSystem, workspaceRoot);
    this.executionRoot = executionRoot;
    this.workspacePathResolver = workspacePathResolver;
  }

  private static ImmutableList<String> buildArtifactDirectories(
      BuildSystem buildSystem, WorkspaceRoot workspaceRoot) {
    BuildSystemProvider provider = BuildSystemProvider.getBuildSystemProvider(buildSystem);
    if (provider == null) {
      provider = BuildSystemProvider.defaultBuildSystem();
    }
    return provider.buildArtifactDirectories(workspaceRoot);
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
    return ImmutableList.of(path.getFileRootedAt(executionRoot));
  }

  private boolean isInWorkspace(ExecutionRootPath path) {
    String firstPathComponent = getFirstPathComponent(path.getAbsoluteOrRelativeFile().getPath());
    return !buildArtifactDirectories.contains(firstPathComponent)
        && !isExternalWorkspacePath(firstPathComponent);
  }

  private static String getFirstPathComponent(String path) {
    int index = path.indexOf(File.separatorChar);
    return index == -1 ? path : path.substring(0, index);
  }

  private static boolean isExternalWorkspacePath(String firstPathComponent) {
    return firstPathComponent.equals("external");
  }
}
