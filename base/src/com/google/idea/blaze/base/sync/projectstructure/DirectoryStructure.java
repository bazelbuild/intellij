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
package com.google.idea.blaze.base.sync.projectstructure;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.prefetch.FetchExecutor;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.Collection;

/**
 * Directory structure representation used by {@link ContentEntryEditor}.
 *
 * <p>The purpose of this class is to pull out all file system operations out of the project
 * structure commit step, as this step locks the UI.
 */
public class DirectoryStructure {

  final ImmutableMap<WorkspacePath, DirectoryStructure> directories;

  private DirectoryStructure(ImmutableMap<WorkspacePath, DirectoryStructure> directories) {
    this.directories = directories;
  }

  public static ListenableFuture<DirectoryStructure> getRootDirectoryStructure(
      Project project, WorkspaceRoot workspaceRoot, ProjectViewSet projectViewSet) {
    return FetchExecutor.EXECUTOR.submit(
        () -> computeRootDirectoryStructure(project, workspaceRoot, projectViewSet));
  }

  private static DirectoryStructure computeRootDirectoryStructure(
      Project project, WorkspaceRoot workspaceRoot, ProjectViewSet projectViewSet) {
    ImportRoots importRoots =
        ImportRoots.builder(workspaceRoot, Blaze.getBuildSystem(project))
            .add(projectViewSet)
            .build();
    Collection<WorkspacePath> rootDirectories = importRoots.rootDirectories();
    ImmutableMap.Builder<WorkspacePath, DirectoryStructure> result = ImmutableMap.builder();
    for (WorkspacePath rootDirectory : rootDirectories) {
      walkDirectoryStructure(workspaceRoot, result, rootDirectory);
    }
    return new DirectoryStructure(result.build());
  }

  private static void walkDirectoryStructure(
      WorkspaceRoot workspaceRoot,
      ImmutableMap.Builder<WorkspacePath, DirectoryStructure> parent,
      WorkspacePath workspacePath) {
    File file = workspaceRoot.fileForPath(workspacePath);
    if (!FileOperationProvider.getInstance().isDirectory(file)) {
      return;
    }
    ImmutableMap.Builder<WorkspacePath, DirectoryStructure> result = ImmutableMap.builder();
    File[] children = FileOperationProvider.getInstance().listFiles(file);
    if (children != null) {
      for (File child : children) {
        WorkspacePath childWorkspacePath;
        try {
          childWorkspacePath = workspaceRoot.workspacePathFor(child);
        } catch (IllegalArgumentException e) {
          // stop at directories with unhandled characters.
          continue;
        }
        walkDirectoryStructure(workspaceRoot, result, childWorkspacePath);
      }
    }
    parent.put(workspacePath, new DirectoryStructure(result.build()));
  }
}
