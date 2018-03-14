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
package com.google.idea.sdkcompat.codestyle;

import com.intellij.formatting.FormattingMode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.ChangedRangesInfo;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.FormattingModeAwareIndentAdjuster;
import com.intellij.util.IncorrectOperationException;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/** Adapter to bridge different SDK versions. */
public abstract class DelegatingCodeStyleManagerSdkCompatAdapter extends CodeStyleManager
    implements FormattingModeAwareIndentAdjuster {

  protected CodeStyleManager delegate;

  protected DelegatingCodeStyleManagerSdkCompatAdapter(CodeStyleManager delegate) {
    this.delegate = delegate;
  }

  @Override
  public void reformatTextWithContext(@NotNull PsiFile file, @NotNull ChangedRangesInfo info)
      throws IncorrectOperationException {
    List<TextRange> ranges = new ArrayList<>();
    if (info.insertedRanges != null) {
      ranges.addAll(info.insertedRanges);
    }
    ranges.addAll(info.allChangedRanges);
    this.reformatTextWithContext(file, ranges);
  }

  @Override
  public int getSpacing(@NotNull PsiFile file, int offset) {
    return delegate.getSpacing(file, offset);
  }

  @Override
  public int getMinLineFeeds(@NotNull PsiFile file, int offset) {
    return delegate.getMinLineFeeds(file, offset);
  }

  /** Uses same fallback as {@link CodeStyleManager#getCurrentFormattingMode}. */
  @Override
  public FormattingMode getCurrentFormattingMode() {
    if (delegate instanceof FormattingModeAwareIndentAdjuster) {
      return ((FormattingModeAwareIndentAdjuster) delegate).getCurrentFormattingMode();
    }
    return FormattingMode.REFORMAT;
  }

  @Override
  public int adjustLineIndent(
      @NotNull final Document document, final int offset, FormattingMode mode)
      throws IncorrectOperationException {
    if (delegate instanceof FormattingModeAwareIndentAdjuster) {
      return ((FormattingModeAwareIndentAdjuster) delegate)
          .adjustLineIndent(document, offset, mode);
    }
    return offset;
  }
}
