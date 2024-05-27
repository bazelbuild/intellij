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

package com.google.idea.blaze.clwb;

import com.google.idea.blaze.base.wizard2.BazelDisableImportNotification;
import com.google.idea.blaze.base.wizard2.BazelImportCurrentProjectAction;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotificationPanel.Status;
import com.intellij.ui.EditorNotificationProvider;
import com.jetbrains.cidr.lang.OCFileType;
import java.util.function.Function;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provide notification for C-family files temporarily until moving to new CLion project status
 * api.
 */
public class BazelCNotificationProvider implements EditorNotificationProvider {

  @Override
  public @Nullable Function<? super FileEditor, ? extends JComponent> collectNotificationData(
      @NotNull Project project, @NotNull VirtualFile file) {
    if (file.getFileType() != OCFileType.INSTANCE) {
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
      Runnable importAction = BazelImportCurrentProjectAction.createAction(panel, root);

      panel.setText("Project is not configured");
      panel.createActionLabel("Import Bazel project", importAction);
      panel.createActionLabel("Dismiss", "Bazel.DisableImportNotification");

      return panel;
    };
  }
}
