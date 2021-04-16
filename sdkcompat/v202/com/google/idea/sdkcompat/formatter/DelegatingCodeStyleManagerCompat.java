package com.google.idea.sdkcompat.formatter;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.arrangement.*;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;

/**
 * Compat for {@code DelegatingCodeStyleManager}. {@link CodeStyleManager} got a new method in
 * 2020.2. #api201
 */
public class DelegatingCodeStyleManagerCompat {

  private DelegatingCodeStyleManagerCompat() {}

  // #api201: Method introduced in 2020.2. If not overridden, an exception is thrown upon class
  // creation.
  public static void scheduleReformatWhenSettingsComputed(CodeStyleManager delegate, PsiFile file) {
    delegate.scheduleReformatWhenSettingsComputed(file);
  }

  public static class Entry extends DefaultArrangementEntry implements NameAwareArrangementEntry {
    private final String name;

    public Entry(@Nullable ArrangementEntry parent, String name, TextRange range, boolean canBeMatched) {
      super(parent, range.getStartOffset(), range.getEndOffset(), canBeMatched);
      this.name = name;
    }

    @Override
    public String getName() {
      return name;
    }
  }

  /** #api203: wildcard generics added in 2021.1. */
  public interface ProjectViewRearrangerAdapter extends Rearranger<Entry> {
    @Nullable
    @Override
    default Pair<DelegatingCodeStyleManagerCompat.Entry, List<DelegatingCodeStyleManagerCompat.Entry>> parseWithNew(
        PsiElement root,
        @Nullable Document document,
        Collection<TextRange> ranges,
        PsiElement element,
        ArrangementSettings settings) {
      // no support for generating new elements
      return doParseWithNew(root, document, ranges, element, settings);
    }

    Pair<DelegatingCodeStyleManagerCompat.Entry, List<DelegatingCodeStyleManagerCompat.Entry>> doParseWithNew(PsiElement root,
                        Document document,
                        Collection<? extends TextRange> ranges,
                        PsiElement element,
                        ArrangementSettings settings);

    @Override
    default List<DelegatingCodeStyleManagerCompat.Entry> parse(
          PsiElement root,
          @Nullable Document document,
          Collection<TextRange> ranges,
          ArrangementSettings settings) {
      return doParse(root, document, ranges, settings);
    }

    List<DelegatingCodeStyleManagerCompat.Entry> doParse(PsiElement root,
                                                         Document document,
                                                         Collection<? extends TextRange> ranges,
                                                         ArrangementSettings settings);
  }
}
