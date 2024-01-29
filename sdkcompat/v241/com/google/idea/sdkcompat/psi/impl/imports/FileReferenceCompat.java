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

package com.google.idea.sdkcompat.psi.impl.imports;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import java.util.Collection;

/** Compat class for FileReference. */
public abstract class FileReferenceCompat extends FileReference {
  public FileReferenceCompat(
      FileReferenceSet fileReferenceSet, TextRange range, int index, String text) {
    super(fileReferenceSet, range, index, text);
  }

  @Override
  protected void innerResolveInContext(
      String text,
      PsiFileSystemItem context,
      Collection<? super ResolveResult> result,
      boolean caseSensitive) {
    if (doInnerResolveInContext(text, context, result, caseSensitive)) {
      super.innerResolveInContext(text, context, result, caseSensitive);
    }
  }

  protected abstract boolean doInnerResolveInContext(
      String text,
      PsiFileSystemItem context,
      Collection<? super ResolveResult> result,
      boolean caseSensitive);
}
