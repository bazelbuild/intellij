/*
 * Copyright 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Modifications Copyright 2026 The Bazel Authors. All rights reserved.
 */
package com.google.idea.blaze.base.execlog.prototext;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

/**
 * Contains token types used by the prototext lexer.
 */
public final class ProtoTextTokenTypes {

  // Symbols
  public static final IElementType ASSIGN = new ProtoTextTokenType("=");
  public static final IElementType COLON = new ProtoTextTokenType(":");
  public static final IElementType COMMA = new ProtoTextTokenType(",");
  public static final IElementType DOT = new ProtoTextTokenType(".");
  public static final IElementType GT = new ProtoTextTokenType(">");
  public static final IElementType LBRACE = new ProtoTextTokenType("{");
  public static final IElementType LBRACK = new ProtoTextTokenType("[");
  public static final IElementType LPAREN = new ProtoTextTokenType("(");
  public static final IElementType LT = new ProtoTextTokenType("<");
  public static final IElementType MINUS = new ProtoTextTokenType("-");
  public static final IElementType RBRACE = new ProtoTextTokenType("}");
  public static final IElementType RBRACK = new ProtoTextTokenType("]");
  public static final IElementType RPAREN = new ProtoTextTokenType(")");
  public static final IElementType SEMI = new ProtoTextTokenType(";");
  public static final IElementType SLASH = new ProtoTextTokenType("/");

  // Literal types
  public static final IElementType FLOAT_LITERAL = new ProtoTextTokenType("float");
  public static final IElementType IDENTIFIER_LITERAL = new ProtoTextTokenType("identifier");
  public static final IElementType INTEGER_LITERAL = new ProtoTextTokenType("integer");
  public static final IElementType STRING_LITERAL = new ProtoTextTokenType("string");

  // Special types
  public static final IElementType IDENTIFIER_AFTER_NUMBER = new ProtoTextTokenType("IDENTIFIER_AFTER_NUMBER");
  public static final IElementType LINE_COMMENT = new ProtoTextTokenType("LINE_COMMENT");
  public static final IElementType SYMBOL = new ProtoTextTokenType("SYMBOL");

  // Boolean literals (highlighted as keywords)
  public static final IElementType FALSE = new ProtoTextTokenType("false");
  public static final IElementType TRUE = new ProtoTextTokenType("true");

  public static final TokenSet WHITE_SPACE = TokenSet.WHITE_SPACE;
  public static final TokenSet COMMENTS = TokenSet.create(LINE_COMMENT);
  public static final TokenSet STRINGS = TokenSet.create(STRING_LITERAL);
  public static final TokenSet NUMBERS = TokenSet.create(FLOAT_LITERAL, INTEGER_LITERAL);
}
