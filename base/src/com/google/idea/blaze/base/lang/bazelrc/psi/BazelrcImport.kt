package com.google.idea.blaze.base.lang.bazelrc.psi

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.google.idea.blaze.base.lang.bazelrc.elements.BazelrcTokenTypes
import com.google.idea.blaze.base.lang.bazelrc.references.BazelrcImportFileReferenceSet

class BazelrcImport(node: ASTNode) : BazelrcBaseElement(node) {
  override fun acceptVisitor(visitor: BazelrcElementVisitor) = visitor.visitImport(this)

  override fun getReference(): PsiReference? = references.last()

  override fun getReferences(): Array<out PsiReference?> = BazelrcImportFileReferenceSet(this).allReferences

  fun getImportPath(): PsiElement? = findChildByType<PsiElement>(BazelrcTokenTypes.VALUE)

  fun isOptional(): Boolean = findChildByType<PsiElement>(BazelrcTokenTypes.IMPORT)?.text?.startsWith("try-") == true
}
