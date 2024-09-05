/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.lang.buildfile.completion;

import com.google.idea.blaze.base.lang.buildfile.language.BuildFileLanguage;
import com.google.idea.blaze.base.lang.buildfile.psi.StringLiteral;
import com.intellij.codeInsight.lookup.CharFilter;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

/** Allows '@' to be typed (and appended to completion) inside a label from a build file. */
public final class BuildLabelCharFilter extends CharFilter {
  @Nullable
  public CharFilter.Result acceptChar(char c, int prefixLength, @NotNull Lookup lookup) {
    if (!lookup.isCompletion()) {
      return null;
    }

    PsiFile file = lookup.getPsiFile();
    if (file == null ||
            file.getLanguage() != BuildFileLanguage.INSTANCE ||
            PsiTreeUtil.getParentOfType(lookup.getPsiElement(), StringLiteral.class) == null) {
      return null;
    }

    switch (c) {
      case '@':
        return Result.ADD_TO_PREFIX;

      case '/':
        if (lookup.getCurrentItem() instanceof ExternalWorkspaceLookupElement) {
          return Result.SELECT_ITEM_AND_FINISH_LOOKUP;
        }
    }

    return null;
  }
}
