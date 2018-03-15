/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.cpp.syncstatus;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ProjectViewNodeDecorator;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packageDependencies.ui.PackageDependenciesNode;
import com.intellij.psi.PsiFile;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.jetbrains.cidr.lang.psi.OCFile;

/** Grays out any unreachable (from project view targets) C++ files. */
public class BlazeCppSyncStatusFileNodeDecorator implements ProjectViewNodeDecorator {

  @Override
  public void decorate(ProjectViewNode node, PresentationData data) {
    if (!(node instanceof PsiFileNode)) {
      return;
    }
    PsiFile psiFile = ((PsiFileNode) node).getValue();
    if (!(psiFile instanceof OCFile)) {
      return;
    }
    VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile == null) {
      return;
    }
    Project project = node.getProject();
    if (SyncStatusHelper.isUnsynced(project, virtualFile)) {
      data.clearText();
      data.addText(psiFile.getName(), SimpleTextAttributes.GRAY_ATTRIBUTES);
      data.addText(" (unsynced)", SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
  }

  @Override
  public void decorate(PackageDependenciesNode node, ColoredTreeCellRenderer cellRenderer) {}
}
