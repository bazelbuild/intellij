package com.google.idea.sdkcompat.formatter;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.FormattingModeAwareIndentAdjuster;
import com.intellij.util.IncorrectOperationException;

import java.util.Collection;

public abstract class ExternalFormatterCodeStyleManagerAdapter extends CodeStyleManager
        implements FormattingModeAwareIndentAdjuster {
    protected final CodeStyleManager delegate;

    protected ExternalFormatterCodeStyleManagerAdapter(CodeStyleManager delegate) {
      this.delegate = delegate;
    }

    /** #api203: changed ranges argument generic type to {@code ? extends TextRange} in 2021.1 */
    @Override
    public void reformatText(PsiFile file, Collection<? extends TextRange> ranges)
        throws IncorrectOperationException {
        reformatTextAdapted(file, ranges);
    }

    protected abstract void reformatTextLegacy(PsiFile file, Collection<TextRange> ranges);
    protected abstract void reformatTextAdapted(PsiFile file, Collection<? extends TextRange> ranges);

    protected void reformatTextFromDelegate(PsiFile file, Collection<? extends TextRange> ranges) {
        delegate.reformatText(file, ranges);
    }

    /** #api203: changed ranges argument generic type to {@code ? extends TextRange} in 2021.1 */
    @Override
    public void reformatTextWithContext(PsiFile file, Collection<? extends TextRange> ranges) {
        reformatTextWithContextAdapted(file, ranges);
    }
    public abstract void reformatTextWithContextLegacy(PsiFile file, Collection<TextRange> ranges);
    public abstract void reformatTextWithContextAdapted(PsiFile file, Collection<? extends TextRange> ranges);

    public void reformatTextWithContextFromDelegate(PsiFile file, Collection<? extends TextRange> ranges) {
        delegate.reformatTextWithContext(file, ranges);
    }
}
