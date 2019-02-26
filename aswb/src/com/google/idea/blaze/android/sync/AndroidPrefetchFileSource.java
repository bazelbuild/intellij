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
package com.google.idea.blaze.android.sync;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.android.sync.model.BlazeAndroidSyncData;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.prefetch.PrefetchFileSource;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.Set;
import java.util.stream.Collectors;

/** Adds the resource directories outside our source roots to prefetch. */
public class AndroidPrefetchFileSource implements PrefetchFileSource {
  @Override
  public void addFilesToPrefetch(
      Project project,
      ProjectViewSet projectViewSet,
      ImportRoots importRoots,
      BlazeProjectData blazeProjectData,
      Set<File> files) {
    BlazeAndroidSyncData syncData = blazeProjectData.getSyncState().get(BlazeAndroidSyncData.class);
    if (syncData == null) {
      return;
    }
    if (syncData.importResult.resourceLibraries == null) {
      return;
    }
    ArtifactLocationDecoder artifactLocationDecoder = blazeProjectData.getArtifactLocationDecoder();
    files.addAll(
        artifactLocationDecoder.decodeAll(
            syncData.importResult.resourceLibraries.values().stream()
                .map(resourceLibrary -> resourceLibrary.root)
                .collect(Collectors.toList())));
  }

  @Override
  public Set<String> prefetchFileExtensions() {
    return ImmutableSet.of("xml");
  }
}
