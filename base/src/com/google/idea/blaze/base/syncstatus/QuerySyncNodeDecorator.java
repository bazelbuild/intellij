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

import static java.util.Arrays.stream;

import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.qsync.DependencyTracker;
import com.google.idea.blaze.base.qsync.QuerySync;
import com.google.idea.blaze.base.qsync.QuerySyncManager;
import com.google.idea.blaze.base.syncstatus.SyncStatusContributor.PsiFileAndName;
import com.google.idea.blaze.common.Label;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ProjectViewNodeDecorator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packageDependencies.ui.PackageDependenciesNode;
import com.intellij.psi.PsiFile;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import java.util.Objects;
import java.util.Set;

/** Shows the number of external dependencies that need to be build for a source file. */
public class QuerySyncNodeDecorator implements ProjectViewNodeDecorator {

  @Override
  public void decorate(ProjectViewNode<?> node, PresentationData data) {
    if (!QuerySync.isEnabled()) {
      return;
    }
    Project project = node.getProject();
    if (project == null) {
      return;
    }
    PsiFileAndName psiFileAndName =
        stream(SyncStatusContributor.EP_NAME.getExtensions())
            .map(c -> c.toPsiFileAndName(node))
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);

    if (psiFileAndName == null) {
      return;
    }

    PsiFile file = psiFileAndName.psiFile;
    VirtualFile vf = file.getVirtualFile();
    WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProject(project);
    if (!workspaceRoot.isInWorkspace(vf)) {
      return;
    }
    DependencyTracker deps = QuerySyncManager.getInstance(project).getDependencyTracker();
    if (deps == null) {
      return;
    }
    Set<Label> targets = deps.getPendingTargets(workspaceRoot.relativize(vf));
    if (targets != null && !targets.isEmpty()) {
      String text = data.getPresentableText();
      data.clearText();
      data.addText(text, SimpleTextAttributes.REGULAR_ATTRIBUTES);
      data.addText(String.format(" (%s)", targets.size()), SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
  }

  @Override
  public void decorate(PackageDependenciesNode node, ColoredTreeCellRenderer cellRenderer) {}
}
