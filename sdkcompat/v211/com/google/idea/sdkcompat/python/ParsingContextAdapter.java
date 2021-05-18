package com.google.idea.sdkcompat.python;

import com.intellij.lang.SyntaxTreeBuilder;
import com.jetbrains.python.parsing.ParsingContext;
import com.jetbrains.python.psi.LanguageLevel;

/** Compatibility adapter for {@link ParsingContext}. #api202 */
public class ParsingContextAdapter extends ParsingContext {
  /** #api202: Constructor does NOT accept the additional futureFlag parameter anymore in 2020.3 */
  public ParsingContextAdapter(SyntaxTreeBuilder builder, LanguageLevel languageLevel) {
    super(builder, languageLevel);
  }
}
