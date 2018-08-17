/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.sync.autosync;

import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.google.idea.blaze.base.sync.BlazeSyncParams;
import com.google.idea.blaze.base.sync.BlazeSyncParams.SyncMode;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import javax.annotation.Nullable;

class BuildFileAutoSyncProvider implements AutoSyncProvider {

  @Override
  public boolean isSyncSensitiveFile(Project project, VirtualFile file) {
    return isBuildFile(project, file) && isInProject(project, file);
  }

  private static boolean isBuildFile(Project project, VirtualFile file) {
    return Blaze.getBuildSystemProvider(project).isBuildFile(file.getName());
  }

  private static boolean isInProject(Project project, VirtualFile file) {
    // for now, accept any file under the indexed project directories. Later, we may want to check
    // whether there are actually target map targets in the corresponding package
    WorkspacePath relativePath = getWorkspacePath(project, file);
    if (relativePath == null) {
      return false;
    }
    ImportRoots roots = ImportRoots.forProjectSafe(project);
    return roots != null && roots.containsWorkspacePath(relativePath);
  }

  @Nullable
  private static WorkspacePath getWorkspacePath(Project project, VirtualFile file) {
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    return projectData != null
        ? projectData.workspacePathResolver.getWorkspacePath(new File(file.getPath()))
        : null;
  }

  @Nullable
  @Override
  public BlazeSyncParams getAutoSyncParamsForFile(Project project, VirtualFile modifiedFile) {
    if (!BlazeUserSettings.getInstance().getResyncAutomatically()
        || !isSyncSensitiveFile(project, modifiedFile)) {
      return null;
    }
    WorkspacePath path = getWorkspacePath(project, modifiedFile);
    if (path == null || path.getParent() == null) {
      return null;
    }
    return new BlazeSyncParams.Builder(AUTO_SYNC_TITLE, SyncMode.PARTIAL)
        .addTargetExpression(TargetExpression.allFromPackageNonRecursive(path.getParent()))
        .setBackgroundSync(true)
        .build();
  }
}
