package com.google.idea.blaze.base.lang.bazelrc.elements

import com.intellij.psi.tree.IElementType
import com.google.idea.blaze.base.lang.bazelrc.BazelrcLanguage

class BazelrcTokenType(debugName: String) : IElementType(debugName, BazelrcLanguage) {
  override fun toString(): String = "Bazelrc:" + super.toString()
}
