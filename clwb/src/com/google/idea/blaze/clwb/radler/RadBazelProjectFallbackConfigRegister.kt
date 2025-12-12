/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.clwb.radler

import com.google.idea.blaze.base.util.pluginApplicationDisposable
import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.extensions.Extensions

private const val EXTENSION_POINT_NAME = "com.intellij.rider.cpp.core.projectModel.radProjectFallbackConfigDisabler"
private const val IMPLEMENTATION_CLASS = "com.google.idea.blaze.clwb.radler.RadBazelProjectFallbackConfigDisabler"

/**
 * This is required for backwards compatibility with CLion 2025.2.4 and earlier. Since the extension
 * point was introduced in 2025.2.5, it is not save to simply instantiate the implementation class.
 *
 * Switch to proper registration with removal of #api252.
 */
class RadBazelProjectFallbackConfigRegister : AppLifecycleListener {

  @Suppress("UnstableApiUsage")
  override fun appStarted() {
    val extensionPoint = Extensions.getRootArea().getExtensionPointIfRegistered<Any>(EXTENSION_POINT_NAME) ?: return

    // if the extension point is registered, we can safely instantiate the implementation
    val implementation = Class.forName(IMPLEMENTATION_CLASS).getConstructor().newInstance()

    extensionPoint.registerExtension(implementation, pluginApplicationDisposable())

  }
}