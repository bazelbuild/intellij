package com.google.idea.sdkcompat.formatter;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.FormattingModeAwareIndentAdjuster;
import com.intellij.util.IncorrectOperationException;

import java.util.Collection;

public abstract class DelegatingCodeStyleManagerAdapter extends CodeStyleManager
        implements FormattingModeAwareIndentAdjuster{
    protected final CodeStyleManager delegate;

    protected DelegatingCodeStyleManagerAdapter(CodeStyleManager delegate) {
      this.delegate = delegate;
    }

    /** #api203: changed ranges argument generic type to {@code ? extends TextRange} in 2021.1 */
    @Override
    public void reformatText(PsiFile file, Collection<TextRange> ranges)
        throws IncorrectOperationException {
      delegate.reformatText(file, ranges);
    }

    /** #api203: changed ranges argument generic type to {@code ? extends TextRange} in 2021.1 */
    @Override
    public void reformatTextWithContext(PsiFile file, Collection<TextRange> ranges)
        throws IncorrectOperationException {
      delegate.reformatTextWithContext(file, ranges);
    }

}
