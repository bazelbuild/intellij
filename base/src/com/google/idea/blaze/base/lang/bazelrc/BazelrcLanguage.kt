package com.google.idea.blaze.base.lang.bazelrc

import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.LanguageFileType
import icons.BlazeIcons
import javax.swing.Icon

object BazelrcLanguage : Language("Bazelrc")

object BazelrcFileType : LanguageFileType(BazelrcLanguage) {
  override fun getName(): String = "Bazelrc"

  override fun getDescription(): String = "Bazelrc language"

  override fun getDefaultExtension(): String = "bazelrc"

  override fun getIcon(): Icon = BlazeIcons.Logo
}
