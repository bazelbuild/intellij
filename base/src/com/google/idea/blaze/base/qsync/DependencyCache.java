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

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.idea.blaze.base.qsync.cache.ArtifactFetcher;
import com.google.idea.blaze.base.qsync.cache.ArtifactTracker;
import com.google.idea.blaze.base.qsync.cache.ArtifactTracker.UpdateResult;
import com.google.idea.blaze.common.Label;
import com.intellij.openapi.project.Project;
import java.io.IOException;
import java.util.Set;

/** A local cache of project dependencies. */
public class DependencyCache {
  private final Supplier<ArtifactTracker> artifactTrackerProvider;

  public DependencyCache(Project project, ArtifactFetcher artifactFetcher) {
    artifactTrackerProvider =
        Suppliers.memoize(
            () -> {
              ArtifactTracker artifactTracker = new ArtifactTracker(project, artifactFetcher);
              artifactTracker.initialize();
              return artifactTracker;
            });
  }

  /* Cleans up all cache files and reset Artifact map. */
  public void invalidateAll() throws IOException {
    artifactTrackerProvider.get().clear();
  }

  /* Caches new Artifacts to local. */
  public UpdateResult update(Set<Label> targets, OutputInfo outputInfo) throws IOException {
    return artifactTrackerProvider.get().add(targets, outputInfo);
  }

  /* Save artifact info to disk. */
  public void saveState() throws IOException {
    artifactTrackerProvider.get().saveToDisk();
  }

  public Set<Label> getCachedTargets() {
    return artifactTrackerProvider.get().getCachedTargets();
  }
}
