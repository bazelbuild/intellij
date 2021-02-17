package com.google.idea.sdkcompat.python;

import com.intellij.lang.PsiBuilder;
import com.jetbrains.python.parsing.ParsingContext;
import com.jetbrains.python.psi.LanguageLevel;

/** Compatibility adapter for {@link ParsingContext}. #api202 */
public class ParsingContextAdapter extends ParsingContext {
  /** #api202: Constructor does NOT accept the additional futureFlag parameter anymore in 2020.3 */
  public ParsingContextAdapter(PsiBuilder builder, LanguageLevel languageLevel) {
    // For reasons of having a simple implementation of compatibility with old sdks and reducing the
    // maintenance burden we chose to pass the StatementParsing.FUTURE as null for versions < 203.
    // we expect this to only impact python versions below 2.7.
    // Python 2.67, the last version below 2.7, is officially unsupported
    // and a potential security risk context: https://www.python.org/downloads/release/python-267/

    super(builder, languageLevel, /* futureFlag= */ null);
  }
}
