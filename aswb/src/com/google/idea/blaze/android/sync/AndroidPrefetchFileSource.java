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
package com.google.idea.blaze.android.sync;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.android.sync.model.BlazeAndroidSyncData;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.prefetch.PrefetchFileSource;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.Collection;
import java.util.Set;

/** Adds the resource directories outside our source roots to prefetch. */
public class AndroidPrefetchFileSource implements PrefetchFileSource {
  @Override
  public void addFilesToPrefetch(
      Project project, BlazeProjectData blazeProjectData, Collection<File> files) {
    BlazeAndroidSyncData syncData = blazeProjectData.syncState.get(BlazeAndroidSyncData.class);
    if (syncData == null) {
      return;
    }
    if (syncData.importResult.resourceLibrary == null) {
      return;
    }
    ArtifactLocationDecoder artifactLocationDecoder = blazeProjectData.artifactLocationDecoder;
    files.addAll(artifactLocationDecoder.decodeAll(syncData.importResult.resourceLibrary.sources));
  }

  @Override
  public Set<String> prefetchSrcFileExtensions() {
    return ImmutableSet.of("xml");
  }
}
