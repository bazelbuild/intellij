package com.google.idea.blaze.base.lang.bazelrc.highlighting

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.tree.IElementType
import com.google.idea.blaze.base.lang.bazelrc.elements.BazelrcTokenTypes
import com.google.idea.blaze.base.lang.bazelrc.lexer.BazelrcLexer

object BazelrcSyntaxHighlighter : SyntaxHighlighterBase() {
  private val keys =
    mapOf(
      BazelrcTokenTypes.COMMENT to BazelrcHighlightingColors.COMMENT,
      BazelrcTokenTypes.IMPORT to BazelrcHighlightingColors.IMPORT,
      BazelrcTokenTypes.COMMAND to BazelrcHighlightingColors.COMMAND,
      BazelrcTokenTypes.CONFIG to BazelrcHighlightingColors.IDENTIFIER,
      BazelrcTokenTypes.FLAG to BazelrcHighlightingColors.STRING,
    )

  override fun getHighlightingLexer(): Lexer = BazelrcLexer()

  override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> = pack(keys[tokenType])
}

class BazelrcSyntaxHighlighterFactory : SyntaxHighlighterFactory() {
  override fun getSyntaxHighlighter(project: Project?, virtualFile: VirtualFile?): SyntaxHighlighter = BazelrcSyntaxHighlighter
}
