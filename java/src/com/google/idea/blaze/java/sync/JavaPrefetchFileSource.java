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

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.model.BlazeLibrary;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.prefetch.PrefetchFileSource;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.sync.libraries.BlazeLibraryCollector;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.java.libraries.JarCache;
import com.google.idea.blaze.java.libraries.SourceJarManager;
import com.google.idea.blaze.java.settings.BlazeJavaUserSettings;
import com.google.idea.blaze.java.sync.model.BlazeJarLibrary;
import com.google.idea.blaze.java.sync.model.BlazeJavaSyncData;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.Collection;
import java.util.Set;

/** Adds the jars to prefetch. */
public class JavaPrefetchFileSource implements PrefetchFileSource {
  @Override
  public void addFilesToPrefetch(
      Project project,
      ProjectViewSet projectViewSet,
      ImportRoots importRoots,
      BlazeProjectData blazeProjectData,
      Set<File> files) {
    BlazeJavaSyncData syncData = blazeProjectData.syncState.get(BlazeJavaSyncData.class);
    if (syncData == null) {
      return;
    }
    // If we have a local jar cache we don't need to prefetch anything
    if (JarCache.getInstance(project).isEnabled()) {
      return;
    }
    boolean attachSourcesByDefault =
        BlazeJavaUserSettings.getInstance().getAttachSourcesByDefault();
    SourceJarManager sourceJarManager = SourceJarManager.getInstance(project);
    Collection<BlazeLibrary> libraries =
        BlazeLibraryCollector.getLibraries(projectViewSet, blazeProjectData);
    ArtifactLocationDecoder artifactLocationDecoder = blazeProjectData.artifactLocationDecoder;
    for (BlazeLibrary library : libraries) {
      if (!(library instanceof BlazeJarLibrary)) {
        continue;
      }
      BlazeJarLibrary jarLibrary = (BlazeJarLibrary) library;
      files.add(artifactLocationDecoder.decode(jarLibrary.libraryArtifact.jarForIntellijLibrary()));

      boolean attachSourceJar =
          attachSourcesByDefault || sourceJarManager.hasSourceJarAttached(jarLibrary.key);
      if (attachSourceJar && jarLibrary.libraryArtifact.sourceJar != null) {
        files.add(artifactLocationDecoder.decode(jarLibrary.libraryArtifact.sourceJar));
      }
    }
  }

  @Override
  public Set<String> prefetchFileExtensions() {
    return ImmutableSet.of("java");
  }
}
