package com.google.idea.sdkcompat.radler

import com.intellij.openapi.project.Project
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration
import com.jetbrains.cidr.lang.workspace.OCResolveConfigurationChooser

// #api253
abstract class OCResolveConfigurationChooserAdapter : OCResolveConfigurationChooser {
  override fun selectResolveConfiguration(
    project: Project,
    configurations: List<OCResolveConfiguration>,
  ): OCResolveConfiguration? {
    return doSelectResolveConfiguration(project, configurations)
  }

  abstract fun doSelectResolveConfiguration(
    project: Project,
    configurations: List<OCResolveConfiguration>,
  ): OCResolveConfiguration?
}
