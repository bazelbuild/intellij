package com.google.idea.blaze.base.lang.bazelrc.elements

import com.intellij.psi.tree.IElementType
import com.google.idea.blaze.base.lang.bazelrc.BazelrcLanguage

class BazelrcElementType(debugName: String) : IElementType(debugName, BazelrcLanguage)
