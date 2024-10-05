package com.google.idea.blaze.base.lang.bazelrc.elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.google.idea.blaze.base.lang.bazelrc.psi.BazelrcFlag
import com.google.idea.blaze.base.lang.bazelrc.psi.BazelrcImport
import com.google.idea.blaze.base.lang.bazelrc.psi.BazelrcLine

object BazelrcElementTypes {
  val LINE = BazelrcElementType("LINE")
  val FLAG = BazelrcElementType("FLAG")
  val IMPORT = BazelrcElementType("IMPORT")

  fun createElement(node: ASTNode): PsiElement =
    when (val type = node.elementType) {
      LINE -> BazelrcLine(node)
      FLAG -> BazelrcFlag(node)
      IMPORT -> BazelrcImport(node)

      else -> error("Unknown element type: $type")
    }
}
