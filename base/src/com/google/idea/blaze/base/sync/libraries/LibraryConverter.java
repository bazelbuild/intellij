/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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

import static java.util.Arrays.stream;

import com.google.idea.blaze.base.model.BlazeLibrary;
import com.google.idea.blaze.base.model.BlazeLibraryModelModifier;
import com.google.idea.blaze.base.model.LibraryKey;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.Library.ModifiableModel;
import java.util.Optional;

/** Helps convert from {@link BlazeLibrary} to {@link BlazeLibraryModelModifier}. */
public interface LibraryConverter {
  ExtensionPointName<LibraryConverter> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.LibraryConverter");

  static Optional<LibraryConverter> getFirstAvailableLibraryConverter() {
    return stream(EP_NAME.getExtensions()).filter(ep -> ep.isEnabled()).findFirst();
  }

  static ModifiableModel getLibraryModifiableModel(
      IdeModifiableModelsProvider modelsProvider, LibraryKey libraryKey) {
    String libraryName = libraryKey.getIntelliJLibraryName();
    Library library = modelsProvider.getLibraryByName(libraryName);
    boolean libraryExists = library != null;
    if (!libraryExists) {
      library = modelsProvider.createLibrary(libraryName);
    }
    return modelsProvider.getModifiableLibraryModel(library);
  }

  boolean isEnabled();

  /**
   * Gets the name of {@link Library} that needs to be updated from {@link BlazeLibrary}. Note:
   * {@link BlazeLibrary} and {@link Library} may be many-to-one mapped, so there may be multiple
   * {@link BlazeLibrary} sharing the same name. It's possible to have duplication when you try to
   * get library name for a list of {@link BlazeLibrary}.
   */
  String getLibraryName(BlazeLibrary library);

  /**
   * Converts from {@link BlazeLibrary} to {@link BlazeLibraryModelModifier} which provide access to
   * update library content. Note: Note: * {@link BlazeLibrary} and {@link
   * BlazeLibraryModelModifier} may be many-to-one mapped, so there may be multiple {@link
   * BlazeLibrary} sharing the same {@link BlazeLibraryModelModifier}. It's recommended to remove
   * duplicates before updating library model to avoid unnecessary writing time.
   */
  BlazeLibraryModelModifier getBlazeLibraryModelModifier(
      Project project,
      ArtifactLocationDecoder artifactLocationDecoder,
      IdeModifiableModelsProvider modelsProvider,
      BlazeLibrary blazeLibrary);
}
