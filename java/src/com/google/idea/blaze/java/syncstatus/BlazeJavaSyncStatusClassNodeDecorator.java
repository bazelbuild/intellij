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

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ProjectViewNodeDecorator;
import com.intellij.ide.projectView.impl.nodes.ClassTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packageDependencies.ui.PackageDependenciesNode;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;

/** Grays out any unreachable java classes. */
public class BlazeJavaSyncStatusClassNodeDecorator implements ProjectViewNodeDecorator {
  @Override
  public void decorate(ProjectViewNode node, PresentationData data) {
    if (!(node instanceof ClassTreeNode)) {
      return;
    }
    PsiClass psiClass = ((ClassTreeNode) node).getPsiClass();
    if (psiClass == null) {
      return;
    }
    PsiFile psiFile = psiClass.getContainingFile();
    if (psiFile == null) {
      return;
    }
    VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile == null) {
      return;
    }

    Project project = node.getProject();
    if (SyncStatusHelper.isUnsynced(project, virtualFile)) {
      data.clearText();
      data.addText(psiClass.getName(), SimpleTextAttributes.GRAY_ATTRIBUTES);
      data.addText(" (unsynced)", SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
  }

  @Override
  public void decorate(PackageDependenciesNode node, ColoredTreeCellRenderer cellRenderer) {}
}
