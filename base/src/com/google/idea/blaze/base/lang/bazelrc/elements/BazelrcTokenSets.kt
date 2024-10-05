package com.google.idea.blaze.base.lang.bazelrc.elements

import com.intellij.psi.TokenType
import com.intellij.psi.tree.TokenSet
import com.google.idea.blaze.base.lang.bazelrc.elements.BazelrcTokenTypes.COMMAND
import com.google.idea.blaze.base.lang.bazelrc.elements.BazelrcTokenTypes.COMMENT
import com.google.idea.blaze.base.lang.bazelrc.elements.BazelrcTokenTypes.DOUBLE_QUOTE
import com.google.idea.blaze.base.lang.bazelrc.elements.BazelrcTokenTypes.FLAG
import com.google.idea.blaze.base.lang.bazelrc.elements.BazelrcTokenTypes.SINGLE_QUOTE
import com.google.idea.blaze.base.lang.bazelrc.elements.BazelrcTokenTypes.VALUE

object BazelrcTokenSets {
  val WHITE_SPACES = TokenSet.create(TokenType.WHITE_SPACE)

  val COMMENTS = TokenSet.create(COMMENT)
  val COMMANDS = TokenSet.create(COMMAND)

  val QUOTES = TokenSet.create(DOUBLE_QUOTE, SINGLE_QUOTE)

  val BIBI = TokenSet.create(BazelrcTokenTypes.COMMAND, BazelrcTokenTypes.COMMENT, FLAG, VALUE)
}
