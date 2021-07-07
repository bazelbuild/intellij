/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.java.plugin;

import com.google.idea.blaze.base.plugin.PluginUtils;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.common.util.Transactions;
import com.intellij.application.Topics;
import com.intellij.ide.AppLifecycleListener;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.PlatformUtils;
import com.intellij.util.messages.MessageBusConnection;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;
import javax.annotation.Nullable;
import javax.swing.JComponent;
import javax.swing.event.HyperlinkEvent;

/**
 * Runs on startup, displaying an error if the JUnit plugin (required for the blaze plugin to
 * properly function) is not enabled.
 */
public class JUnitPluginDependencyWarning implements ApplicationComponent {

  private static final String JUNIT_PLUGIN_ID = "JUnit";

  @Override
  public void initComponent() {
    Topics.subscribe(
        AppLifecycleListener.TOPIC,
        /* disposable= */ null,
        new AppLifecycleListener() {
          @Override
          public void appStarting(@Nullable Project projectFromCommandLine) {
            if (PlatformUtils.isIntelliJ() && !PluginUtils.isPluginEnabled(JUNIT_PLUGIN_ID)) {
              notifyJUnitNotEnabled();
            }
          }
        });
  }

  /**
   * Pop up a notification asking user to enable the JUnit plugin, and also add an error item to the
   * event log.
   */
  private static void notifyJUnitNotEnabled() {
    String buildSystem = Blaze.defaultBuildSystemName();

    String message =
        String.format(
            "<html>The JUnit plugin is disabled, but it's required for the %s plugin to function."
                + "<br>Please <a href=\"fix\">enable the JUnit plugin</a> and restart the IDE",
            buildSystem);

    NotificationListener listener =
        new NotificationListener.Adapter() {
          @Override
          protected void hyperlinkActivated(Notification notification, HyperlinkEvent e) {
            if ("fix".equals(e.getDescription())) {
              PluginUtils.installOrEnablePlugin(JUNIT_PLUGIN_ID);
            }
          }
        };

    Notification notification =
        new Notification(
            buildSystem + " Plugin Error",
            buildSystem + " plugin dependencies are missing",
            message,
            NotificationType.ERROR,
            listener);
    notification.setImportant(true);

    Application app = ApplicationManager.getApplication();
    MessageBusConnection connection = app.getMessageBus().connect(app);
    connection.subscribe(
        AppLifecycleListener.TOPIC,
        new AppLifecycleListener() {
          @Override
          public void appStarting(@Nullable Project projectFromCommandLine) {
            // Adds an error item to the 'Event Log' tab.
            // Easy to ignore, but remains in event log until manually cleared.
            Transactions.submitTransactionAndWait(() -> Notifications.Bus.notify(notification));
          }

          // #api211 @Override is removed because this function is removed from
          // AppLifecycleListener in 2021.2. We will need to remove it when 2021.1 is no longer
          // supported.
          public void appFrameCreated(
              List<String> commandLineArgs, Ref<? super Boolean> willOpenProject) {}

          @Override
          public void appFrameCreated(List<String> commandLineArgs) {
            // Popup dialog in welcome screen.
            app.invokeLater(() -> showPopupNotification(message));
          }
        });
    if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
      connection.subscribe(
          ProjectManager.TOPIC,
          new ProjectManagerAdapter() {
            @Override
            public void projectOpened(Project project) {
              // Popup dialog on project open, for users bypassing the welcome screen.
              if (Blaze.isBlazeProject(project)) {
                app.invokeLater(() -> showPopupNotification(message));
              }
            }
          });
    }
  }

  private static void showPopupNotification(String message) {
    JComponent component = WindowManager.getInstance().findVisibleFrame().getRootPane();
    if (component == null) {
      return;
    }
    Rectangle rect = component.getVisibleRect();
    JBPopupFactory.getInstance()
        .createHtmlTextBalloonBuilder(
            message,
            MessageType.WARNING,
            new HyperlinkAdapter() {
              @Override
              protected void hyperlinkActivated(HyperlinkEvent e) {
                PluginUtils.installOrEnablePlugin(JUNIT_PLUGIN_ID);
              }
            })
        .setFadeoutTime(-1)
        .setHideOnLinkClick(true)
        .setHideOnFrameResize(false)
        .setHideOnClickOutside(false)
        .setHideOnKeyOutside(false)
        .setDisposable(ApplicationManager.getApplication())
        .createBalloon()
        .show(
            new RelativePoint(component, new Point(rect.x + 30, rect.y + rect.height - 10)),
            Balloon.Position.above);
  }
}
