package com.google.idea.blaze.base.lang.bazelrc.parser

import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import com.google.idea.blaze.base.lang.bazelrc.BazelrcLanguage
import com.google.idea.blaze.base.lang.bazelrc.elements.BazelrcElementTypes
import com.google.idea.blaze.base.lang.bazelrc.elements.BazelrcTokenSets
import com.google.idea.blaze.base.lang.bazelrc.lexer.BazelrcLexer
import com.google.idea.blaze.base.lang.bazelrc.psi.BazelrcFile

class BazelrcParserDefinition : ParserDefinition {
  private val file = IFileElementType(BazelrcLanguage)

  override fun createLexer(project: Project?): Lexer = BazelrcLexer()

  override fun createParser(project: Project?): PsiParser =
    PsiParser { root, builder ->
      Parsing(root, builder).parseFile()
    }

  override fun getFileNodeType(): IFileElementType = file

  override fun getWhitespaceTokens(): TokenSet = BazelrcTokenSets.WHITE_SPACES

  override fun getCommentTokens(): TokenSet = BazelrcTokenSets.COMMENTS

  override fun getStringLiteralElements(): TokenSet = TokenSet.EMPTY

  override fun createElement(node: ASTNode): PsiElement = BazelrcElementTypes.createElement(node)

  override fun createFile(viewProvider: FileViewProvider): PsiFile = BazelrcFile(viewProvider)
}
