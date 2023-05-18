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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.exception.BuildException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

/** A local cache of project dependencies. */
public interface DependencyCache {

  /* Cleans up all cache files and reset Artifact map. */
  void invalidateAll() throws IOException;

  /* Caches new Artifacts to local. */
  UpdateResult update(Set<Label> targets, OutputInfo outputInfo, BlazeContext context)
      throws BuildException;

  /* Save artifact info to disk. */
  void saveState() throws IOException;

  Set<Label> getLiveCachedTargets();

  Path getExternalAarDirectory();

  Path getGenSrcCacheDirectory();

  ImmutableList<Path> getGenSrcSubfolders() throws IOException;

  /** A data class representing the result of updating artifacts. */
  @AutoValue
  public abstract static class UpdateResult {
    public abstract ImmutableSet<Path> updatedFiles();

    public abstract ImmutableSet<String> removedKeys();

    public static UpdateResult create(
        ImmutableSet<Path> updatedFiles, ImmutableSet<String> removedKeys) {
      return new AutoValue_DependencyCache_UpdateResult(updatedFiles, removedKeys);
    }
  }
}
