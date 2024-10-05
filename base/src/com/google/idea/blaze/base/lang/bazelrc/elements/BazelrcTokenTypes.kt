package com.google.idea.blaze.base.lang.bazelrc.elements

object BazelrcTokenTypes {
  @JvmField
  val IMPORT = BazelrcTokenType("IMPORT")

  // Comment
  @JvmField
  val COMMENT = BazelrcTokenType("COMMENT")

  @JvmField
  val DOUBLE_QUOTE = BazelrcTokenType("\"")

  @JvmField
  val SINGLE_QUOTE = BazelrcTokenType("'")

  @JvmField
  val COMMAND = BazelrcTokenType("COMMAND")

  @JvmField
  val COLON = BazelrcTokenType(":")

  @JvmField
  val CONFIG = BazelrcTokenType("CONFIG")

  @JvmField
  val FLAG = BazelrcTokenType("FLAG")

  @JvmField
  val EQ = BazelrcTokenType("=")

  @JvmField
  val VALUE = BazelrcTokenType("VALUE")
}
