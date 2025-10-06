/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
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
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.io.IOException;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * Converts execution-root-relative paths to absolute files with a minimum of file system calls
 * (typically none).
 *
 * <p>Files which exist both underneath the execution root and within a workspace will be resolved
  * to paths within their workspace. This prevents those paths from being broken when a different
  * target is built.
 */
public interface ExecutionRootPathResolver {

  File externalPath = new File("external");

  @Nullable
  static ExecutionRootPathResolver fromProject(Project project) {
    final var projectData = BlazeProjectDataManager.getInstance(project).getBlazeProjectData();

    if (projectData == null) {
      return null;
    }

    return fromProjectData(project, projectData);
  }

  static ExecutionRootPathResolver fromProjectData(Project project, BlazeProjectData projectData) {
    return new ExecutionRootPathResolverImpl(
        Blaze.getBuildSystemProvider(project),
        WorkspaceRoot.fromProject(project),
        projectData.getBlazeInfo().getExecutionRoot(),
        projectData.getBlazeInfo().getOutputBase(),
        projectData.getWorkspacePathResolver(),
        projectData.getTargetMap()
    );
  }

  File resolveExecutionRootPath(ExecutionRootPath path);

  /**
   * This method should be used for directories. Returns all workspace files corresponding to the given
   * execution-root-relative path. If the file does not exist inside a workspace (e.g. for blaze output files), returns
   * the path rooted in the execution root.
   *
   * This function should not be used i.e. should not be necessary when VirtualIncludesService is enabled. But since
   * this is in base, it is not possible to check in the implementation that whether the service is enabled or not.
   */
  ImmutableList<File> resolveToIncludeDirectories(ExecutionRootPath path);

  /**
   * Resolves ExecutionRootPath to external workspace location and in case if item in external workspace is a link to
   * workspace root then follows it and returns a path to workspace root
   */
  @NotNull
  ImmutableList<File> resolveToExternalWorkspaceWithSymbolicLinkResolution(ExecutionRootPath path);

  File getExecutionRoot();
}
