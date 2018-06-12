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
package com.google.idea.blaze.base.syncstatus;

import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.targetmaps.SourceToTargetMap;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import java.util.Arrays;
import javax.annotation.Nullable;

/**
 * Implemented on a per-language basis to indicate source files of that language which weren't built
 * in the most recent sync.
 */
public interface SyncStatusContributor {

  ExtensionPointName<SyncStatusContributor> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.SyncStatusContributor");

  /**
   * Returns true if the given {@link VirtualFile} is indexed in the project, and of a type handled
   * by a {@link SyncStatusContributor}, but not built in the most recent sync.
   */
  static boolean isUnsynced(Project project, VirtualFile vf) {
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (projectData == null) {
      return false;
    }
    return isUnsynced(project, projectData, vf);
  }

  /**
   * Returns true if the given {@link PsiFile} is indexed in the project, and of a type handled by a
   * {@link SyncStatusContributor}, but not built in the most recent sync.
   */
  static boolean isUnsynced(Project project, BlazeProjectData projectData, VirtualFile vf) {
    if (!vf.isValid() || !vf.isInLocalFileSystem()) {
      return false;
    }
    boolean handledType =
        Arrays.stream(EP_NAME.getExtensions()).anyMatch(c -> c.handlesFile(projectData, vf));
    if (!handledType) {
      return false;
    }
    if (ProjectFileIndex.SERVICE.getInstance(project).getModuleForFile(vf) == null) {
      return false;
    }
    SourceToTargetMap sourceToTargetMap = SourceToTargetMap.getInstance(project);
    return sourceToTargetMap.getRulesForSourceFile(VfsUtilCore.virtualToIoFile(vf)).isEmpty();
  }

  /**
   * Converts a {@link ProjectViewNode} to a corresponding {@link PsiFile}, or returns null if this
   * contributor doesn't handle the given node type for this project.
   */
  @Nullable
  PsiFileAndName toPsiFileAndName(BlazeProjectData projectData, ProjectViewNode<?> node);

  /** Whether this {@link SyncStatusContributor} handles the given file type, for this project. */
  boolean handlesFile(BlazeProjectData projectData, VirtualFile file);

  /** The {@link PsiFile} and UI text associated with a {@link ProjectViewNode}. */
  class PsiFileAndName {
    final PsiFile psiFile;
    final String name;

    public PsiFileAndName(PsiFile psiFile, String name) {
      this.psiFile = psiFile;
      this.name = name;
    }
  }
}
