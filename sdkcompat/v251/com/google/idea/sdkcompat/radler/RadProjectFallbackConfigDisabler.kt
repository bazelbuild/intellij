package com.google.idea.sdkcompat.radler;

import com.intellij.openapi.project.Project

/**
 * This is a do nothing stub. #api251
 */
interface RadProjectFallbackConfigDisabler {

  suspend fun disableFallbackConfigForProject(project: Project): Boolean;
}
