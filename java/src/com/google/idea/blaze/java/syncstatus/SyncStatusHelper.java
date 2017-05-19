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
import com.google.idea.blaze.base.sync.SyncCache;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.java.sync.model.BlazeJavaSyncData;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.util.Set;

class SyncStatusHelper {

  static boolean isUnsynced(Project project, VirtualFile virtualFile) {
    if (!virtualFile.isInLocalFileSystem()) {
      return false;
    }
    if (ProjectFileIndex.SERVICE.getInstance(project).getModuleForFile(virtualFile) == null) {
      return false;
    }
    Set<File> syncedJavaFiles =
        SyncCache.getInstance(project)
            .get(SyncStatusHelper.class, SyncStatusHelper::getSyncedJavaFiles);
    if (syncedJavaFiles == null) {
      return false;
    }
    File file = new File(virtualFile.getPath());
    return !syncedJavaFiles.contains(file);
  }

  @SuppressWarnings("unused")
  private static Set<File> getSyncedJavaFiles(Project project, BlazeProjectData projectData) {
    BlazeJavaSyncData syncData = projectData.syncState.get(BlazeJavaSyncData.class);
    if (syncData == null) {
      return ImmutableSet.of();
    }
    ArtifactLocationDecoder artifactLocationDecoder = projectData.artifactLocationDecoder;
    return ImmutableSet.copyOf(
        artifactLocationDecoder.decodeAll(syncData.importResult.javaSourceFiles));
  }
}
