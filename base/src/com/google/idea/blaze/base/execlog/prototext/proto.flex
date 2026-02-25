// Copyright 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
// Modifications Copyright 2026 The Bazel Authors. All rights reserved.

package com.google.idea.blaze.base.execlog.prototext;

import com.google.idea.blaze.base.execlog.prototext.ProtoTextTokenTypes;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;

@SuppressWarnings("fallthrough")
%%

%{
  public _ProtoLexer() {
    this(null);
  }
%}

%public
%class _ProtoLexer
%implements FlexLexer
%function advance
%type IElementType
%unicode
%state COMMENT, AFTER_NUMBER

// General classes
Alpha = [a-zA-Z_]
Digit = [0-9]
NonZeroDigit = [1-9]
HexDigit = [0-9a-fA-F]
OctDigit = [0-7]
Alphanumeric = {Alpha} | {Digit}

// Catch-all for symbols not handled elsewhere.
//
// From tokenizer.h:
//   Any other printable character, like '!' or '+'. Symbols are always a single character, so
//   "!+$%" is four tokens.
Symbol = [!#$%&()*+,-./:;<=>?@\[\\\]\^`{|}~]

// Whitespace.
WhitespaceNoNewline = [\ \t\r\f\x0b] // '\x0b' is '\v' (vertical tab) in C.
Whitespace = ({WhitespaceNoNewline} | "\n")+

// Comments.
ShLineComment = "#" [^\n]*

// Identifiers.
//
// From tokenizer.h:
//   A sequence of letters, digits, and underscores, not starting with a digit.  It is an error for
//   a number to be followed by an identifier with no space in between.
Identifier = {Alpha} {Alphanumeric}*

// Integers.
//
// From tokenizer.h:
//   A sequence of digits representing an integer.  Normally the digits are decimal, but a prefix of
//   "0x" indicates a hex number and a leading zero indicates octal, just like with C numeric
//   literals.  A leading negative sign is NOT included in the token; it's up to the parser to
//   interpret the unary minus operator on its own.
DecInteger = "0" | {NonZeroDigit} {Digit}*
OctInteger = "0" {OctDigit}+
HexInteger = "0" [xX] {HexDigit}+
Integer = {DecInteger} | {OctInteger} | {HexInteger}

// Floats.
//
// From tokenizer.h:
//   A floating point literal, with a fractional part and/or an exponent.  Always in decimal.
//   Again, never negative.
Float = ("." {Digit}+ {Exponent}? | {DecInteger} "." {Digit}* {Exponent}? | {DecInteger} {Exponent})
Exponent = [eE] [-+]? {Digit}+
FloatWithF = {Float} [fF]
IntegerWithF = {DecInteger} [fF]

// Strings.
//
// From tokenizer.h:
//   A quoted sequence of escaped characters.  Either single or double quotes can be used, but they
//   must match. A string literal cannot cross a line break.
SingleQuotedString = \' ([^\\\'\n] | \\[^\n])* (\' | \\)?
DoubleQuotedString = \" ([^\\\"\n] | \\[^\n])* (\" | \\)?
String = {SingleQuotedString} | {DoubleQuotedString}

%%

<YYINITIAL> {
  {Whitespace}              { return com.intellij.psi.TokenType.WHITE_SPACE; }

  "="                       { return ProtoTextTokenTypes.ASSIGN; }
  ":"                       { return ProtoTextTokenTypes.COLON; }
  ","                       { return ProtoTextTokenTypes.COMMA; }
  "."                       { return ProtoTextTokenTypes.DOT; }
  ">"                       { return ProtoTextTokenTypes.GT; }
  "{"                       { return ProtoTextTokenTypes.LBRACE; }
  "["                       { return ProtoTextTokenTypes.LBRACK; }
  "("                       { return ProtoTextTokenTypes.LPAREN; }
  "<"                       { return ProtoTextTokenTypes.LT; }
  "-"                       { return ProtoTextTokenTypes.MINUS; }
  "}"                       { return ProtoTextTokenTypes.RBRACE; }
  "]"                       { return ProtoTextTokenTypes.RBRACK; }
  ")"                       { return ProtoTextTokenTypes.RPAREN; }
  ";"                       { return ProtoTextTokenTypes.SEMI; }
  "/"                       { return ProtoTextTokenTypes.SLASH; }

  "true"                    { return ProtoTextTokenTypes.TRUE; }
  "false"                   { return ProtoTextTokenTypes.FALSE; }

  {Identifier}              { return ProtoTextTokenTypes.IDENTIFIER_LITERAL; }
  {String}                  { return ProtoTextTokenTypes.STRING_LITERAL; }
  {Integer}                 { yybegin(AFTER_NUMBER); return ProtoTextTokenTypes.INTEGER_LITERAL; }
  {Float}                   { yybegin(AFTER_NUMBER); return ProtoTextTokenTypes.FLOAT_LITERAL; }

  {IntegerWithF}            { yybegin(AFTER_NUMBER); return ProtoTextTokenTypes.FLOAT_LITERAL; }
  {FloatWithF}              { yybegin(AFTER_NUMBER); return ProtoTextTokenTypes.FLOAT_LITERAL; }

  // sh-style comments (#).
  "#"                       { yypushback(1); yybegin(COMMENT); }

  // Additional unmatched symbols are matched individually as SYMBOL.
  {Symbol}                  { return ProtoTextTokenTypes.SYMBOL; }

  // All other unmatched characters.
  [^]                       { return com.intellij.psi.TokenType.BAD_CHARACTER; }
}

<COMMENT> {
  {ShLineComment}           { yybegin(YYINITIAL); return ProtoTextTokenTypes.LINE_COMMENT; }
}

<AFTER_NUMBER> {
  // An identifier immediately following a number (with no whitespace) is an error. We return
  // the special IDENTIFIER_AFTER_NUMBER token type to signal this scenario.
  {Identifier} { yybegin(YYINITIAL); return ProtoTextTokenTypes.IDENTIFIER_AFTER_NUMBER; }

  // Any other token is valid. Push the token back and return to the initial state.
  [^] { yybegin(YYINITIAL); yypushback(yylength()); }
}
