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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Arrays.stream;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.idea.blaze.base.actions.BlazeProjectAction;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.LibraryKey;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.sync.SyncListener;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.SyncResult;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.libraries.LibraryEditor;
import com.google.idea.blaze.base.sync.libraries.LibraryFilesProviderFactory;
import com.google.idea.blaze.java.sync.model.BlazeJarLibrary;
import com.google.idea.blaze.java.sync.model.BlazeJavaSyncData;
import com.google.idea.common.util.Transactions;
import com.google.idea.sdkcompat.general.BaseSdkCompat;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.ui.Messages;
import java.util.List;
import java.util.Objects;

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

    List<BlazeJarLibrary> librariesToDetach = Lists.newArrayList();
    AttachedSourceJarManager sourceJarManager = AttachedSourceJarManager.getInstance(project);

    BlazeJavaSyncData syncData = blazeProjectData.getSyncState().get(BlazeJavaSyncData.class);
    if (syncData == null) {
      Messages.showErrorDialog(project, "Project isn't synced. Please resync project.", "Error");
      return;
    }
    ImmutableList<String> libraryNames =
        stream(LibraryTablesRegistrar.getInstance().getLibraryTable(project).getLibraries())
            .map(Library::getName)
            .filter(Objects::nonNull)
            .collect(toImmutableList());

    LibraryFilesProviderFactory libraryFilesProviderFactory =
        LibraryFilesProviderFactory.getInstance(project);
    syncData
        .getImportResult()
        .libraries
        .forEach(
            (blazeLibraryKey, blazeJarLibrary) -> {
              String libraryName = libraryFilesProviderFactory.get(blazeJarLibrary).getName();
              if (libraryNames.contains(libraryName)) {
                // detach blaze library's source jars
                if (sourceJarManager.hasSourceJarAttached(blazeLibraryKey)) {
                  sourceJarManager.setHasSourceJarAttached(blazeLibraryKey, false);
                }
                // detach library's source jars
                LibraryKey libraryKey = LibraryKey.fromIntelliJLibraryName(libraryName);
                if (sourceJarManager.hasSourceJarAttached(libraryKey)) {
                  sourceJarManager.setHasSourceJarAttached(libraryKey, false);
                }
                librariesToDetach.add(blazeJarLibrary);
              }
            });

    if (librariesToDetach.isEmpty()) {
      return;
    }
    Transactions.submitWriteActionTransaction(
        project,
        () -> {
          IdeModifiableModelsProvider modelsProvider =
              BaseSdkCompat.createModifiableModelsProvider(project);
          for (BlazeJarLibrary blazeLibrary : librariesToDetach) {
            LibraryEditor.updateLibrary(project, blazeProjectData, modelsProvider, blazeLibrary);
          }
          modelsProvider.commit();
        });
  }

  static class DetachAllOnSync implements SyncListener {

    @Override
    public void onSyncComplete(
        Project project,
        BlazeContext context,
        BlazeImportSettings importSettings,
        ProjectViewSet projectViewSet,
        ImmutableSet<Integer> buildIds,
        BlazeProjectData blazeProjectData,
        SyncMode syncMode,
        SyncResult syncResult) {
      if (syncMode == SyncMode.FULL) {
        detachAll(project);
      }
    }
  }
}
