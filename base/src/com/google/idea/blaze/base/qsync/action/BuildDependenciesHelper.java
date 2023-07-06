/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.qsync.action;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.qsync.QuerySyncManager;
import com.google.idea.blaze.base.sync.status.BlazeSyncStatus;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Helper class for actions that build dependencies for source files, to allow the core logic to be
 * shared.
 */
public class BuildDependenciesHelper {

  private final Project project;
  private final QuerySyncManager syncManager;

  BuildDependenciesHelper(Project project) {
    this.project = project;
    syncManager = QuerySyncManager.getInstance(project);
  }

  boolean canEnableAnalysisNow() {
    return !BlazeSyncStatus.getInstance(project).syncInProgress();
  }

  Optional<Path> getRelativePathToEnableAnalysisFor(VirtualFile virtualFile) {
    if (virtualFile == null || !virtualFile.isInLocalFileSystem()) {
      return Optional.empty();
    }
    Path workspaceRoot = WorkspaceRoot.fromProject(project).path();
    Path filePath = virtualFile.toNioPath();
    if (!filePath.startsWith(workspaceRoot)) {
      return Optional.empty();
    }

    Path relative = workspaceRoot.relativize(filePath);
    if (!syncManager.canEnableAnalysisFor(relative)) {
      return Optional.empty();
    }
    return Optional.of(relative);
  }

  void enableAnalysis(Path workspaceRelativePath) {
    enableAnalysis(ImmutableList.of(workspaceRelativePath));
  }

  void enableAnalysis(ImmutableList<Path> workspaceRelativePaths) {
    syncManager.enableAnalysis(workspaceRelativePaths);
  }
}
