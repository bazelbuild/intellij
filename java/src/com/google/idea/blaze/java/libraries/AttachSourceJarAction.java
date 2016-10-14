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
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.java.sync.model.BlazeJarLibrary;
import com.google.idea.blaze.java.sync.projectstructure.LibraryEditor;
import com.intellij.CommonBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

class AttachSourceJarAction extends BlazeAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    assert project != null;
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (blazeProjectData == null) {
      return;
    }
    Library library = LibraryActionHelper.findLibraryForAction(e);
    if (library != null) {
      BlazeJarLibrary blazeLibrary =
          LibraryActionHelper.findLibraryFromIntellijLibrary(project, blazeProjectData, library);
      if (blazeLibrary == null) {
        Messages.showErrorDialog(
            project, "Could not find this library in the project.", CommonBundle.getErrorTitle());
        return;
      }

      final LibraryArtifact libraryArtifact = blazeLibrary.libraryArtifact;
      if (libraryArtifact.sourceJar == null) {
        return;
      }
      SourceJarManager sourceJarManager = SourceJarManager.getInstance(project);
      boolean attachSourceJar = !sourceJarManager.hasSourceJarAttached(blazeLibrary.key);
      sourceJarManager.setHasSourceJarAttached(blazeLibrary.key, attachSourceJar);

      ApplicationManager.getApplication()
          .runWriteAction(
              () -> {
                LibraryTable libraryTable = ProjectLibraryTable.getInstance(project);
                LibraryTable.ModifiableModel libraryTableModel = libraryTable.getModifiableModel();
                LibraryEditor.updateLibrary(
                    project,
                    blazeProjectData.artifactLocationDecoder,
                    libraryTable,
                    libraryTableModel,
                    blazeLibrary);
                libraryTableModel.commit();
              });
    }
  }

  @Override
  protected void doUpdate(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    String text = "Attach Source Jar";
    boolean visible = false;
    boolean enabled = false;
    Project project = e.getProject();
    if (project != null) {
      BlazeProjectData blazeProjectData =
          BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
      if (blazeProjectData != null) {
        Library library = LibraryActionHelper.findLibraryForAction(e);
        if (library != null) {
          visible = true;

          BlazeJarLibrary blazeLibrary =
              LibraryActionHelper.findLibraryFromIntellijLibrary(
                  e.getProject(), blazeProjectData, library);
          if (blazeLibrary != null && blazeLibrary.libraryArtifact.sourceJar != null) {
            enabled = true;
            if (SourceJarManager.getInstance(project).hasSourceJarAttached(blazeLibrary.key)) {
              text = "Detach Source Jar";
            }
          }
        }
      }
    }
    presentation.setVisible(visible);
    presentation.setEnabled(enabled);
    presentation.setText(text);
  }
}
