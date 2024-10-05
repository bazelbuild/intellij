package com.google.idea.blaze.base.lang.bazelrc.commenter

import com.intellij.lang.Commenter

class BazelrcCommenter : Commenter {
  override fun getLineCommentPrefix(): String = "# "

  override fun getBlockCommentPrefix(): String? = null

  override fun getBlockCommentSuffix(): String? = null

  override fun getCommentedBlockCommentPrefix(): String? = null

  override fun getCommentedBlockCommentSuffix(): String? = null
}
