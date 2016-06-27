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
package com.google.idea.blaze.base.vcs;

import com.google.common.collect.Lists;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.sections.DirectoryEntry;
import com.google.idea.blaze.base.projectview.section.sections.DirectorySection;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.impl.DefaultVcsRootPolicy;
import com.intellij.openapi.vcs.impl.projectlevelman.NewMappings;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.List;

/**
 * Converts project-level mappings to actual VCS roots.
 */
public class BlazeDefaultVcsRootPolicy extends DefaultVcsRootPolicy {

  @NotNull
  private final Project project;

  public BlazeDefaultVcsRootPolicy(@NotNull Project project) {
    this.project = project;
  }

  @Override
  public void addDefaultVcsRoots(
    NewMappings mappingList,
    @NotNull String vcsName,
    List<VirtualFile> result) {
    result.addAll(getVcsRoots());
  }

  @NotNull
  @Override
  public Collection<VirtualFile> getDirtyRoots() {
    return getVcsRoots();
  }

  private List<VirtualFile> getVcsRoots() {
    List<VirtualFile> result = Lists.newArrayList();
    BlazeImportSettings importSettings = BlazeImportSettingsManager.getInstance(project)
      .getImportSettings();
    if (importSettings == null) {
      return result;
    }
    ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
    if (projectViewSet == null) {
      return result;
    }
    WorkspaceRoot workspaceRoot = WorkspaceRoot.fromImportSettings(importSettings);

    for (DirectoryEntry entry : projectViewSet.listItems(DirectorySection.KEY)) {
      if (!entry.included) {
        continue;
      }
      File packageDir = workspaceRoot.fileForPath(entry.directory);

      VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile(packageDir);
      if (virtualFile != null) {
        result.add(virtualFile);
      }
    }
    return result;
  }

  @Override
  public boolean matchesDefaultMapping(final VirtualFile file, final Object matchContext) {
    for (VirtualFile directory : getVcsRoots()) {
      if (VfsUtilCore.isAncestor(directory, file, false)) {
        return true;
      }
    }
    return false;
  }

  @Override
  @Nullable
  public Object getMatchContext(final VirtualFile file) {
    return null;
  }

  @Override
  @Nullable
  public VirtualFile getVcsRootFor(final VirtualFile file) {
    for (VirtualFile directory : getVcsRoots()) {
      if (VfsUtilCore.isAncestor(directory, file, false)) {
        return directory;
      }
    }
    return null;
  }
}
