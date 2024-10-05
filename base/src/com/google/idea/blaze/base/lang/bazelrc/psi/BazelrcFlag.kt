package com.google.idea.blaze.base.lang.bazelrc.psi

import com.intellij.lang.ASTNode

class BazelrcFlag(node: ASTNode) : BazelrcBaseElement(node) {
  override fun acceptVisitor(visitor: BazelrcElementVisitor) = visitor.visitFlag(this)
}
