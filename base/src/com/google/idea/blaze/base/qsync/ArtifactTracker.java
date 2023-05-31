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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.project.BlazeProjectSnapshot;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

/** A local cache of project dependencies. */
public interface ArtifactTracker {

  /** Drops all artifacts and clears caches. */
  void clear() throws IOException;

  /** Fetches, caches and sets up new artifacts. */
  UpdateResult update(Set<Label> targets, OutputInfo outputInfo, BlazeContext context)
      throws BuildException;

  /**
   * Makes the project snapshot reflect the current state of tracked artifacts.
   *
   * <p>When additional artifacts are brought into the IDE they may require additional configuration
   * to be applied to the IDE project.
   */
  BlazeProjectSnapshot updateSnapshot(BlazeProjectSnapshot snapshot) throws IOException;

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

  /** A data class representing the result of updating artifacts. */
  @AutoValue
  abstract class UpdateResult {
    public abstract ImmutableSet<Path> updatedFiles();

    public abstract ImmutableSet<String> removedKeys();

    public static UpdateResult create(
        ImmutableSet<Path> updatedFiles, ImmutableSet<String> removedKeys) {
      return new AutoValue_ArtifactTracker_UpdateResult(updatedFiles, removedKeys);
    }
  }
}
