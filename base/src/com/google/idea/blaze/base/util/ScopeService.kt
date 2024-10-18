package com.google.idea.blaze.base.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

@Service(Service.Level.PROJECT)
private class ProjectScopeService : Disposable {
  // #api223 use the injected scope
  val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  override fun dispose() {
    scope.cancel()
  }
}

/**
 * Gets a coroutine scope that is canceled when either the project is closed or the plugin is unloaded.
 */
fun pluginProjectScope(project: Project): CoroutineScope = project.service<ProjectScopeService>().scope
