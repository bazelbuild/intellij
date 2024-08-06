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

package com.google.idea.blaze.base.wizard2;

import com.google.idea.blaze.base.lang.buildfile.language.BuildFileType;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotificationPanel.Status;
import com.intellij.ui.EditorNotificationProvider;
import java.util.function.Function;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BazelNotificationProvider implements EditorNotificationProvider, DumbAware {

  @Override
  public @Nullable Function<? super FileEditor, ? extends JComponent> collectNotificationData(
      @NotNull Project project, @NotNull VirtualFile file) {
    if (!isProjectAwareFile(file)) {
      return null;
    }
    if (!BazelImportCurrentProjectAction.projectCouldBeImported(project)) {
      return null;
    }
    if (BazelDisableImportNotification.isNotificationDisabled(project)) {
      return null;
    }

    String root = project.getBasePath();
    if (root == null) {
      return null;
    }

    return fileEditor -> {
      EditorNotificationPanel panel = new EditorNotificationPanel(fileEditor, Status.Warning);
      Runnable importAction = BazelImportCurrentProjectAction.createAction(root);

      panel.setText("Project is not configured");
      panel.createActionLabel("Import Bazel project", importAction);
      panel.createActionLabel("Dismiss", "Bazel.DisableImportNotification");

      return panel;
    };
  }

  protected boolean isProjectAwareFile(@NotNull VirtualFile file) {
    return file.getFileType() == BuildFileType.INSTANCE;
  }
}
