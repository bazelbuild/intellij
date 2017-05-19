/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.sync.sharding;

import com.google.idea.blaze.base.projectview.ProjectViewEdit;
import com.google.idea.blaze.base.projectview.section.ScalarSection;
import com.google.idea.blaze.base.projectview.section.sections.ShardBlazeBuildsSection;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.google.idea.blaze.base.sync.BlazeSyncManager;
import com.google.idea.blaze.base.sync.BlazeSyncParams;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.pom.NavigatableAdapter;
import com.intellij.xml.util.XmlStringUtil;
import javax.swing.event.HyperlinkEvent;

/** If blaze runs out of memory during sync, suggest that the user enables build sharding. */
public class SuggestEnablingShardingNotification {

  public static void suggestSharding(Project project, BlazeContext context) {
    if (!BlazeBuildTargetSharder.canEnableSharding(project)) {
      return;
    }
    String buildSystem = Blaze.buildSystemName(project);
    String message =
        String.format(
            "The %1$s server ran out of memory during sync. This can occur for large projects. You "
                + "can workaround this by <a href='fix'>sharding the %1$s build during sync</a>, "
                + "or alternatively allocate more memory to %1$s",
            buildSystem);
    IssueOutput.error(message)
        .navigatable(
            new NavigatableAdapter() {
              @Override
              public void navigate(boolean requestFocus) {
                enableShardingAndResync(project);
              }
            })
        .submit(context);

    Notification notification =
        new Notification(
            "Out of memory during sync",
            buildSystem + " ran out of memory during sync",
            XmlStringUtil.wrapInHtml(message),
            NotificationType.ERROR,
            new NotificationListener.Adapter() {
              @Override
              protected void hyperlinkActivated(
                  Notification notification, HyperlinkEvent hyperlinkEvent) {
                notification.expire();
                enableShardingAndResync(project);
              }
            });
    notification.setImportant(true);
    ApplicationManager.getApplication().invokeLater(() -> notification.notify(project));
  }

  private static void enableShardingAndResync(Project project) {
    ProjectViewEdit edit =
        ProjectViewEdit.editLocalProjectView(
            project,
            builder -> {
              ScalarSection<Boolean> existingSection = builder.getLast(ShardBlazeBuildsSection.KEY);
              builder.replace(
                  existingSection, ScalarSection.builder(ShardBlazeBuildsSection.KEY).set(true));
              return true;
            });
    if (edit == null) {
      Messages.showErrorDialog(
          "Could not modify project view. Check for errors in your project view and try again",
          "Error");
      return;
    }
    edit.apply();
    BlazeSyncManager.getInstance(project)
        .requestProjectSync(
            new BlazeSyncParams.Builder("Sync", BlazeSyncParams.SyncMode.INCREMENTAL)
                .addProjectViewTargets(true)
                .addWorkingSet(BlazeUserSettings.getInstance().getExpandSyncToWorkingSet())
                .build());
  }
}
