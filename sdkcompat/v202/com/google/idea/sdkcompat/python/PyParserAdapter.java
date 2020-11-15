package com.google.idea.sdkcompat.python;

import com.intellij.lang.SyntaxTreeBuilder;
import com.jetbrains.python.parsing.ParsingContext;
import com.jetbrains.python.parsing.PyParser;
import com.jetbrains.python.parsing.StatementParsing;
import com.jetbrains.python.psi.LanguageLevel;

/** Compatibility adapter for {@link PyParser}. #api201 */
public abstract class PyParserAdapter extends PyParser {

  /** #api201: Super method uses new interface SyntaxTreeBuilder in 2020.2 */
  @Override
  protected ParsingContext createParsingContext(
      SyntaxTreeBuilder builder, LanguageLevel languageLevel, StatementParsing.FUTURE futureFlag) {
    return createParsingContext(SyntaxTreeBuilderWrapper.wrap(builder), languageLevel, futureFlag);
  }

  protected abstract ParsingContext createParsingContext(
      SyntaxTreeBuilderWrapper builder,
      LanguageLevel languageLevel,
      StatementParsing.FUTURE futureFlag);
}
