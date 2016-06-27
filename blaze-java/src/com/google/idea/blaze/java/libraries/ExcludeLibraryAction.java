/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.java.libraries;

import com.google.idea.blaze.base.actions.BlazeAction;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.projectview.ProjectViewEdit;
import com.google.idea.blaze.base.projectview.section.Glob;
import com.google.idea.blaze.base.projectview.section.ListSection;
import com.google.idea.blaze.base.sync.BlazeSyncManager;
import com.google.idea.blaze.base.sync.BlazeSyncParams;
import com.google.idea.blaze.java.projectview.ExcludeLibrarySection;
import com.google.idea.blaze.java.sync.model.BlazeLibrary;
import com.intellij.CommonBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

public class ExcludeLibraryAction extends BlazeAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    assert project != null;
    Library library = LibraryActionHelper.findLibraryForAction(e);
    if (library != null) {
      BlazeLibrary blazeLibrary = LibraryActionHelper.findLibraryFromIntellijLibrary(project, library);
      if (blazeLibrary == null) {
        Messages.showErrorDialog(project, "Could not find this library in the project.", CommonBundle.getErrorTitle());
        return;
      }

      final LibraryArtifact libraryArtifact = blazeLibrary.getLibraryArtifact();
      if (libraryArtifact == null) {
        return;
      }

      final String path = libraryArtifact.jar.getRelativePath();

      ProjectViewEdit edit = ProjectViewEdit.editLocalProjectView(project, builder -> {
        builder.put(ListSection
          .update(ExcludeLibrarySection.KEY, builder.get(ExcludeLibrarySection.KEY))
          .add(new Glob(path))
        );
        return true;
      });
      edit.apply();

      BlazeSyncManager.getInstance(project).requestProjectSync(new BlazeSyncParams.Builder(
        "Sync",
        BlazeSyncParams.SyncMode.INCREMENTAL
      ).setDoBuild(false).build());
    }
  }

  @Override
  protected void doUpdate(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    boolean enabled = LibraryActionHelper.findLibraryForAction(e) != null;
    presentation.setVisible(enabled);
    presentation.setEnabled(enabled);
  }
}
