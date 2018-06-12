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

import com.google.common.collect.Lists;
import com.google.idea.blaze.base.actions.BlazeProjectAction;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.LibraryKey;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.sync.BlazeSyncParams.SyncMode;
import com.google.idea.blaze.base.sync.SyncListener;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.libraries.LibraryEditor;
import com.google.idea.blaze.java.sync.model.BlazeJarLibrary;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import java.util.List;

class DetachAllSourceJarsAction extends BlazeProjectAction {

  @Override
  protected void actionPerformedInBlazeProject(Project project, AnActionEvent e) {
    detachAll(project);
  }

  private static void detachAll(Project project) {
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (blazeProjectData == null) {
      return;
    }

    List<Library> librariesToDetach = Lists.newArrayList();
    AttachedSourceJarManager sourceJarManager = AttachedSourceJarManager.getInstance(project);
    for (Library library : ProjectLibraryTable.getInstance(project).getLibraries()) {
      if (library.getName() == null) {
        continue;
      }
      LibraryKey libraryKey = LibraryKey.fromIntelliJLibraryName(library.getName());
      if (sourceJarManager.hasSourceJarAttached(libraryKey)) {
        sourceJarManager.setHasSourceJarAttached(libraryKey, false);
        librariesToDetach.add(library);
      }
    }

    if (librariesToDetach.isEmpty()) {
      return;
    }
    ApplicationManager.getApplication()
        .runWriteAction(
            () -> {
              LibraryTable libraryTable = ProjectLibraryTable.getInstance(project);
              LibraryTable.ModifiableModel libraryTableModel = libraryTable.getModifiableModel();
              for (Library library : librariesToDetach) {
                BlazeJarLibrary blazeLibrary =
                    LibraryActionHelper.findLibraryFromIntellijLibrary(
                        project, blazeProjectData, library);
                if (blazeLibrary == null) {
                  continue;
                }
                LibraryEditor.updateLibrary(
                    project,
                    blazeProjectData.artifactLocationDecoder,
                    libraryTable,
                    libraryTableModel,
                    blazeLibrary);
              }
              libraryTableModel.commit();
            });
  }

  static class DetachAllOnSync extends SyncListener.Adapter {

    @Override
    public void onSyncComplete(
        Project project,
        BlazeContext context,
        BlazeImportSettings importSettings,
        ProjectViewSet projectViewSet,
        BlazeProjectData blazeProjectData,
        SyncMode syncMode,
        SyncResult syncResult) {
      if (syncMode == SyncMode.FULL) {
        detachAll(project);
      }

      super.onSyncComplete(
          project, context, importSettings, projectViewSet, blazeProjectData, syncMode, syncResult);
    }
  }
}
