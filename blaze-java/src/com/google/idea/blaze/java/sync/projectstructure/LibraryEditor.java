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
package com.google.idea.blaze.java.sync.projectstructure;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.PrintOutput;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.google.idea.blaze.java.libraries.SourceJarManager;
import com.google.idea.blaze.java.sync.BlazeJavaSyncAugmenter;
import com.google.idea.blaze.java.sync.model.BlazeLibrary;
import com.google.idea.blaze.java.sync.model.LibraryKey;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.io.URLUtil;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Edits IntelliJ libraries
 */
public class LibraryEditor {
  private static final Logger LOG = Logger.getInstance(LibraryEditor.class);

  public static void updateProjectLibraries(Project project,
                                            BlazeContext context,
                                            BlazeProjectData blazeProjectData,
                                            Collection<BlazeLibrary> newLibraries,
                                            Collection<BlazeLibrary> oldLibraries) {
    Set<LibraryKey> intelliJLibraryState = Sets.newHashSet();
    for (Library library : ProjectLibraryTable.getInstance(project).getLibraries()) {
      String name = library.getName();
      if (name != null) {
        intelliJLibraryState.add(LibraryKey.fromIntelliJLibraryName(name));
      }
    }
    Collection<BlazeLibrary> librariesToUpdate = getUpdatedObjects(oldLibraries,
                                                                   newLibraries,
                                                                   intelliJLibraryState);
    if (oldLibraries.isEmpty()) {
      context.output(new PrintOutput(
        String.format(
          "Importing %d libraries",
          librariesToUpdate.size())));
    }
    else {
      String consoleMessage = String.format(
        "Total libraries: %d\n"
        + "Updating %d modified libraries",
        newLibraries.size(),
        librariesToUpdate.size());
      context.output(new PrintOutput(consoleMessage));
    }

    Set<String> externallyAddedLibraries = Sets.newHashSet();
    for (BlazeJavaSyncAugmenter syncAugmenter : BlazeJavaSyncAugmenter.EP_NAME.getExtensions()) {
      externallyAddedLibraries.addAll(syncAugmenter.getExternallyAddedLibraries(blazeProjectData));
    }

    LibraryTable libraryTable = ProjectLibraryTable.getInstance(project);
    LibraryTable.ModifiableModel libraryTableModel =
      libraryTable.getModifiableModel();
    try {
      boolean attachSourcesByDefault = BlazeUserSettings.getInstance().getAttachSourcesByDefault();
      SourceJarManager sourceJarManager = SourceJarManager.getInstance(project);
      for (BlazeLibrary library : librariesToUpdate) {
        boolean attachSources = attachSourcesByDefault || sourceJarManager.hasSourceJarAttached(library.getKey());
        updateLibrary(libraryTable, libraryTableModel, library, attachSources);
      }

      // Garbage collect unused libraries
      Set<LibraryKey> newLibraryKeys = newLibraries.stream().map(BlazeLibrary::getKey).collect(Collectors.toSet());
      for (LibraryKey libraryKey : intelliJLibraryState) {
        String libraryIntellijName = libraryKey.getIntelliJLibraryName();
        if (!newLibraryKeys.contains(libraryKey) && !externallyAddedLibraries.contains(libraryIntellijName)) {
          Library library = libraryTable.getLibraryByName(libraryIntellijName);
          if (library != null) {
            libraryTableModel.removeLibrary(library);
          }
        }
      }
    }
    finally {
      libraryTableModel.commit();
    }
  }


  public static void updateLibrary(
    LibraryTable libraryTable,
    LibraryTable.ModifiableModel libraryTableModel,
    BlazeLibrary blazeLibrary,
    boolean attachSourceJar) {
    String libraryName = blazeLibrary.getKey().getIntelliJLibraryName();
    Library library = libraryTable.getLibraryByName(libraryName);
    if (library != null) {
      libraryTableModel.removeLibrary(library);
    }
    library = libraryTableModel.createLibrary(libraryName);

    Library.ModifiableModel libraryModel = library.getModifiableModel();
    try {
      LibraryArtifact libraryArtifact = blazeLibrary.getLibraryArtifact();
      if (libraryArtifact != null) {
        libraryModel.addRoot(
          pathToUrl(libraryArtifact.jar.getFile()),
          OrderRootType.CLASSES
        );
        if (attachSourceJar && libraryArtifact.sourceJar != null) {
          libraryModel.addRoot(
            pathToUrl(libraryArtifact.sourceJar.getFile()),
            OrderRootType.SOURCES
          );
        }
      }
      if (blazeLibrary.getSources() != null) {
        for (File file : blazeLibrary.getSources()) {
          libraryModel.addRoot(
            pathToUrl(file),
            OrderRootType.SOURCES
          );
        }
      }
    }
    finally {
      libraryModel.commit();
    }
  }

  static Collection<BlazeLibrary> getUpdatedObjects(Collection<BlazeLibrary> oldObjects,
                                                    Collection<BlazeLibrary> newObjects,
                                                    Set<LibraryKey> intelliJState) {
    List<BlazeLibrary> result = Lists.newArrayList();
    Set<BlazeLibrary> oldObjectSet = Sets.newHashSet(oldObjects);
    for (BlazeLibrary value : newObjects) {
      LibraryKey key = value.getKey();
      if (!intelliJState.contains(key) || !oldObjectSet.contains(value)) {
        result.add(value);
      }
    }
    return result;
  }

  private static String pathToUrl(File path) {
    String name = path.getName();
    boolean isJarFile = FileUtilRt.extensionEquals(name, "jar") ||
                        FileUtilRt.extensionEquals(name, "zip");
    // .jar files require an URL with "jar" protocol.
    String protocol = isJarFile
                      ? StandardFileSystems.JAR_PROTOCOL
                      : StandardFileSystems.FILE_PROTOCOL;
    String filePath = FileUtil.toSystemIndependentName(path.getPath());
    String url = VirtualFileManager.constructUrl(protocol, filePath);
    if (isJarFile) {
      url += URLUtil.JAR_SEPARATOR;
    }
    return url;
  }

  public static void configureDependencies(
    Project project,
    BlazeContext context,
    ModifiableRootModel modifiableRootModel,
    Collection<BlazeLibrary> libraries) {
    for (BlazeLibrary library : libraries) {
      updateLibraryDependency(modifiableRootModel, library.getKey());
    }
  }

  private static void updateLibraryDependency(
    ModifiableRootModel model,
    LibraryKey libraryKey) {
    LibraryTable libraryTable = ProjectLibraryTable.getInstance(model.getProject());
    Library library = libraryTable.getLibraryByName(libraryKey.getIntelliJLibraryName());
    if (library == null) {
      LOG.error("Library missing: " + libraryKey.getIntelliJLibraryName() + ". Please resync project to resolve.");
      return;
    }
    model.addLibraryEntry(library);
  }
}
