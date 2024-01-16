/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.qsync.cache;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.command.buildresult.OutputArtifactInfo;
import com.google.idea.blaze.base.qsync.cache.FileCache.CacheLayout;
import com.google.idea.blaze.base.qsync.cache.FileCache.OutputArtifactDestinationAndLayout;
import java.nio.file.Path;
import java.util.Collection;

/**
 * Places artifacts in the cache using the same path and filename that they appear at in the build
 * outputs directory.
 */
public class ArtifactPathCacheLayout implements CacheLayout {

  private final Path cacheDirectory;

  ArtifactPathCacheLayout(Path cacheDirectory) {
    this.cacheDirectory = cacheDirectory;
  }

  @Override
  public OutputArtifactDestinationAndLayout getOutputArtifactDestinationAndLayout(
      OutputArtifactInfo outputArtifact) {
    return PreparedOutputArtifactDestination.create(
        cacheDirectory.resolve(outputArtifact.getRelativePath()));
  }

  @Override
  public Collection<Path> getCachePaths() {
    return ImmutableList.of(cacheDirectory);
  }
}
