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
package com.google.idea.blaze.skylark.debugger.impl;

import com.google.idea.blaze.base.lang.buildfile.language.BuildFileType;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.psi.StatementList;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProviderBase;
import javax.annotation.Nullable;

/** Provides the editor environment used for debugger evaluation. */
class SkylarkDebuggerEditorsProvider extends XDebuggerEditorsProviderBase {

  @Override
  protected PsiFile createExpressionCodeFragment(
      Project project, String text, @Nullable PsiElement context, boolean isPhysical) {
    text = text.trim();
    SkylarkExpressionCodeFragment fragment =
        new SkylarkExpressionCodeFragment(project, codeFragmentFileName(context), text, isPhysical);
    fragment.setContext(getStatement(context));
    return fragment;
  }

  @Override
  public FileType getFileType() {
    return BuildFileType.INSTANCE;
  }

  private static String codeFragmentFileName(@Nullable PsiElement context) {
    if (context == null) {
      return "fragment.bzl";
    }
    PsiFile contextFile = context.getContainingFile();
    return contextFile != null ? contextFile.getName() : "fragment.bzl";
  }

  /** Find the correct context for completion suggestions: the start of the containing statement. */
  @Nullable
  private static PsiElement getStatement(@Nullable PsiElement element) {
    if (element == null) {
      return null;
    }
    PsiElement statementList = getStatementList(element);
    if (statementList == null) {
      return element;
    }
    PsiElement context = getParentRightBefore(element, statementList);
    return context == null ? element : context;
  }

  @Nullable
  private static PsiElement getStatementList(PsiElement element) {
    if (element instanceof BuildFile || element instanceof StatementList) {
      return element;
    }
    return PsiTreeUtil.getParentOfType(element, BuildFile.class, StatementList.class);
  }

  /**
   * Returns ancestor of the element that is also direct child of the given super parent.
   *
   * @param element element to start search from
   * @param superParent direct parent of the desired ancestor
   */
  @Nullable
  private static PsiElement getParentRightBefore(PsiElement element, PsiElement superParent) {
    return PsiTreeUtil.findFirstParent(
        element, false, element1 -> element1.getParent() == superParent);
  }
}
