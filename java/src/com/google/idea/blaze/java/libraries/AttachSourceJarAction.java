/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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

import com.google.idea.blaze.base.actions.BlazeProjectAction;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.libraries.LibraryEditor;
import com.google.idea.blaze.java.sync.model.BlazeJarLibrary;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTable;

class AttachSourceJarAction extends BlazeProjectAction {

  @Override
  protected void updateForBlazeProject(Project project, AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (projectData == null) {
      hideAction(presentation);
      return;
    }
    BlazeJarLibrary library = LibraryActionHelper.findBlazeLibraryForAction(project, e);
    if (library == null || library.libraryArtifact.getSourceJarsOrClassJar().isEmpty()) {
      hideAction(presentation);
      return;
    }
    presentation.setEnabledAndVisible(true);
    boolean attached =
        AttachedSourceJarManager.getInstance(project).hasSourceJarAttached(library.key);
    presentation.setText(attached ? "Detach Source Jar" : "Attach Source Jar");
  }

  private static void hideAction(Presentation presentation) {
    presentation.setEnabledAndVisible(false);
  }

  @Override
  protected void actionPerformedInBlazeProject(Project project, AnActionEvent e) {
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (projectData == null) {
      return;
    }
    BlazeJarLibrary library = LibraryActionHelper.findBlazeLibraryForAction(project, e);
    if (library == null || library.libraryArtifact.getSourceJarsOrClassJar().isEmpty()) {
      return;
    }
    AttachedSourceJarManager sourceJarManager = AttachedSourceJarManager.getInstance(project);
    boolean attachSourceJar = !sourceJarManager.hasSourceJarAttached(library.key);
    sourceJarManager.setHasSourceJarAttached(library.key, attachSourceJar);

    ApplicationManager.getApplication()
        .runWriteAction(
            () -> {
              LibraryTable libraryTable = ProjectLibraryTable.getInstance(project);
              LibraryTable.ModifiableModel libraryTableModel = libraryTable.getModifiableModel();
              LibraryEditor.updateLibrary(
                  project,
                  projectData.getArtifactLocationDecoder(),
                  libraryTable,
                  libraryTableModel,
                  library);
              libraryTableModel.commit();
            });
  }
}
