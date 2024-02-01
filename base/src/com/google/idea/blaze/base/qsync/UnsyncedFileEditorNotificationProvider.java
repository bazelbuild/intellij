/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.qsync;

import com.google.idea.blaze.base.qsync.settings.QuerySyncSettings;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.google.idea.blaze.base.sync.actions.IncrementalSyncProjectAction;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotificationProvider;
import com.intellij.ui.EditorNotifications;
import java.nio.file.Path;
import java.util.function.Function;
import javax.annotation.Nullable;
import javax.swing.JComponent;

/**
 * A class which provides an Editor notification for a newly added file that needs to be synced
 * (Query sync specific).
 */
public class UnsyncedFileEditorNotificationProvider implements EditorNotificationProvider {

  private static final BoolExperiment ENABLED =
      new BoolExperiment("qsync.new.file.editor.notification", true);

  @Override
  @Nullable
  public Function<? super FileEditor, ? extends JComponent> collectNotificationData(
      Project project, VirtualFile virtualFile) {
    if (!Blaze.getProjectType(project).equals(ProjectType.QUERY_SYNC)) {
      return null;
    }
    if (!ENABLED.getValue()) {
      return null;
    }

    if (QuerySyncManager.getInstance(project).operationInProgress()) {
      return null;
    }
    Path path = virtualFile.toNioPath();
    if (!QuerySyncManager.getInstance(project).isProjectFileAddedSinceSync(path).orElse(false)) {
      return null;
    }

    return editor -> getEditorNotificationPanel(project);
  }

  private static EditorNotificationPanel getEditorNotificationPanel(Project project) {
    EditorNotificationPanel panel = new EditorNotificationPanel();
    panel.setText("This file is not synced");
    panel
        .createActionLabel("Sync now", IncrementalSyncProjectAction.ID)
        .addHyperlinkListener(
            event -> EditorNotifications.getInstance(project).updateAllNotifications());
    panel.createActionLabel(
        "Enable automatic syncing",
        () -> {
          QuerySyncSettings.getInstance().enableSyncOnFileChanges(true);
          IncrementalSyncProjectAction.doIncrementalSync(
              UnsyncedFileEditorNotificationProvider.class, project, null);
          EditorNotifications.getInstance(project).updateAllNotifications();
        });
    return panel;
  }
}
