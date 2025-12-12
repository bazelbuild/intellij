package com.google.idea.blaze.base.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope

@Service(Service.Level.PROJECT)
private class ProjectScopeService(val scope: CoroutineScope) : Disposable.Default

/**
 * Gets a coroutine scope that is canceled when either the project is closed or the plugin is unloaded.
 */
fun pluginProjectScope(project: Project): CoroutineScope = project.service<ProjectScopeService>().scope

/**
 * Gets a disposable that is disposed when either the project is closed or the plugin is unloaded.
 */
fun pluginProjectDisposable(project: Project): Disposable = project.service<ProjectScopeService>()

@Service(Service.Level.APP)
private class ApplicationScopeService(val scope: CoroutineScope) : Disposable.Default

/**
 * Gets a coroutine scope that is canceled when either the application is closed or the plugin is unloaded.
 */
fun pluginApplicationScope(): CoroutineScope = ApplicationManager.getApplication().service<ApplicationScopeService>().scope

/**
 * Gets a disposable that is disposed when either the application is closed or the plugin is unloaded.
 */
fun pluginApplicationDisposable(): Disposable = ApplicationManager.getApplication().service<ApplicationScopeService>()