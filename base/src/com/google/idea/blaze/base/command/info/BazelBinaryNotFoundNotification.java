package com.google.idea.blaze.base.command.info;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.ShowSettingsUtil;
import org.jetbrains.annotations.NotNull;

class BazelBinaryNotFoundNotification {
  private static final NotificationGroup notificationGroup =
      new NotificationGroup("Bazel binary not found", NotificationDisplayType.BALLOON, true);

  static void show(String binaryPath) {
    Notification notification = new Notification(
        notificationGroup.getDisplayId(),
        "Bazel binary not found",
        "Cannot execute command '" + binaryPath
            + "'. Please configure correct bazel binary location and try again.",
        NotificationType.ERROR
    );

    notification.addAction(new NotificationAction("Bazel Settings") {
      public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(e.getProject() != null);
      }

      public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
        if (e.getProject() == null) {
          return;
        }
        ShowSettingsUtil.getInstance().showSettingsDialog(e.getProject(), "Bazel Settings");
      }
    });
    Notifications.Bus.notify(notification);
  }
}
