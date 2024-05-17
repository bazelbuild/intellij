/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.idea.sdkcompat.refactoring.rename;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Pass;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import javax.annotation.Nullable;

/** Compat content for DelegatingRenamePsiElementProcessor. */
public abstract class DelegatingRenamePsiElementProcessorBase extends RenamePsiElementProcessor {
  private volatile RenamePsiElementProcessor baseProcessor;

  @Override
  public boolean isInplaceRenameSupported() {
    return baseProcessor != null
        ? baseProcessor.isInplaceRenameSupported()
        : super.isInplaceRenameSupported();
  }

  @Override
  public boolean forcesShowPreview() {
    return baseProcessor != null ? baseProcessor.forcesShowPreview() : super.forcesShowPreview();
  }

  @Nullable
  protected abstract RenamePsiElementProcessor getDelegate(PsiElement element);

  @Nullable
  protected RenamePsiElementProcessor getDelegateAndStoreState(PsiElement element) {
    RenamePsiElementProcessor delegate = getDelegate(element);
    baseProcessor = delegate;
    return delegate;
  }

  @Override
  public void substituteElementToRename(
      PsiElement element, Editor editor, Pass<? super PsiElement> renameCallback) {
    RenamePsiElementProcessor processor = getDelegateAndStoreState(element);
    if (processor != null) {
      processor.substituteElementToRename(element, editor, renameCallback);
    } else {
      super.substituteElementToRename(element, editor, renameCallback);
    }
  }
}
