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
package com.google.idea.blaze.java.sync.importer;

import com.google.common.collect.*;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.java.sync.model.BlazeLibrary;
import com.google.idea.blaze.java.sync.model.LibraryKey;

import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Manages libraries during the module import stage.
 */
public class LibraryBuilder {

  private final Map<LibraryKey, BlazeLibrary> libraries = Maps.newHashMap();
  private final Multimap<Label, LibraryKey> labelToLibraryKeys = ArrayListMultimap.create();
  private final Map<String, LibraryKey> jdepsPathToLibraryKey = Maps.newHashMap();
  private final Set<LibraryKey> referencedLibraryKeys = Sets.newHashSet();

  void createLibraryForRule(Label label, LibraryArtifact libraryArtifact) {
    LibraryKey libraryKey = createLibrary(libraryArtifact);
    labelToLibraryKeys.put(label, libraryKey);
  }

  public void createLibraryForModule(LibraryArtifact libraryArtifact) {
    LibraryKey libraryKey = createLibrary(libraryArtifact);
    referencedLibraryKeys.add(libraryKey);
  }

  void referenceLibraryFromModule(Label label) {
    Collection<LibraryKey> libraryKeys = labelToLibraryKeys.get(label);
    referencedLibraryKeys.addAll(libraryKeys);
  }

  void referenceLibraryFromModule(String jdepsPath) {
    LibraryKey libraryKey = jdepsPathToLibraryKey.get(jdepsPath);
    if (libraryKey != null) {
      referencedLibraryKeys.add(libraryKey);
    }
  }

  private LibraryKey createLibrary(LibraryArtifact libraryArtifact) {
    File jar = libraryArtifact.jar.getFile();
    LibraryKey key = LibraryKey.fromJarFile(jar);
    BlazeLibrary library = new BlazeLibrary(key, libraryArtifact);
    addLibrary(key, library);
    return key;
  }

  private void addLibrary(LibraryKey key,
                          BlazeLibrary library) {
    BlazeLibrary existingLibrary = libraries.putIfAbsent(key, library);
    existingLibrary = existingLibrary != null ? existingLibrary : library;

    LibraryArtifact libraryArtifact = existingLibrary.getLibraryArtifact();

    // Index the library by jar for jdeps support
    if (libraryArtifact != null) {
      ArtifactLocation jar = libraryArtifact.jar;
      jdepsPathToLibraryKey.put(jar.getExecutionRootRelativePath(), key);

      ArtifactLocation runtimeJar = libraryArtifact.runtimeJar;
      if (runtimeJar != null) {
        jdepsPathToLibraryKey.put(runtimeJar.getExecutionRootRelativePath(), key);
      }
    }
  }

  ImmutableMap<LibraryKey, BlazeLibrary> build() {
    ImmutableMap.Builder<LibraryKey, BlazeLibrary> result = ImmutableMap.builder();
    for (LibraryKey libraryKey : referencedLibraryKeys) {
      result.put(libraryKey, libraries.get(libraryKey));
    }
    return result.build();
  }
}
