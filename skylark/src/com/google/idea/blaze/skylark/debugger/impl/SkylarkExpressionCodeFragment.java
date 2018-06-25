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
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SingleRootFileViewProvider;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.file.impl.FileManager;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.testFramework.LightVirtualFile;
import javax.annotation.Nullable;

/**
 * TODO(brendandouglas): inject bindings from debug frame directly (see example in
 * XsltDebuggerEditorsProvider)
 */
class SkylarkExpressionCodeFragment extends BuildFile {

  @Nullable private PsiElement context;
  private boolean isPhysical;
  @Nullable private FileViewProvider viewProvider;

  public SkylarkExpressionCodeFragment(
      Project project, String fileName, String text, boolean isPhysical) {
    super(
        PsiManagerEx.getInstanceEx(project)
            .getFileManager()
            .createFileViewProvider(
                new LightVirtualFile(fileName, BuildFileType.INSTANCE, text), isPhysical));
    this.isPhysical = isPhysical;
    ((SingleRootFileViewProvider) getViewProvider()).forceCachedPsi(this);
  }

  @Override
  protected PsiFileImpl clone() {
    SkylarkExpressionCodeFragment clone =
        (SkylarkExpressionCodeFragment) cloneImpl((FileElement) calcTreeElement().clone());
    clone.isPhysical = false;
    clone.myOriginalFile = this;
    FileManager fileManager = ((PsiManagerEx) getManager()).getFileManager();
    SingleRootFileViewProvider cloneViewProvider =
        (SingleRootFileViewProvider)
            fileManager.createFileViewProvider(
                new LightVirtualFile(getName(), getLanguage(), getText()), false);
    cloneViewProvider.forceCachedPsi(clone);
    clone.viewProvider = cloneViewProvider;
    return clone;
  }

  public PsiElement getContext() {
    return context != null && context.isValid() ? context : super.getContext();
  }

  @Override
  public FileViewProvider getViewProvider() {
    return viewProvider != null ? viewProvider : super.getViewProvider();
  }

  @Override
  public boolean isPhysical() {
    return isPhysical;
  }

  public void setContext(@Nullable PsiElement context) {
    this.context = context;
  }
}
