package com.google.idea.sdkcompat.codestyle;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.ChangedRangesInfo;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/** Adapter to bridge different SDK versions. */
public abstract class DelegatingCodeStyleManagerSdkCompatAdapter extends CodeStyleManager {

  protected CodeStyleManager delegate;

  protected DelegatingCodeStyleManagerSdkCompatAdapter(CodeStyleManager delegate) {
    this.delegate = delegate;
  }

  @Override
  public void reformatTextWithContext(@NotNull PsiFile file, @NotNull ChangedRangesInfo info)
      throws IncorrectOperationException {
    List<TextRange> ranges = new ArrayList<>();
    ranges.addAll(info.insertedRanges);
    ranges.addAll(info.allChangedRanges);
    this.reformatTextWithContext(file, ranges);
  }
}
