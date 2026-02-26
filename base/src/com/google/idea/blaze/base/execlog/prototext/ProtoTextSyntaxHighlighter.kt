/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.execlog.prototext

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.tree.IElementType

class ProtoTextSyntaxHighlighter : SyntaxHighlighterBase() {

  override fun getHighlightingLexer(): Lexer = ProtoTextLexer()

  override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> {
    if (tokenType in ProtoTextTokenTypes.COMMENTS) return pack(DefaultLanguageHighlighterColors.LINE_COMMENT)
    if (tokenType in ProtoTextTokenTypes.STRINGS) return pack(DefaultLanguageHighlighterColors.STRING)
    if (tokenType in ProtoTextTokenTypes.NUMBERS) return pack(DefaultLanguageHighlighterColors.NUMBER)

    return when (tokenType) {
      ProtoTextTokenTypes.TRUE, ProtoTextTokenTypes.FALSE -> pack(DefaultLanguageHighlighterColors.KEYWORD)
      ProtoTextTokenTypes.IDENTIFIER_LITERAL -> pack(DefaultLanguageHighlighterColors.IDENTIFIER)
      ProtoTextTokenTypes.LBRACE, ProtoTextTokenTypes.RBRACE -> pack(DefaultLanguageHighlighterColors.BRACES)
      ProtoTextTokenTypes.LBRACK, ProtoTextTokenTypes.RBRACK -> pack(DefaultLanguageHighlighterColors.BRACKETS)
      ProtoTextTokenTypes.LPAREN, ProtoTextTokenTypes.RPAREN -> pack(DefaultLanguageHighlighterColors.PARENTHESES)
      ProtoTextTokenTypes.COLON, ProtoTextTokenTypes.ASSIGN -> pack(DefaultLanguageHighlighterColors.OPERATION_SIGN)
      ProtoTextTokenTypes.COMMA -> pack(DefaultLanguageHighlighterColors.COMMA)
      ProtoTextTokenTypes.SEMI -> pack(DefaultLanguageHighlighterColors.SEMICOLON)
      ProtoTextTokenTypes.DOT -> pack(DefaultLanguageHighlighterColors.DOT)

      else -> pack(null)
    }
  }
}
