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
package com.google.idea.blaze.java.syncstatus;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.sync.BlazeSyncParams.SyncMode;
import com.google.idea.blaze.base.sync.SyncListener;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.java.sync.model.BlazeJavaSyncData;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.util.Set;

class SyncStatusHelper {

  static SyncStatusHelper getInstance(Project project) {
    return ServiceManager.getService(project, SyncStatusHelper.class);
  }

  private final Project project;
  private Set<File> syncedJavaFiles = null;

  SyncStatusHelper(Project project) {
    this.project = project;
  }

  boolean isUnsynced(VirtualFile virtualFile) {
    if (!virtualFile.isInLocalFileSystem()) {
      return false;
    }
    if (ProjectFileIndex.SERVICE.getInstance(project).getModuleForFile(virtualFile) == null) {
      return false;
    }
    if (syncedJavaFiles == null) {
      syncedJavaFiles = refresh();
    }
    if (syncedJavaFiles == null) {
      return false;
    }
    File file = new File(virtualFile.getPath());
    return !syncedJavaFiles.contains(file);
  }

  Set<File> refresh() {
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (blazeProjectData == null) {
      return null;
    }
    BlazeJavaSyncData syncData = blazeProjectData.syncState.get(BlazeJavaSyncData.class);
    if (syncData == null) {
      return null;
    }
    ArtifactLocationDecoder artifactLocationDecoder = blazeProjectData.artifactLocationDecoder;
    return ImmutableSet.<File>builder()
        .addAll(artifactLocationDecoder.decodeAll(syncData.importResult.javaSourceFiles))
        .build();
  }

  static class UpdateSyncStatusMap extends SyncListener.Adapter {
    @Override
    public void onSyncComplete(
        Project project,
        BlazeContext context,
        BlazeImportSettings importSettings,
        ProjectViewSet projectViewSet,
        BlazeProjectData blazeProjectData,
        SyncMode syncMode,
        SyncResult syncResult) {
      getInstance(project).syncedJavaFiles = null;
    }
  }
}
