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

package com.google.idea.blaze.cpp;

import com.google.idea.blaze.base.wizard2.BazelImportCurrentProjectAction;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.jetbrains.cidr.cpp.cmake.workspace.CMakeNotificationProvider.AdditionalActionProvider;
import org.jetbrains.annotations.NotNull;

public class BazelCMakeNotificationProvider implements AdditionalActionProvider {

  @Override
  public void installAction(@NotNull EditorNotificationPanel panel,
      @NotNull VirtualFile virtualFile, @NotNull FileEditor fileEditor, @NotNull Project project) {
    if (!BazelImportCurrentProjectAction.projectCouldBeImported(project)) {
      return;
    }

    String root = project.getBasePath();
    if (root == null) {
      return;
    }

    Runnable importAction = BazelImportCurrentProjectAction.createAction(panel, root);
    panel.createActionLabel("Import Bazel project", importAction);
  }
}
