/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.filecache;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.idea.blaze.base.prefetch.FetchExecutor;
import com.google.idea.blaze.base.scope.BlazeContext;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;

/**
 * Synchronizes original ("source") files and cached files for a {@link FileCache}. Updates or
 * removes files if the timestamps of the original file and the cached file are different.
 *
 * <p>Delegates the actual file naming, updates and deletion to {@link FileCacheSynchronizerTraits}.
 */
public class FileCacheSynchronizer {
  private final FileCacheSynchronizerTraits traits;

  public FileCacheSynchronizer(FileCacheSynchronizerTraits traits) {
    this.traits = traits;
  }

  /**
   * Synchronizes the underlying file cache according to {@link #traits}.
   *
   * @param context an optional context
   * @param removeMissingFiles true if this should remove any files from the cache that are no
   *     longer at the source location.
   */
  public void synchronize(@Nullable BlazeContext context, boolean removeMissingFiles)
      throws InterruptedException, ExecutionException {
    // Discover state of source jars
    ImmutableMap<File, Long> sourceFileTimestamps = FileDiffer.readFileState(traits.sourceFiles());
    ImmutableMap.Builder<String, Long> sourceFileCacheKeyToTimestamp = ImmutableMap.builder();
    for (Map.Entry<File, Long> entry : sourceFileTimestamps.entrySet()) {
      String cacheKey = traits.sourceFileToCacheKey(entry.getKey());
      sourceFileCacheKeyToTimestamp.put(cacheKey, entry.getValue());
    }

    // Discover current on-disk cache state
    Collection<File> cacheFiles = traits.enumerateCacheFiles();
    ImmutableMap<File, Long> cacheFileTimestamps =
        FileDiffer.readFileState(new ArrayList<>(cacheFiles));
    ImmutableMap.Builder<String, Long> cachedFileCacheKeyToTimestamp = ImmutableMap.builder();
    for (Map.Entry<File, Long> entry : cacheFileTimestamps.entrySet()) {
      String cacheKey = traits.cacheFileToCacheKey(entry.getKey());
      cachedFileCacheKeyToTimestamp.put(cacheKey, entry.getValue());
    }

    List<String> updatedFiles = new ArrayList<>();
    List<String> removedFiles = new ArrayList<>();
    FileDiffer.diffState(
        cachedFileCacheKeyToTimestamp.build(),
        sourceFileCacheKeyToTimestamp.build(),
        updatedFiles,
        removedFiles);

    // Update cache files, and remove files if required.
    ListeningExecutorService executor = FetchExecutor.EXECUTOR;
    List<ListenableFuture<?>> futures = new ArrayList<>();
    futures.addAll(traits.updateFiles(updatedFiles, executor));
    if (removeMissingFiles) {
      futures.addAll(traits.removeFiles(removedFiles, executor));
    }

    Futures.allAsList(futures).get();
    if (context != null) {
      traits.logStats(context, updatedFiles.size(), removedFiles.size(), removeMissingFiles);
    }
  }
}
