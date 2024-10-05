package com.google.idea.blaze.base.lang.bazelrc.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElementVisitor

abstract class BazelrcBaseElement(node: ASTNode) :
  ASTWrapperPsiElement(node),
  BazelrcElement {
  override fun accept(visitor: PsiElementVisitor) {
    if (visitor is BazelrcElementVisitor) {
      acceptVisitor(visitor)
    } else {
      super.accept(visitor)
    }
  }

  protected abstract fun acceptVisitor(visitor: BazelrcElementVisitor)
}
