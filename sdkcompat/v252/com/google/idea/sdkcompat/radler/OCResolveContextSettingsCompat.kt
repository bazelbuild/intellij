package com.google.idea.sdkcompat.radler

import com.intellij.openapi.project.Project
import com.jetbrains.cidr.lang.settings.OCResolveContextSettings
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration

// #api252
object OCResolveContextSettingsCompat {
  fun findPriorityConfiguration(project: Project, configs: Collection<OCResolveConfiguration>): OCResolveConfiguration? {
    return OCResolveContextSettings.getInstance(project).findPriorityConfiguration(configs)?.first
  }

  fun setSelectedConfiguration(project: Project, config: OCResolveConfiguration) {
    OCResolveContextSettings.getInstance(project).setSelectedConfiguration(config)
  }
}
