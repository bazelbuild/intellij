package com.google.idea.sdkcompat.python;

import com.intellij.lang.SyntaxTreeBuilder;
import com.jetbrains.python.parsing.ParsingContext;
import com.jetbrains.python.parsing.PyParser;
import com.jetbrains.python.parsing.StatementParsing.FUTURE;
import com.jetbrains.python.psi.LanguageLevel;

/** Compatibility adapter for {@link PyParser}. #api202 */
public abstract class PyParserAdapter extends PyParser {

  /** #api202: Super method does not require futureFlag anymore in 2020.3 */
  @Override
  protected ParsingContext createParsingContext(
      SyntaxTreeBuilder builder, LanguageLevel languageLevel, FUTURE futureFlag) {
    return createParsingContext(builder, languageLevel);
  }

  protected abstract ParsingContext createParsingContext(
      SyntaxTreeBuilder builder, LanguageLevel languageLevel);
}
