package com.google.idea.blaze.java.sync;

import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.notification.NotificationsManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;

class JdkDetectionNotification {

  private static final NotificationGroup notificationGroup =
      new NotificationGroup("JDK Detection", NotificationDisplayType.BALLOON, true);

  static void show(Project project, BlazeContext context, @NotNull LanguageLevel languageLevel) {

    Notification[] notifications = NotificationsManager
        .getNotificationsManager()
        .getNotificationsOfType(Notification.class, project);

    for (Notification notification : notifications) {
      if (notification.getGroupId().equals(notificationGroup.getDisplayId())) {
        return;
      }
    }

    String title =
        String.format("Unable to find a JDK %1$s installed.", languageLevel.getPresentableText());

    String body =
        "After configuring a suitable JDK in the \"Project Structure\" dialog, sync the project again.";

    Notification notification =
        new Notification(notificationGroup.getDisplayId(), title, body, NotificationType.ERROR);

    notification.addAction(openProjectSettingsAction());
    Notifications.Bus.notify(notification, project);

    IssueOutput.error(title + "\n" + body).submit(context);
  }

  static private AnAction openProjectSettingsAction() {
    return new NotificationAction("Project Settings") {
      public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(e.getProject() != null);
      }

      public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
        if (e.getProject() == null) {
          return;
        }
        ProjectSettingsService.getInstance(e.getProject()).openProjectSettings();
      }
    };
  }
}
