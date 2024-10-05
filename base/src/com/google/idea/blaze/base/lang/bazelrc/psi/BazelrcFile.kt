package com.google.idea.blaze.base.lang.bazelrc.psi

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider
import com.google.idea.blaze.base.lang.bazelrc.BazelrcFileType
import com.google.idea.blaze.base.lang.bazelrc.BazelrcLanguage

class BazelrcFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, BazelrcLanguage) {
  override fun getFileType(): FileType = BazelrcFileType

  val imports: Array<BazelrcImport>
    get() = this.findChildrenByClass(BazelrcImport::class.java)

  val lines: Array<BazelrcLine>
    get() = this.findChildrenByClass(BazelrcLine::class.java)
}
