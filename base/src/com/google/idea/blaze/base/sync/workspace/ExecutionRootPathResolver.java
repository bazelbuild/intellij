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
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
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
public class ExecutionRootPathResolver {
  private static final Logger LOG = Logger.getInstance(ExecutionRootPathResolver.class);
  private final static String externalPrefix = "external";
  final static File externalPath = new File(externalPrefix);
  private final ImmutableList<String> buildArtifactDirectories;
  private final File executionRoot;
  private final File outputBase;
  private final WorkspacePathResolver workspacePathResolver;
  private final TargetMap targetMap;

  public ExecutionRootPathResolver(
      BuildSystemProvider buildSystemProvider,
      WorkspaceRoot workspaceRoot,
      File executionRoot,
      File outputBase,
      WorkspacePathResolver workspacePathResolver,
      TargetMap targetMap) {
    this.buildArtifactDirectories = buildArtifactDirectories(buildSystemProvider, workspaceRoot);
    this.executionRoot = executionRoot;
    this.outputBase = outputBase;
    this.workspacePathResolver = workspacePathResolver;
    this.targetMap = targetMap;
  }

  @Nullable
  public static ExecutionRootPathResolver fromProject(Project project) {
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (projectData == null) {
      return null;
    }
    return new ExecutionRootPathResolver(
        Blaze.getBuildSystemProvider(project),
        WorkspaceRoot.fromProject(project),
        projectData.getBlazeInfo().getExecutionRoot(),
        projectData.getBlazeInfo().getOutputBase(),
        projectData.getWorkspacePathResolver(),
        projectData.getTargetMap());
  }

  private static ImmutableList<String> buildArtifactDirectories(
      BuildSystemProvider buildSystemProvider, WorkspaceRoot workspaceRoot) {
    return buildSystemProvider.buildArtifactDirectories(workspaceRoot);
  }

  public File resolveExecutionRootPath(ExecutionRootPath path) {
    if (path.isAbsolute()) {
      return path.getAbsoluteOrRelativeFile();
    }
    String firstPathComponent = getFirstPathComponent(path.getAbsoluteOrRelativeFile().getPath());
    if (buildArtifactDirectories.contains(firstPathComponent)) {
      // Build artifacts accumulate under the execution root, independent of symlink settings
      return path.getFileRootedAt(executionRoot);
    }
    if (firstPathComponent.equals("external")) { // In external workspace
      // External workspaces accumulate under the output base.
      // The symlinks to them under the execution root are unstable, and only linked per build.
      return path.getFileRootedAt(outputBase);
    }
    // Else, in main workspace
    return workspacePathResolver.resolveToFile(path.getAbsoluteOrRelativeFile().getPath());
  }

  /**
   * This method should be used for directories. Returns all workspace files corresponding to the
   * given execution-root-relative path. If the file does not exist inside a workspace (e.g. for
   * blaze output files), returns the path rooted in the execution root.
   */
  public ImmutableList<File> resolveToIncludeDirectories(ExecutionRootPath path) {
    if (path.isAbsolute()) {
      return ImmutableList.of(path.getAbsoluteOrRelativeFile());
    }
    String firstPathComponent = getFirstPathComponent(path.getAbsoluteOrRelativeFile().getPath());
    if (buildArtifactDirectories.contains(firstPathComponent)) {
      // Build artifacts accumulate under the execution root, independent of symlink settings

      if(Registry.is("bazel.sync.resolve.virtual.includes") &&
          VirtualIncludesHandler.containsVirtualInclude(path)) {
        // Resolve virtual_include from execution root either to local or external workspace for correct code insight
        ImmutableList<File> resolved = ImmutableList.of();
        try {
          resolved = VirtualIncludesHandler.resolveVirtualInclude(path, outputBase,
              workspacePathResolver, targetMap);
        } catch (Throwable throwable) {
          LOG.error("Failed to resolve virtual includes for " + path, throwable);
        }

        return resolved.isEmpty()
            ? ImmutableList.of(path.getFileRootedAt(executionRoot))
            : resolved;
      } else {
        return ImmutableList.of(path.getFileRootedAt(executionRoot));
      }
    }
    if (firstPathComponent.equals(externalPrefix)) { // In external workspace
      // External workspaces accumulate under the output base.
      // The symlinks to them under the execution root are unstable, and only linked per build.
      return resolveToExternalWorkspaceWithSymbolicLinkResolution(path);
    }
    // Else, in main workspace
    WorkspacePath workspacePath =
        WorkspacePath.createIfValid(path.getAbsoluteOrRelativeFile().getPath());
    if (workspacePath != null) {
      return workspacePathResolver.resolveToIncludeDirectories(workspacePath);
    } else {
      return ImmutableList.of();
    }
  }

  /**
   * Resolves ExecutionRootPath to external workspace location and in case if item in external
   * workspace is a link to workspace root then follows it and returns a path to workspace root
   */
  @NotNull
  private ImmutableList<File> resolveToExternalWorkspaceWithSymbolicLinkResolution(
      ExecutionRootPath path) {
    File fileInExecutionRoot = path.getFileRootedAt(outputBase);

    try {
      File realPath = fileInExecutionRoot.toPath().toRealPath().toFile();
      if (workspacePathResolver.getWorkspacePath(realPath) != null) {
        return ImmutableList.of(realPath);
      }
    } catch (IOException ioException) {
      LOG.warn("Failed to resolve real path for " + fileInExecutionRoot, ioException);
    }

    return ImmutableList.of(fileInExecutionRoot);
  }

  public File getExecutionRoot() {
    return executionRoot;
  }

  private static String getFirstPathComponent(String path) {
    int index = path.indexOf(File.separatorChar);
    return index == -1 ? path : path.substring(0, index);
  }
}
