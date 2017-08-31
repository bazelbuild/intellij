/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.cpp.syncstatus;

import com.google.idea.blaze.base.targetmaps.SourceToTargetMap;
import com.google.idea.blaze.cpp.BlazeCWorkspace;
import com.google.idea.blaze.cpp.OCWorkspaceProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.cidr.lang.workspace.OCWorkspace;

/** Checks if we have sync data for the given C++ file. */
final class SyncStatusHelper {
  private SyncStatusHelper() {}

  static boolean isUnsynced(Project project, VirtualFile virtualFile) {
    if (!virtualFile.isInLocalFileSystem()) {
      return false;
    }
    if (ProjectFileIndex.SERVICE.getInstance(project).getModuleForFile(virtualFile) == null) {
      return false;
    }
    OCWorkspace workspace = OCWorkspaceProvider.getWorkspace(project);
    if (!(workspace instanceof BlazeCWorkspace)) {
      // Skip if the project isn't a Blaze project or doesn't have C support enabled anyway.
      return false;
    }
    if (workspace.getConfigurations().isEmpty()) {
      // The workspace configurations may not have been loaded yet.
      return false;
    }
    SourceToTargetMap sourceToTargetMap = SourceToTargetMap.getInstance(project);
    return sourceToTargetMap
        .getRulesForSourceFile(VfsUtilCore.virtualToIoFile(virtualFile))
        .isEmpty();
  }
}
