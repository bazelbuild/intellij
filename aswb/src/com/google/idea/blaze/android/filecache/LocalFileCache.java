/*
 * Copyright 2021 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.filecache;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/** Maintains cache state for local cached files. */
public class LocalFileCache implements LocalCache {
  private static final Logger logger = Logger.getInstance(LocalFileCache.class);
  /** Name of the cache. This is used for logging purposes only. */
  private final String cacheName;

  /** Absolute path to the directory where artifacts will be stored. */
  private final Path cacheDir;

  /**
   * Maps cache key to CacheEntry. A cache key is a String to uniquely identify a CacheEntry for a
   * specific set of Artifacts. The cache key is the same as what is stored in CacheEntry.
   */
  private final Map<String, CacheEntry> cacheState = new HashMap<>();

  public LocalFileCache(Project project, String cacheName, Path cacheDir) {
    this.cacheName = cacheName;
    this.cacheDir = cacheDir;
  }

  /**
   * {@inheritDoc}
   *
   * <p>NOTE: This method does blocking disk I/O, so should not be called in the cache's
   * constructor.
   */
  @Override
  public synchronized void initialize() {
    loadCacheData();
  }

  /**
   * {@inheritDoc}
   *
   * <p>Any untracked files in {@link #cacheDir} will be removed as well. Blocks until done.
   */
  @Override
  public synchronized void clearCache() {
    try {
      ImmutableList<ListenableFuture<?>> deletionFutures = LocalCacheUtils.clearCache(cacheDir);
      Futures.allAsList(deletionFutures).get();
    } catch (ExecutionException | LocalCacheOperationException e) {
      logger.warn("Could not delete contents of " + cacheDir, e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } finally {
      // write an empty state to disk
      cacheState.clear();
      writeCacheData();
    }
  }

  @Override
  public synchronized void refresh() {
    cacheState.clear();
    try (Stream<Path> stream = Files.list(cacheDir)) {
      stream.forEach(
          file -> {
            CacheEntry cacheEntry = CacheEntry.forFile(file.toFile());
            cacheState.put(cacheEntry.getCacheKey(), cacheEntry);
          });
    } catch (IOException e) {
      logger.warn("Fail to refresh contents of " + cacheDir, e);
    } finally {
      writeCacheData();
    }
  }

  @Override
  @Nullable
  public synchronized Path get(String cacheKey) {
    CacheEntry cacheEntry = cacheState.get(cacheKey);
    if (cacheEntry == null) {
      return null;
    }
    return LocalCacheUtils.getPathToCachedFile(cacheDir, cacheEntry.getFileName());
  }

  /**
   * Loads cache information from {@link #cacheDir} and ensures the that the serialized cache
   * information is consistent between serialized json and files on disk. If inconsistent cache
   * state is found, makes the best effort to fix cache state and make it consistent.
   */
  private void loadCacheData() {
    // All files in cache directory except the serialized cache state json
    Set<File> cachedFiles = LocalCacheUtils.getCacheFiles(cacheDir);

    File cacheDataFile = LocalCacheUtils.getCacheDataFile(cacheDir);
    // No cache data file, but there are other files present in cache directory
    if (!FileOperationProvider.getInstance().exists(cacheDataFile) && !cachedFiles.isEmpty()) {
      logger.warn(
          String.format(
              "%s does not exist, but %s contains cached files. Clearing directory for a clean"
                  + " start.",
              cacheDataFile, cacheDir));
      clearCache();
      return;
    }

    try {
      // Read cache state from disk
      LocalCacheUtils.loadCacheDataFileToCacheState(cacheDataFile, cacheState);

      // Remove any references to files that no longer exists in file system
      ImmutableCollection<String> removedReferences =
          LocalCacheUtils.removeStaleReferences(cachedFiles, cacheState);

      if (!removedReferences.isEmpty()) {
        logger.warn(
            String.format(
                "%d invalid references in %s. Removed invalid references.",
                removedReferences.size(), cacheName));
      }

      // Remove any file in FS that is not referenced by the cache
      removeUntrackedFiles(cachedFiles);
    } finally {
      writeCacheData();
    }
  }

  /** Deletes files in {@code cachedFiles} that are not tracked in {@link #cacheState}. */
  private void removeUntrackedFiles(Set<File> cachedFiles) {
    ImmutableList<ListenableFuture<?>> futures =
        LocalCacheUtils.removeUntrackedFiles(cachedFiles, cacheState);

    if (futures.isEmpty()) {
      return;
    }

    try {
      Futures.allAsList(futures).get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (ExecutionException e) {
      logger.warn("Could not remove untracked files", e);
    }
  }

  /** Serializes {@link #cacheState} to a file on disk. */
  private void writeCacheData() {
    File cacheDataFile = LocalCacheUtils.getCacheDataFile(cacheDir);
    CacheData cacheData = new CacheData(cacheState.values());

    try {
      LocalCacheUtils.writeCacheData(cacheDir, cacheDataFile, cacheData).get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (ExecutionException e) {
      logger.warn(String.format("Failed to write cache state file %s", cacheDataFile));
    }
  }

  public boolean isEmpty() {
    return cacheState.isEmpty();
  }
}
