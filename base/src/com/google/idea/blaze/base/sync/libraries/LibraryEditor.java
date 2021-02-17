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
package com.google.idea.blaze.base.sync.libraries;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.idea.blaze.base.model.BlazeLibrary;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.LibraryKey;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.PrintOutput;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Edits IntelliJ libraries */
public class LibraryEditor {
  private static final Logger logger = Logger.getInstance(LibraryEditor.class);

  public static void updateProjectLibraries(
      Project project,
      BlazeContext context,
      ProjectViewSet projectViewSet,
      BlazeProjectData blazeProjectData,
      Collection<BlazeLibrary> libraries) {
    Set<LibraryKey> intelliJLibraryState = Sets.newHashSet();
    LibraryTable libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project);
    for (Library library : libraryTable.getLibraries()) {
      String name = library.getName();
      if (name != null) {
        intelliJLibraryState.add(LibraryKey.fromIntelliJLibraryName(name));
      }
    }
    context.output(PrintOutput.log(String.format("Workspace has %d libraries", libraries.size())));

    LibraryTable.ModifiableModel libraryTableModel = libraryTable.getModifiableModel();
    try {
      for (BlazeLibrary library : libraries) {
        updateLibrary(
            project,
            blazeProjectData.getArtifactLocationDecoder(),
            libraryTable,
            libraryTableModel,
            library);
      }

      // Garbage collect unused libraries
      List<LibrarySource> librarySources = Lists.newArrayList();
      for (BlazeSyncPlugin syncPlugin : BlazeSyncPlugin.EP_NAME.getExtensions()) {
        LibrarySource librarySource = syncPlugin.getLibrarySource(projectViewSet, blazeProjectData);
        if (librarySource != null) {
          librarySources.add(librarySource);
        }
      }
      Predicate<Library> gcRetentionFilter =
          librarySources.stream()
              .map(LibrarySource::getGcRetentionFilter)
              .filter(Objects::nonNull)
              .reduce(Predicate::or)
              .orElse(o -> false);

      Set<LibraryKey> newLibraryKeys =
          libraries.stream().map((blazeLibrary) -> blazeLibrary.key).collect(Collectors.toSet());
      for (LibraryKey libraryKey : intelliJLibraryState) {
        String libraryIntellijName = libraryKey.getIntelliJLibraryName();
        if (!newLibraryKeys.contains(libraryKey)) {
          Library library = libraryTable.getLibraryByName(libraryIntellijName);
          if (!gcRetentionFilter.test(library)) {
            if (library != null) {
              libraryTableModel.removeLibrary(library);
            }
          }
        }
      }
    } finally {
      libraryTableModel.commit();
    }
  }

  public static void updateLibrary(
      Project project,
      ArtifactLocationDecoder artifactLocationDecoder,
      LibraryTable libraryTable,
      LibraryTable.ModifiableModel libraryTableModel,
      BlazeLibrary blazeLibrary) {
    String libraryName = blazeLibrary.key.getIntelliJLibraryName();

    Library library = libraryTable.getLibraryByName(libraryName);
    boolean libraryExists = library != null;
    if (!libraryExists) {
      library = libraryTableModel.createLibrary(libraryName);
    }
    Library.ModifiableModel libraryModel = library.getModifiableModel();
    if (libraryExists) {
      for (String url : libraryModel.getUrls(OrderRootType.CLASSES)) {
        libraryModel.removeRoot(url, OrderRootType.CLASSES);
      }
      for (String url : libraryModel.getUrls(OrderRootType.SOURCES)) {
        libraryModel.removeRoot(url, OrderRootType.SOURCES);
      }
    }
    try {
      blazeLibrary.modifyLibraryModel(project, artifactLocationDecoder, libraryModel);
    } finally {
      libraryModel.commit();
    }
  }

  public static void configureDependencies(
      ModifiableRootModel modifiableRootModel, Collection<BlazeLibrary> libraries) {
    for (BlazeLibrary library : libraries) {
      updateLibraryDependency(modifiableRootModel, library.key);
    }
  }

  private static void updateLibraryDependency(ModifiableRootModel model, LibraryKey libraryKey) {
    LibraryTable libraryTable =
        LibraryTablesRegistrar.getInstance().getLibraryTable(model.getProject());
    Library library = libraryTable.getLibraryByName(libraryKey.getIntelliJLibraryName());
    if (library == null) {
      logger.error(
          "Library missing: "
              + libraryKey.getIntelliJLibraryName()
              + ". Please resync project to resolve.");
      return;
    }
    model.addLibraryEntry(library);
  }
}
