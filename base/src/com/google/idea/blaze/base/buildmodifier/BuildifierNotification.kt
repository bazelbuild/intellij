/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.buildmodifier

import com.google.idea.blaze.base.settings.BlazeUserSettings
import com.google.idea.blaze.base.util.pluginApplicationScope
import com.intellij.ide.BrowserUtil
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.*
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.progress.currentThreadCoroutineScope
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.platform.ide.progress.withBackgroundProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object BuildifierNotification {
  private const val GITHUB_URL = "https://github.com/bazelbuild/buildtools/"

  private const val NOTIFICATION_GROUP_ID = "Buildifier"
  private const val NOTIFICATION_DOWNLOAD_TITLE = "Download Buildifier"
  private const val NOTIFICATION_DOWNLOAD_QUESTION = "Would you like to download the buildifier formatter?"
  private const val NOTIFICATION_DOWNLOAD_SUCCESS = "Buildifier download was successful."
  private const val NOTIFICATION_DOWNLOAD_FAILURE = "Buildifier download failed. Please install it manually."
  private const val NOTIFICATION_NOTFOUND_FAILURE = "Could not find buildifier binary. Please install it manually."

  private const val PROGRESS_TITLE = "Downloading Buildifier binary..."

  private val NOTIFICATION_DOWNLOAD_SHOWN_KEY = Key<Boolean>("buildifier.notification.download")
  private val NOTIFICATION_NOTFOUND_SHOWN_KEY = Key<Boolean>("buildifier.notification.notfound")

  private val NOTIFICATION_GROUP: NotificationGroup = NotificationGroupManager.getInstance().getNotificationGroup(
    NOTIFICATION_GROUP_ID
  )

  private val OPEN_GITHUB_ACTION = NotificationAction.createSimple("Open GitHub Page") {
    BrowserUtil.open(GITHUB_URL)
  }

  @JvmStatic
  fun showDownloadNotification(project: Project) {
    if (!shouldShowNotification(NOTIFICATION_DOWNLOAD_SHOWN_KEY)) {
      return
    }

    val notification = NOTIFICATION_GROUP.createNotification(
      NOTIFICATION_DOWNLOAD_TITLE,
      NOTIFICATION_DOWNLOAD_QUESTION,
      NotificationType.INFORMATION
    ).setSuggestionType(true)

    notification.addAction(
      object : NotificationAction("Download") {
        override fun actionPerformed(e: AnActionEvent, notification: Notification) {
          notification.expire()

          @Suppress("UnstableApiUsage")
          install(project, currentThreadCoroutineScope())
        }
      }
    )

    notification.addDismissAction(NOTIFICATION_DOWNLOAD_SHOWN_KEY)

    notification.notify(project)
  }

  @JvmStatic
  fun showNotFoundNotification() {
    if (!shouldShowNotification(NOTIFICATION_NOTFOUND_SHOWN_KEY)) {
      return
    }

    val notification = NOTIFICATION_GROUP.createNotification(
      NOTIFICATION_NOTFOUND_FAILURE,
      NotificationType.ERROR
    )

    notification.addAction(OPEN_GITHUB_ACTION)
    notification.addDismissAction(NOTIFICATION_NOTFOUND_SHOWN_KEY)

    Notifications.Bus.notify(notification)
  }

  private fun install(project: Project, scope: CoroutineScope) {
    scope.launch {
      val file = withContext(Dispatchers.IO) {
        withBackgroundProgress(project, PROGRESS_TITLE) {
          BuildifierDownloader.downloadSync()
        }
      }

      if (file == null) {
        showFailureNotification()
        return@launch
      }

      showSuccessNotification()

      withContext(Dispatchers.EDT) {
        BlazeUserSettings.getInstance().buildifierBinaryPath = file.absolutePath
      }
    }
  }

  private fun showFailureNotification() {
    val notification = NOTIFICATION_GROUP.createNotification(
      NOTIFICATION_DOWNLOAD_TITLE,
      NOTIFICATION_DOWNLOAD_FAILURE,
      NotificationType.ERROR
    )

    notification.addAction(OPEN_GITHUB_ACTION)

    Notifications.Bus.notify(notification)
  }

  private fun showSuccessNotification() {
    val notification = NOTIFICATION_GROUP.createNotification(
      NOTIFICATION_DOWNLOAD_TITLE,
      NOTIFICATION_DOWNLOAD_SUCCESS,
      NotificationType.INFORMATION
    )

    Notifications.Bus.notify(notification)
  }

  private fun shouldShowNotification(key: Key<Boolean>): Boolean {
    // dismiss is a persisted property
    if (PropertiesComponent.getInstance().getBoolean(key.toString(), false)) {
      return false
    }

    // show the notification only once per application start
    val application = ApplicationManager.getApplication()
    if (key[application, false]) {
      return false
    }

    application.putUserData(key, true)
    return true
  }

  private fun Notification.addDismissAction(key: Key<Boolean>) {
    this.addAction(NotificationAction.createSimple("Dismiss") {
      this.expire()
      PropertiesComponent.getInstance().setValue(key.toString(), true)
    })
  }
}
