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
package com.google.idea.blaze.base.actions;

import com.google.idea.blaze.base.lang.buildfile.psi.FuncallExpression;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.common.actionhelper.ActionPresentationHelper;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import java.awt.datatransfer.StringSelection;
import javax.annotation.Nullable;

/** Copies a blaze target path into the clipboard */
public class CopyBlazeTargetPathAction extends BlazeProjectAction {

  @Override
  protected void actionPerformedInBlazeProject(Project project, AnActionEvent e) {
    Label label = getSelectedTarget(e);
    if (label != null) {
      CopyPasteManager.getInstance().setContents(new StringSelection(label.toString()));
    }
  }

  @Override
  protected void updateForBlazeProject(Project project, AnActionEvent e) {
    Label label = getSelectedTarget(e);
    ActionPresentationHelper.of(e).hideIf(label == null).commit();
  }

  @Nullable
  private static Label getSelectedTarget(AnActionEvent e) {
    PsiElement psiElement = e.getData(CommonDataKeys.PSI_ELEMENT);
    if (!(psiElement instanceof FuncallExpression)) {
      return null;
    }
    return ((FuncallExpression) psiElement).resolveBuildLabel();
  }
}
