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

import com.google.idea.blaze.base.logging.utils.querysync.QuerySyncActionStatsScope;
import com.google.idea.blaze.base.qsync.QuerySyncManager.TaskOrigin;
import com.google.idea.blaze.base.qsync.settings.QuerySyncSettings;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.google.idea.blaze.base.sync.status.BlazeSyncStatus;
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
public class NewFileEditorNotificationProvider implements EditorNotificationProvider {

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
    return editor -> maybeCreatePanel(project, editor);
  }

  @Nullable
  private static JComponent maybeCreatePanel(Project project, FileEditor editor) {

    QuerySyncProject querySyncProject =
        QuerySyncManager.getInstance(project).getLoadedProject().orElse(null);
    if (querySyncProject == null) {
      return null;
    }
    if (BlazeSyncStatus.getInstance(project).syncInProgress()) {
      return null;
    }
    VirtualFile virtualFile = editor.getFile();
    Path path = virtualFile.toNioPath();
    if (!querySyncProject.containsPath(path) || querySyncProject.explicitlyExcludesPath(path)) {
      return null;
    }

    if (querySyncProject.recognizesFile(path)) {
      return null;
    }

    return getEditorNotificationPanel(project, virtualFile);
  }

  private static EditorNotificationPanel getEditorNotificationPanel(
      Project project, VirtualFile virtualFile) {
    EditorNotificationPanel panel = new EditorNotificationPanel();
    panel.setText("This file is not synced");
    panel
        .createActionLabel("Sync now", "Blaze.IncrementalSyncProject")
        .addHyperlinkListener(
            event -> EditorNotifications.getInstance(project).updateNotifications(virtualFile));
    panel.createActionLabel(
        "Enable automatic syncing",
        () -> {
          QuerySyncSettings.getInstance().enableSyncOnFileChanges(true);
          QuerySyncActionStatsScope scope =
              QuerySyncActionStatsScope.createForFile(
                  NewFileEditorNotificationProvider.class, null, virtualFile);
          QuerySyncManager.getInstance(project).deltaSync(scope, TaskOrigin.USER_ACTION);
          EditorNotifications.getInstance(project).updateNotifications(virtualFile);
        });
    return panel;
  }
}
