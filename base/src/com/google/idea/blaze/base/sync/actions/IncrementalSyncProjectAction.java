/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.sync.actions;

import com.google.idea.blaze.base.actions.BlazeProjectAction;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.google.idea.blaze.base.sync.BlazeSyncManager;
import com.google.idea.blaze.base.sync.status.BlazeSyncStatus;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.project.Project;
import icons.BlazeIcons;
import javax.swing.Icon;

/** Syncs the project with BUILD files. */
public class IncrementalSyncProjectAction extends BlazeProjectAction {

  @Override
  protected void actionPerformedInBlazeProject(Project project, AnActionEvent e) {
    BlazeSyncManager.getInstance(project).incrementalProjectSync();
    updateIcon(e);
  }

  @Override
  protected void updateForBlazeProject(Project project, AnActionEvent e) {
    updateIcon(e);
  }

  private static void updateIcon(AnActionEvent e) {
    Project project = e.getProject();
    Presentation presentation = e.getPresentation();
    BlazeSyncStatus statusHelper = BlazeSyncStatus.getInstance(project);
    BlazeSyncStatus.SyncStatus status = statusHelper.getStatus();
    presentation.setIcon(getIcon(status));
    presentation.setEnabled(!statusHelper.syncInProgress());

    if (status == BlazeSyncStatus.SyncStatus.DIRTY
        && !BlazeUserSettings.getInstance().getSyncStatusPopupShown()) {
      BlazeUserSettings.getInstance().setSyncStatusPopupShown(true);
      showPopupNotification(project);
    }
  }

  private static Icon getIcon(BlazeSyncStatus.SyncStatus status) {
    switch (status) {
      case FAILED:
        return BlazeIcons.BlazeFailed;
      default:
        return BlazeIcons.Blaze;
    }
  }

  private static final NotificationGroup NOTIFICATION_GROUP =
      new NotificationGroup("Changes since last blaze sync", NotificationDisplayType.BALLOON, true);

  private static void showPopupNotification(Project project) {
    String message =
        String.format(
            "Some relevant files (e.g. BUILD files, .blazeproject file) "
                + "have changed since the last sync. "
                + "Please press the 'Sync' button in the toolbar to re-sync your %s project.",
            ApplicationNamesInfo.getInstance().getFullProductName());
    Notification notification =
        new Notification(
            NOTIFICATION_GROUP.getDisplayId(),
            String.format("Changes since last %s sync", Blaze.buildSystemName(project)),
            message,
            NotificationType.INFORMATION);
    notification.setImportant(true);
    Notifications.Bus.notify(notification, project);
  }
}
