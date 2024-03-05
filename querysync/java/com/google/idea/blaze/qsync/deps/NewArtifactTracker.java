/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.exception.BuildException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

/** Placeholder for the new artifact tracker logic. */
public class NewArtifactTracker<C extends Context<C>> implements ArtifactTracker<C> {

  public NewArtifactTracker() {
    // TODO(b/323346056) Implement this.
  }

  @Override
  public void clear() throws IOException {
    // TODO(b/323346056) Implement this.
  }

  @Override
  public void update(Set<Label> targets, OutputInfo outputInfo, C context) throws BuildException {
    // TODO(b/323346056) Implement this.
  }

  @Override
  public Optional<ImmutableSet<Path>> getCachedFiles(Label target) {
    // TODO(b/323346056) this is only used to find built AARs for a target. Refactor that code.
    return Optional.empty();
  }

  @Override
  public ImmutableSet<Path> getTargetSources(Path cachedArtifact) {
    // TODO(b/323346056) Implement this.
    return ImmutableSet.of();
  }

  @Override
  public Set<Label> getLiveCachedTargets() {
    // TODO(b/323346056) Implement this.
    return ImmutableSet.of();
  }

  @Override
  public Path getExternalAarDirectory() {
    // TODO(b/323346056) Implement this.
    return Path.of("/nosuchdir");
  }

  @Override
  public Integer getJarsCount() {
    return 0;
  }

  @Override
  public Iterable<Path> getBugreportFiles() {
    return ImmutableList.of();
  }
}
