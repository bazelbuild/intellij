package com.google.idea.blaze.base.lang.bazelrc.matching

import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler
import com.google.idea.blaze.base.lang.bazelrc.elements.BazelrcTokenSets

class BazelrcQuoteHandler : SimpleTokenSetQuoteHandler(BazelrcTokenSets.BIBI)
