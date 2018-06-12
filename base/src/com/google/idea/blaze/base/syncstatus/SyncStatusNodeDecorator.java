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
package com.google.idea.blaze.base.syncstatus;

import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.syncstatus.SyncStatusContributor.PsiFileAndName;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ProjectViewNodeDecorator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packageDependencies.ui.PackageDependenciesNode;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import java.util.Arrays;
import java.util.Objects;
import javax.annotation.Nullable;

/** Grays out any project view nodes (of a handled type) unreachable from project view targets. */
public class SyncStatusNodeDecorator implements ProjectViewNodeDecorator {
  @Override
  @SuppressWarnings("rawtypes")
  public void decorate(ProjectViewNode node, PresentationData data) {
    Project project = node.getProject();
    if (project == null) {
      return;
    }
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (projectData == null) {
      return;
    }
    PsiFileAndName psiFileAndName = toPsiFile(projectData, node);
    if (psiFileAndName == null) {
      return;
    }
    VirtualFile vf = psiFileAndName.psiFile.getVirtualFile();
    if (vf == null || !SyncStatusContributor.isUnsynced(project, projectData, vf)) {
      return;
    }
    data.clearText();
    data.addText(psiFileAndName.name, SimpleTextAttributes.GRAY_ATTRIBUTES);
    data.addText(" (unsynced)", SimpleTextAttributes.GRAY_ATTRIBUTES);
  }

  @Nullable
  private static PsiFileAndName toPsiFile(BlazeProjectData projectData, ProjectViewNode<?> node) {
    return Arrays.stream(SyncStatusContributor.EP_NAME.getExtensions())
        .map(c -> c.toPsiFileAndName(projectData, node))
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  @Override
  public void decorate(PackageDependenciesNode node, ColoredTreeCellRenderer cellRenderer) {}
}
