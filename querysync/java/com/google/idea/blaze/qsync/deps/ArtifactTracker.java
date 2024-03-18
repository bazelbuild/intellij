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
package com.google.idea.blaze.qsync.deps;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.exception.BuildException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

/** A local cache of project dependencies. */
public interface ArtifactTracker<ContextT extends Context<?>> {

  /** Drops all artifacts and clears caches. */
  void clear() throws IOException;

  /** Fetches, caches and sets up new artifacts. */
  void update(Set<Label> targets, OutputInfo outputInfo, ContextT context) throws BuildException;

  /**
   * Returns a list of local cache files that build by target provided. Returns Optional.empty() if
   * the target has not yet been built.
   */
  Optional<ImmutableSet<Path>> getCachedFiles(Label target);

  /**
   * Returns the sources corresponding to an artifact in the cache.
   *
   * @param cachedArtifact A cached jar file.
   * @return The list of workspace relative source files from the target that {@code libJar} was
   *     derived from.
   */
  ImmutableSet<Path> getTargetSources(Path cachedArtifact);

  /**
   * Returns the set of targets that artifacts are set up for.
   *
   * <p>Note, the returned set is a live set which is updated as a result of {@link #update} and
   * {@link #clear} invocation.
   */
  Set<Label> getLiveCachedTargets();

  /**
   * Returns the location of the directory containing unpacked Android libraries (i.e. resources and
   * manifests) in the layout expected by the IDE.
   */
  Path getExternalAarDirectory();

  /** Returns the count of .jar files. */
  Integer getJarsCount();

  public Iterable<Path> getBugreportFiles();
}
