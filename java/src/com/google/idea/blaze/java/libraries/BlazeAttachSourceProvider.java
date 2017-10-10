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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.model.BlazeLibrary;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.LibraryKey;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.libraries.LibraryEditor;
import com.google.idea.blaze.java.settings.BlazeJavaUserSettings;
import com.google.idea.blaze.java.sync.model.BlazeJarLibrary;
import com.google.idea.sdkcompat.transactions.Transactions;
import com.intellij.codeInsight.AttachSourcesProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.psi.PsiFile;
import java.util.Collection;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/** @author Sergey Evdokimov */
public class BlazeAttachSourceProvider implements AttachSourcesProvider {
  @NotNull
  @Override
  public Collection<AttachSourcesAction> getActions(
      List<LibraryOrderEntry> orderEntries, final PsiFile psiFile) {
    Project project = psiFile.getProject();
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (blazeProjectData == null) {
      return ImmutableList.of();
    }

    List<BlazeLibrary> librariesToAttachSourceTo = Lists.newArrayList();
    for (LibraryOrderEntry orderEntry : orderEntries) {
      Library library = orderEntry.getLibrary();
      if (library == null) {
        continue;
      }
      LibraryKey libraryKey = LibraryKey.fromIntelliJLibrary(library);
      if (SourceJarManager.getInstance(project).hasSourceJarAttached(libraryKey)) {
        continue;
      }
      BlazeJarLibrary blazeLibrary =
          LibraryActionHelper.findLibraryFromIntellijLibrary(project, blazeProjectData, library);
      if (blazeLibrary == null) {
        continue;
      }
      LibraryArtifact libraryArtifact = blazeLibrary.libraryArtifact;
      if (libraryArtifact.sourceJars.isEmpty()) {
        continue;
      }
      librariesToAttachSourceTo.add(blazeLibrary);
    }

    if (librariesToAttachSourceTo.isEmpty()) {
      return ImmutableList.of();
    }

    /**
     * Semi-hack: When sources are requested and we have them, we attach them automatically if the
     * corresponding user setting is active.
     */
    if (BlazeJavaUserSettings.getInstance().getAttachSourcesOnDemand()) {
      Transactions.submitTransaction(
          project,
          () -> {
            attachSources(project, blazeProjectData, librariesToAttachSourceTo);
          });
      return ImmutableList.of();
    }

    return ImmutableList.of(
        new AttachSourcesAction() {
          @Override
          public String getName() {
            return "Attach Blaze Source Jars";
          }

          @Override
          public String getBusyText() {
            return "Attaching source jars...";
          }

          @Override
          public ActionCallback perform(List<LibraryOrderEntry> orderEntriesContainingFile) {
            attachSources(project, blazeProjectData, librariesToAttachSourceTo);
            return ActionCallback.DONE;
          }
        });
  }

  static void attachSources(
      Project project,
      BlazeProjectData blazeProjectData,
      Collection<BlazeLibrary> librariesToAttachSourceTo) {
    ApplicationManager.getApplication()
        .runWriteAction(
            () -> {
              LibraryTable libraryTable = ProjectLibraryTable.getInstance(project);
              LibraryTable.ModifiableModel libraryTableModel = libraryTable.getModifiableModel();
              for (BlazeLibrary blazeLibrary : librariesToAttachSourceTo) {
                // Make sure we don't do it twice
                if (SourceJarManager.getInstance(project).hasSourceJarAttached(blazeLibrary.key)) {
                  continue;
                }
                SourceJarManager.getInstance(project)
                    .setHasSourceJarAttached(blazeLibrary.key, true);
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
}
