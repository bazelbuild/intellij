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
package com.google.idea.blaze.base.treeview;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

/**
 * A PsiDirectoryNode that doesn't render module names or source roots.
 */
public class BlazePsiDirectoryNode extends PsiDirectoryNode {
  public BlazePsiDirectoryNode(@NotNull PsiDirectoryNode original) {
    this(original.getProject(), original.getValue(), original.getSettings());
  }

  public BlazePsiDirectoryNode(Project project, PsiDirectory directory, ViewSettings settings) {
    super(project, directory, settings);
  }

  @Override
  protected boolean shouldShowModuleName() {
    return false;
  }

  @Override
  protected boolean shouldShowSourcesRoot() {
    return false;
  }

  @Override
  protected void updateImpl(PresentationData data) {
    super.updateImpl(data);
    PsiDirectory psiDirectory = getValue();
    assert psiDirectory != null;
    String text = psiDirectory.getName();

    data.setPresentableText(text);
    data.clearText();
    data.addText(text, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    data.setLocationString("");
  }
}
