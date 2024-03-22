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
package com.google.idea.blaze.common.artifact;

import com.google.common.collect.ImmutableCollection;
import com.google.common.io.ByteSource;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.exception.BuildException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * A cache of build artifacts.
 *
 * <p>Downloads build artifacts on request, identifying them based on their digest as provided by
 * {@link OutputArtifact#getDigest()}.
 *
 * <p>For artifacts that have previously been requested via {@link #addAll(ImmutableCollection,
 * Context)}, provides access to their contents as a local file via {@link #get(String)}.
 *
 * <p>Access times are updated when artifacts downloads are requested, and when the contents are
 * requested, to enable unused cache entries to be cleaned up later on (not implemented yet).
 */
public interface BuildArtifactCache {

  static BuildArtifactCache create(
      Path cacheDir, ArtifactFetcher<OutputArtifact> fetcher, ListeningExecutorService executor)
      throws BuildException {
    return new BuildArtifactCacheDirectory(cacheDir, fetcher, executor);
  }

  /**
   * Requests that the given artifacts are added to the cache.
   *
   * @return A future map of (digest)->(absolute path of the artifact) that will complete once all
   *     artifacts have been added to the cache. The future will fail if we fail to add any artifact
   *     to the cache.
   */
  ListenableFuture<?> addAll(ImmutableCollection<OutputArtifact> artifacts, Context<?> context);

  /**
   * Returns a bytesource of an artifact that was previously added to the cache.
   *
   * @return A future of the byte source if the artifact is already present, or is in the process of
   *     being requested. Empty if the artifact has never been added to the cache, or has been
   *     deleted since then.
   */
  Optional<ListenableFuture<ByteSource>> get(String digest);
}
