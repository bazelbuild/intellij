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
package com.google.idea.blaze.base.buildmodifier;

import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

public final class BuildifierNotification {

  private static final String GITHUB_URL = "https://github.com/bazelbuild/buildtools/";

  private static final String NOTIFICATION_GROUP_ID = "Buildifier";
  private static final String NOTIFICATION_DOWNLOAD_TITLE = "Download Buildifier";
  private static final String NOTIFICATION_DOWNLOAD_QUESTION = "Would you like to download the buildifier formatter?";
  private static final String NOTIFICATION_DOWNLOAD_SUCCESS = "Buildifier download was successful.";
  private static final String NOTIFICATION_DOWNLOAD_FAILURE = "Buildifier download failed. Please install it manually.";
  private static final String NOTIFICATION_NOTFOUND_FAILURE = "Could not find buildifier binary. Please install it manually.";

  private static final Key<Boolean> NOTIFICATION_DOWNLOAD_SHOWN_KEY = new Key<>("buildifier.notification.download");
  private static final Key<Boolean> NOTIFICATION_NOTFOUND_SHOWN_KEY = new Key<>("buildifier.notification.notfound");

  private static final NotificationGroup NOTIFICATION_GROUP =
      NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP_ID);

  public static void showDownloadNotification() {
    if (!shouldShowNotification(NOTIFICATION_DOWNLOAD_SHOWN_KEY)) {
      return;
    }

    final var notification = NOTIFICATION_GROUP.createNotification(
        NOTIFICATION_DOWNLOAD_TITLE,
        NOTIFICATION_DOWNLOAD_QUESTION,
        NotificationType.INFORMATION
    ).setSuggestionType(true);

    notification.addAction(
        new NotificationAction("Download") {
          @Override
          public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
            notification.expire();
            install(e.getProject());
          }
        }
    );

    notification.addAction(
        NotificationAction.createSimple("Dismiss", () -> {
              notification.expire();
              dismissNotification(NOTIFICATION_DOWNLOAD_SHOWN_KEY);
            }
        )
    );

    Notifications.Bus.notify(notification);
  }

  public static void showNotFoundNotification() {
    if (!shouldShowNotification(NOTIFICATION_NOTFOUND_SHOWN_KEY)) {
      return;
    }

    final var notification = NOTIFICATION_GROUP.createNotification(
        NOTIFICATION_NOTFOUND_FAILURE,
        NotificationType.ERROR
    );

    notification.addAction(
        NotificationAction.createSimple("Open GitHub Page", () -> BrowserUtil.open(GITHUB_URL))
    );

    notification.addAction(
        NotificationAction.createSimple("Dismiss", () -> {
              notification.expire();
              dismissNotification(NOTIFICATION_NOTFOUND_SHOWN_KEY);
            }
        )
    );

    Notifications.Bus.notify(notification);
  }

  private static void install(Project project) {
    final var file = BuildifierDownloader.downloadWithProgress(project);

    if (file == null) {
      NOTIFICATION_GROUP.createNotification(
          NOTIFICATION_DOWNLOAD_TITLE,
          NOTIFICATION_DOWNLOAD_FAILURE,
          NotificationType.ERROR
      ).addAction(
          NotificationAction.createSimple("Open GitHub Page", () -> BrowserUtil.open(GITHUB_URL))
      ).notify(project);
    } else {
      NOTIFICATION_GROUP.createNotification(
          NOTIFICATION_DOWNLOAD_TITLE,
          NOTIFICATION_DOWNLOAD_SUCCESS,
          NotificationType.INFORMATION
      ).notify(project);

      BlazeUserSettings.getInstance().setBuildifierBinaryPath(file.getAbsolutePath());
    }
  }

  private static boolean shouldShowNotification(Key<Boolean> key) {
    // dismiss is a persisted property
    if (PropertiesComponent.getInstance().getBoolean(key.toString(), false)) {
      return false;
    }

    // show the notification only once per application start
    final var application = ApplicationManager.getApplication();
    if (key.get(application, false)) {
      return false;
    }

    application.putUserData(key, true);
    return true;
  }

  private static void dismissNotification(Key<Boolean> key) {
    PropertiesComponent.getInstance().setValue(key.toString(), true);
  }
}
