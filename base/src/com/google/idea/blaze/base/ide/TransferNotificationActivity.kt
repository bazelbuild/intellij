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
package com.google.idea.blaze.base.ide

import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.ide.util.RunOnceUtil
import com.intellij.notification.NotificationGroupManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.notification.NotificationType
import com.intellij.util.asSafely
import java.time.LocalDate

private const val NOTIFICATION_TITLE = "Plugin Ownership Transfer"
private const val NOTIFICATION_TEMPLATE = "The '%s' plugin is now maintained and released by JetBrains. The plugin is no longer offered by nor affiliated with Google."

// only show the notification for a fixed amount of time in the case we forget to remove this
private val LAST_DAY = LocalDate.of(2025, 10, 1)

class TransferNotificationActivity : ProjectActivity {

  private val identifier: String get() = javaClass.name

  override suspend fun execute(project: Project) {
    if (LocalDate.now().isAfter(LAST_DAY)) return

    RunOnceUtil.runOnceForApp(identifier) {
      showNotification(project)
    }
}

  private fun showNotification(project: Project) {
    if (project.isDisposed) return

    val pluginName = javaClass.classLoader.asSafely<PluginAwareClassLoader>()?.pluginDescriptor?.name ?: return
    val message = String.format(NOTIFICATION_TEMPLATE, pluginName)

    NotificationGroupManager.getInstance()
      .getNotificationGroup(identifier)
      .createNotification(NOTIFICATION_TITLE, message, NotificationType.INFORMATION)
      .notify(project)
  }
}