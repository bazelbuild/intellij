package com.google.idea.blaze.base.lang.bazelrc.lexer;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.google.idea.blaze.base.lang.bazelrc.elements.BazelrcTokenTypes;

@SuppressWarnings("UnnecessaryUnicodeEscape")
%%
%class _BazelrcLexer
%implements FlexLexer
%no_suppress_warnings
%unicode
%function advance
%type IElementType

NL=[\r\n]
SPACE=[\ \t]
COMMENT=#({SOFT_NL} | [^\r\n])*

SOFT_NL=\\\r?\n
SQ=[']
DQ=[\"]

COLON=[:]

COMMAND={SOFT_NL} | [^{COLON}{SQ}{DQ}{SPACE}{NL}]

// lexing states:
%xstate IMPORT

%xstate CMD, CONFIG
%xstate CMD_DQ, CONFIG_DQ
%xstate CMD_SQ, CONFIG_SQ

%xstate FLAGS
%xstate VALUE
%xstate FLAG_DQ, VALUE_DQ
%xstate FLAG_SQ, VALUE_SQ

%%

<YYINITIAL> {
    ({SOFT_NL} | [{SPACE}{NL}])+                { return TokenType.WHITE_SPACE; }
    {COMMENT}+                                  { return BazelrcTokenTypes.COMMENT; }

    "import" | "try-import"                     { yybegin(IMPORT); return BazelrcTokenTypes.IMPORT; }

    [^]                                         { yybegin(CMD); yypushback(1); }
}

<IMPORT> {
    ({SOFT_NL} | [{SPACE}])+                        { return TokenType.WHITE_SPACE; }

    {SOFT_NL} | [^{SQ}{DQ}{SPACE}{NL}]+             { return BazelrcTokenTypes.VALUE; }

    {SQ} ({SOFT_NL} | \\{SQ} | [^{SQ}{NL}])* {SQ}?  { return BazelrcTokenTypes.VALUE; }
    {DQ} ({SOFT_NL} | \\{DQ} | [^{DQ}{NL}])* {DQ}?  { return BazelrcTokenTypes.VALUE; }

    [^]                                             { yybegin(YYINITIAL); yypushback(1); }
}

<CMD> {
    {SQ}                                        { yybegin(CMD_SQ); return BazelrcTokenTypes.SINGLE_QUOTE; }
    {DQ}                                        { yybegin(CMD_DQ); return BazelrcTokenTypes.DOUBLE_QUOTE; }
    {COLON}                                     { yybegin(CONFIG); return BazelrcTokenTypes.COLON; }

    {COMMAND}+                                  { return BazelrcTokenTypes.COMMAND; }

    {SPACE}+                                    { yybegin(FLAGS); return TokenType.WHITE_SPACE; }

    [^]                                         { yybegin(FLAGS); yypushback(1); }
}

<CONFIG>  {
    {COLON}                                     { yybegin(CONFIG); return BazelrcTokenTypes.COLON; }

    {SQ}                                        { yybegin(CONFIG_SQ); return BazelrcTokenTypes.SINGLE_QUOTE; }
    {DQ}                                        { yybegin(CONFIG_DQ); return BazelrcTokenTypes.DOUBLE_QUOTE; }

    ({SOFT_NL} | [^{SQ}{DQ}{SPACE}{NL}] )+      { yybegin(FLAGS); return BazelrcTokenTypes.CONFIG; }

    [^]                                         { yybegin(FLAGS); yypushback(1); }
}

<CMD_DQ> {
    (\\{DQ} | [{SPACE}{SQ}] | {COMMAND})+      { return BazelrcTokenTypes.COMMAND; }

    {COLON}                                    { yybegin(CONFIG_DQ); return BazelrcTokenTypes.COLON;}

    {DQ}                                       { yybegin(FLAGS); return BazelrcTokenTypes.DOUBLE_QUOTE; }
}

<CONFIG_DQ> {
    (\\{DQ} | [{SPACE}{SQ}] | {COMMAND})+      { return BazelrcTokenTypes.CONFIG; }

    {DQ}                                       { yybegin(FLAGS); return BazelrcTokenTypes.DOUBLE_QUOTE; }
}

<CONFIG_SQ> {
    (\\{SQ} | [{SPACE}{DQ}] | {COMMAND})+       { return BazelrcTokenTypes.CONFIG; }

    {SQ}                                        { yybegin(FLAGS); return BazelrcTokenTypes.SINGLE_QUOTE; }
}

<CMD_SQ> {
    (\\{SQ} | [{SPACE}{DQ}] | {COMMAND})+      { return BazelrcTokenTypes.COMMAND; }
    {COLON}                                    { yybegin(CONFIG_SQ); return BazelrcTokenTypes.COLON;}

    {SQ}                                       { yybegin(FLAGS); return BazelrcTokenTypes.SINGLE_QUOTE; }
}

<CMD, CONFIG, CMD_SQ, CONFIG_SQ, CMD_DQ, CONFIG_DQ> {
    {NL}+[{SPACE}{NL}]*                         { yybegin(YYINITIAL); return TokenType.WHITE_SPACE; }
    [^]                                         { yybegin(FLAGS); yypushback(1); }
}

<FLAGS> {
    "-" ({SOFT_NL} | [^={SQ}{DQ}{SPACE}{NL}])+  { return BazelrcTokenTypes.FLAG; }
    "="                                         { yybegin(VALUE); return BazelrcTokenTypes.EQ; }

    {DQ}                                        { yybegin(FLAG_DQ); return BazelrcTokenTypes.DOUBLE_QUOTE; }
    {SQ}                                        { yybegin(FLAG_SQ); return BazelrcTokenTypes.SINGLE_QUOTE; }

    ({SOFT_NL} | {SPACE})+                      { return TokenType.WHITE_SPACE; }

    {COMMENT}+                                  { yybegin(YYINITIAL); return BazelrcTokenTypes.COMMENT; }
    {SPACE}* {NL}+ [{SPACE} {NL}]*              { yybegin(YYINITIAL); return TokenType.WHITE_SPACE; }

    [^]                                         { yybegin(VALUE); yypushback(1); }
}

<VALUE> {
    {DQ}                                        { yybegin(VALUE_DQ); return BazelrcTokenTypes.DOUBLE_QUOTE; }
    {SQ}                                        { yybegin(VALUE_SQ); return BazelrcTokenTypes.SINGLE_QUOTE; }

    ({SOFT_NL} | [^{DQ}{SQ}{SPACE}{NL}])+        { return BazelrcTokenTypes.VALUE; }
    ({SOFT_NL} | {SPACE})+                      { yybegin(FLAGS); return TokenType.WHITE_SPACE; }

    {COMMENT}+                                  { yybegin(YYINITIAL); return BazelrcTokenTypes.COMMENT; }
    {SPACE}* {NL}+ [{SPACE} {NL}]*              { yybegin(YYINITIAL); return TokenType.WHITE_SPACE; }
}

<FLAG_DQ> {
    "-" ({SOFT_NL} | \\{DQ} | [^={DQ}{NL}])+    { return BazelrcTokenTypes.FLAG; }
    "="                                         { yybegin(VALUE_DQ); return BazelrcTokenTypes.EQ; }
    {DQ}                                        { yybegin(FLAGS); return BazelrcTokenTypes.DOUBLE_QUOTE; }

    [^]                                         { yybegin(FLAGS); yypushback(1); }
}

<VALUE_DQ> {
    ({SOFT_NL} | \\{DQ} | [^{DQ}{NL}])+         { return BazelrcTokenTypes.VALUE; }
    {DQ}                                        { yybegin(FLAGS); return BazelrcTokenTypes.DOUBLE_QUOTE; }

    [^]                                         { yybegin(FLAGS); yypushback(1); }
}

<FLAG_SQ> {
    "-" ({SOFT_NL} | \\{SQ} | [^={SQ}{NL}])+    { return BazelrcTokenTypes.FLAG; }
    "="                                         { yybegin(VALUE_SQ); return BazelrcTokenTypes.EQ; }
    {SQ}                                        { yybegin(FLAGS); return BazelrcTokenTypes.SINGLE_QUOTE; }

    [^]                                         { yybegin(FLAGS); yypushback(1); }
}

<VALUE_SQ> {
    ({SOFT_NL} | \\{SQ} | [^{SQ}{NL}])+         { return BazelrcTokenTypes.VALUE; }
    {SQ}                                        { yybegin(FLAGS); return BazelrcTokenTypes.SINGLE_QUOTE; }

    [^]                                         { yybegin(FLAGS); yypushback(1); }
}
// [^]                                         { return TokenType.BAD_CHARACTER; }