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
package com.google.idea.blaze.android.sync.importer;

import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.android.sync.model.AarLibrary;
import com.google.idea.blaze.android.sync.model.BlazeResourceLibrary;
import com.google.idea.blaze.base.ideinfo.AndroidResFolder;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.LibraryKey;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Util class used to generate BlazeResourceLibrary. */
public final class LibraryFactory {
  private Map<String, AarLibrary> aarLibraries = new HashMap<>();
  private Map<String, BlazeResourceLibrary.Builder> resourceLibraries = new HashMap<>();
  private final Map<ArtifactLocation, String> resFolderToBuildFile = new HashMap<>();

  public ImmutableMap<String, AarLibrary> getAarLibs() {
    return ImmutableMap.copyOf(aarLibraries);
  }

  public ImmutableMap<String, BlazeResourceLibrary> getBlazeResourceLibs() {
    ImmutableMap.Builder<String, BlazeResourceLibrary> builder = ImmutableMap.builder();
    for (Map.Entry<String, BlazeResourceLibrary.Builder> entry : resourceLibraries.entrySet()) {
      builder.put(entry.getKey(), entry.getValue().build());
    }
    return builder.build();
  }

  /**
   * Creates a new BlazeResourceLibrary, or locates an existing one if one already existed for this
   * location. Returns the library key for the library.
   */
  @NotNull
  private String createBlazeResourceLibrary(
      @NotNull ArtifactLocation root,
      @NotNull Set<String> resources,
      @Nullable ArtifactLocation manifestLocation,
      @Nullable String buildFile) {
    String libraryKey = BlazeResourceLibrary.libraryNameFromArtifactLocation(root);
    BlazeResourceLibrary.Builder library = resourceLibraries.get(libraryKey);
    ArtifactLocation existedManifestLocation = library == null ? null : library.getManifest();
    if (!Objects.equals(existedManifestLocation, manifestLocation)) {
      // For each target, it's hard to tell whether a manifest file is specific for a resource
      // since targets are allowed have same resource directory but different manifest files.
      // So for a target, we have the following assumption
      // 1. A target's manifest file may be specific for its resource when its resource folder and
      // its BUILD file are under same directory
      // 2. If multiple targets meet requirement 1, the closest to resource folder wins
      if (buildFile == null || manifestLocation == null) {
        manifestLocation = existedManifestLocation;
      } else {
        String buildFileParent = buildFile.split("/BUILD", -1)[0];
        if (root.getRelativePath().startsWith(buildFileParent)
            && buildFileParent.startsWith(
                resFolderToBuildFile.getOrDefault(root, buildFileParent))) {
          resFolderToBuildFile.put(root, buildFileParent);
        } else if (existedManifestLocation != null) {
          manifestLocation = existedManifestLocation;
        }
      }
    }
    if (library == null) {
      library = new BlazeResourceLibrary.Builder().setRoot(root).setManifest(manifestLocation);
      resourceLibraries.put(libraryKey, library);
    }
    library.addResources(resources);
    library.setManifest(manifestLocation);
    return libraryKey;
  }

  @NotNull
  public String createBlazeResourceLibrary(
      @NotNull AndroidResFolder androidResFolder,
      @Nullable ArtifactLocation manifestLocation,
      @Nullable String buildFile) {
    return createBlazeResourceLibrary(
        androidResFolder.getRoot(), androidResFolder.getResources(), manifestLocation, buildFile);
  }

  /**
   * Creates a new Aar repository for this target, if possible, or locates an existing one if one
   * already existed for this location. Returns the key for the library or null if no aar exists for
   * this target.
   */
  @Nullable
  public String createAarLibrary(@NotNull TargetIdeInfo target) {
    // NOTE: we are not doing jdeps optimization, even though we have the jdeps data for the AAR's
    // jar. The aar might still have resources that are used (e.g., @string/foo in .xml), and we
    // don't have the equivalent of jdeps data.
    if (target.getAndroidAarIdeInfo() == null
        || target.getJavaIdeInfo() == null
        || target.getJavaIdeInfo().getJars().isEmpty()) {
      return null;
    }

    String libraryKey =
        LibraryKey.libraryNameFromArtifactLocation(target.getAndroidAarIdeInfo().getAar());
    if (!aarLibraries.containsKey(libraryKey)) {
      // aar_import should only have one jar (a merged jar from the AAR's jars).
      LibraryArtifact firstJar = target.getJavaIdeInfo().getJars().iterator().next();
      aarLibraries.put(
          libraryKey, new AarLibrary(firstJar, target.getAndroidAarIdeInfo().getAar()));
    }
    return libraryKey;
  }
}
