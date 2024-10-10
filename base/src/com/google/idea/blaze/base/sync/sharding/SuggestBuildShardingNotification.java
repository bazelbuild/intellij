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
package com.google.idea.blaze.base.sync.sharding;

import com.google.idea.blaze.base.projectview.ProjectViewEdit;
import com.google.idea.blaze.base.projectview.ProjectViewEdit.ProjectViewEditor;
import com.google.idea.blaze.base.projectview.section.ScalarSection;
import com.google.idea.blaze.base.projectview.section.sections.ShardBlazeBuildsSection;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.ui.OpenProjectViewAction;
import com.google.idea.blaze.base.sync.BlazeSyncManager;
import com.intellij.build.issue.BuildIssueQuickFix;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.Consumer;
import com.intellij.xml.util.XmlStringUtil;
import java.util.concurrent.CompletableFuture;
import javax.swing.event.HyperlinkEvent;
import org.jetbrains.annotations.NotNull;

/**
 * If blaze runs out of memory during sync, suggest that the user enables build sharding, or tweaks
 * the shard sizes if sharding is already enabled.
 */
public class SuggestBuildShardingNotification {

  /** Displays any sharding-related notifications (with quick fixes) appropriate to the OOME. */
  public static void syncOutOfMemoryError(Project project, BlazeContext context) {
    if (BlazeBuildTargetSharder.shardingRequested(project)) {
      suggestIncreasingServerMemory(project, context);
    } else {
      suggestSharding(project, context);
    }
  }

  private static void suggestIncreasingServerMemory(Project project, BlazeContext context) {
    final var title = "The Bazel server ran out of memory during sync";
    final var description = "You can work around this by allocating more memory to the Bazel server, "
        + "for example by adding this line to your .bazelrc file:\nstartup --host_jvm_args=-Xmx15g --host_jvm_args=-Xms15g";

    IssueOutput.error(title).withDescription(description).submit(context);
    showNotification(project, String.format("%s. %s", title, description), p -> {});
  }

  private static void suggestSharding(Project project, BlazeContext context) {
    final var title = "The Bazel server ran out of memory during sync";
    final var description = "This can occur for large projects. You can work around this by sharding the Bazel build "
        + "during sync or alternatively allocate more memory to Bazel.";

    final var fix = new BuildIssueQuickFix() {
      @NotNull
      @Override
      public String getId() {
        return "bazel.sync.enable.sharding";
      }

      @NotNull
      @Override
      public CompletableFuture<?> runQuickFix(@NotNull Project project, @NotNull DataContext dataContext) {
        enableShardingAndResync(project);
        return CompletableFuture.completedFuture(null);
      }
    };

    IssueOutput.error(title)
        .withDescription(description)
        .withFix("Enable sharding and retry sync", fix)
        .submit(context);

    final var message = String.format("%s. %s  Click <a href='fix'>here</a> to set up sync sharding.", title, description);
    showNotification(project, message, SuggestBuildShardingNotification::enableShardingAndResync);
  }

  private static void showNotification(
      Project project, String message, Consumer<Project> projectViewEditor) {
    Notification notification =
        new Notification(
            "Out of memory during sync",
            Blaze.buildSystemName(project) + " ran out of memory during sync",
            XmlStringUtil.wrapInHtml(message),
            NotificationType.ERROR,
            new NotificationListener.Adapter() {
              @Override
              protected void hyperlinkActivated(
                  Notification notification, HyperlinkEvent hyperlinkEvent) {
                notification.expire();
                projectViewEditor.consume(project);
              }
            });
    notification.setImportant(true);
    ApplicationManager.getApplication().invokeLater(() -> notification.notify(project));
  }

  private static void enableShardingAndResync(Project project) {
    editProjectViewAndResync(
        project,
        builder -> {
          ScalarSection<Boolean> existingSection = builder.getLast(ShardBlazeBuildsSection.KEY);
          builder.replace(
              existingSection, ScalarSection.builder(ShardBlazeBuildsSection.KEY).set(true));
          return true;
        });
  }

  private static void editProjectViewAndResync(Project project, ProjectViewEditor editor) {
    ProjectViewEdit edit = ProjectViewEdit.editLocalProjectView(project, editor);
    if (edit == null) {
      Messages.showErrorDialog(
          "Could not modify project view. Check for errors in your project view and try again",
          "Error");
      return;
    }
    edit.apply();
    OpenProjectViewAction.openLocalProjectViewFile(project);
    BlazeSyncManager.getInstance(project)
        .incrementalProjectSync(/* reason= */ "SuggestBuildShardingNotification");
  }
}
