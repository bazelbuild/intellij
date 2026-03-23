/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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