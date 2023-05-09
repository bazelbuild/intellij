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
package com.google.idea.blaze.base.qsync;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.qsync.cache.ArtifactTracker;
import com.google.idea.blaze.base.qsync.cache.ArtifactTracker.UpdateResult;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.exception.BuildException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

/** A local cache of project dependencies. */
public class DependencyCache {
  private final ArtifactTracker artifactTracker;

  public DependencyCache(ArtifactTracker artifactTracker) {
    this.artifactTracker = artifactTracker;
  }

  /* Cleans up all cache files and reset Artifact map. */
  public void invalidateAll() throws IOException {
    artifactTracker.clear();
  }

  /* Caches new Artifacts to local. */
  public UpdateResult update(Set<Label> targets, OutputInfo outputInfo, BlazeContext context)
      throws BuildException {
    return artifactTracker.add(targets, outputInfo, context);
  }

  /* Save artifact info to disk. */
  public void saveState() throws IOException {
    artifactTracker.saveToDisk();
  }

  public Set<Label> getCachedTargets() {
    return artifactTracker.getCachedTargets();
  }

  public Path getGenSrcCacheDirectory() {
    return artifactTracker.getGenSrcCacheDirectory();
  }

  public ImmutableList<Path> getGenSrcSubfolders() throws IOException {
    return artifactTracker.getGenSrcSubfolders();
  }
}
