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
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.intellij.openapi.diagnostic.Logger;
import java.io.File;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

public class ExecutionRootPathResolverImpl implements ExecutionRootPathResolver {

  private static final Logger LOG = Logger.getInstance(ExecutionRootPathResolverImpl.class);
  private final static String externalPrefix = "external";
  private final ImmutableList<String> buildArtifactDirectories;
  private final File executionRoot;
  private final File outputBase;
  private final WorkspacePathResolver workspacePathResolver;
  private final TargetMap targetMap;

  @VisibleForTesting
  public ExecutionRootPathResolverImpl(
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


  private static ImmutableList<String> buildArtifactDirectories(
      BuildSystemProvider buildSystemProvider, WorkspaceRoot workspaceRoot) {
    return buildSystemProvider.buildArtifactDirectories(workspaceRoot);
  }

  @Override
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

  @Override
  public ImmutableList<File> resolveToIncludeDirectories(ExecutionRootPath path) {
    if (path.isAbsolute()) {
      return ImmutableList.of(path.getAbsoluteOrRelativeFile());
    }
    String firstPathComponent = getFirstPathComponent(path.getAbsoluteOrRelativeFile().getPath());
    if (buildArtifactDirectories.contains(firstPathComponent)) {
      // Build artifacts accumulate under the execution root, independent of symlink settings

      if(VirtualIncludesHandler.useHeuristic() && VirtualIncludesHandler.containsVirtualInclude(path)) {
        // Resolve virtual_include from execution root either to local or external workspace for correct code insight
        ImmutableList<File> resolved = ImmutableList.of();
        try {
          resolved = VirtualIncludesHandler.resolveVirtualInclude(
              path,
              outputBase,
              workspacePathResolver,
              targetMap);
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

  @Override
  @NotNull
  public ImmutableList<File> resolveToExternalWorkspaceWithSymbolicLinkResolution(
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

  @Override
  public File getExecutionRoot() {
    return executionRoot;
  }

  private static String getFirstPathComponent(String path) {
    int index = path.indexOf(File.separatorChar);
    return index == -1 ? path : path.substring(0, index);
  }
}
