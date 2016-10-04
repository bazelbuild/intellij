/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.java.sync;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.projectview.section.Glob;
import com.google.idea.blaze.java.sync.model.BlazeJarLibrary;
import com.google.idea.blaze.java.sync.model.BlazeJavaSyncData;
import com.google.idea.blaze.java.sync.model.BlazeLibrary;
import java.util.List;
import java.util.stream.Collectors;

/** Collects libraries from the sync data using all contributors. */
public class BlazeLibraryCollector {
  public static List<BlazeLibrary> getLibraries(BlazeProjectData blazeProjectData) {
    BlazeJavaSyncData syncData = blazeProjectData.syncState.get(BlazeJavaSyncData.class);
    if (syncData == null) {
      return ImmutableList.of();
    }

    Glob.GlobSet excludedLibraries = syncData.excludedLibraries;

    List<BlazeLibrary> libraries = Lists.newArrayList();
    libraries.addAll(syncData.importResult.libraries.values());
    for (BlazeJavaSyncAugmenter augmenter :
        BlazeJavaSyncAugmenter.getActiveSyncAgumenters(
            blazeProjectData.workspaceLanguageSettings)) {
      libraries.addAll(augmenter.getAdditionalLibraries(blazeProjectData));
    }
    return libraries
        .stream()
        .filter(blazeLibrary -> !isExcluded(excludedLibraries, blazeLibrary))
        .collect(Collectors.toList());
  }

  private static boolean isExcluded(Glob.GlobSet excludedLibraries, BlazeLibrary blazeLibrary) {
    if (!(blazeLibrary instanceof BlazeJarLibrary)) {
      return false;
    }
    BlazeJarLibrary jarLibrary = (BlazeJarLibrary) blazeLibrary;
    ArtifactLocation interfaceJar = jarLibrary.libraryArtifact.interfaceJar;
    ArtifactLocation classJar = jarLibrary.libraryArtifact.classJar;
    return (interfaceJar != null && excludedLibraries.matches(interfaceJar.getRelativePath()))
        || (classJar != null && excludedLibraries.matches(classJar.getRelativePath()));
  }
}
