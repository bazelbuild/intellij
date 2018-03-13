/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.filecache;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.idea.blaze.base.scope.BlazeContext;
import java.io.File;
import java.util.Collection;

/**
 * Traits to customize how a {@link FileCacheSynchronizer} operates.
 *
 * <p>A Source file is linked to a Cache file by a CacheKey, which is just a String.
 */
public interface FileCacheSynchronizerTraits {

  /** Return the current collection of source files. */
  Collection<File> sourceFiles();

  /** Return the cache key for a given source file. */
  String sourceFileToCacheKey(File sourceFile);

  /** Enumerate and return the current collection files in the cache directory. */
  Collection<File> enumerateCacheFiles();

  /** Return the cache key for a given cache file. */
  String cacheFileToCacheKey(File cacheFile);

  /** Return the cache file for a given cache key. */
  File cacheFileForKey(String cacheKey);

  /**
   * Update the cache for the given list of CacheKeys.
   *
   * @return the futures spawned for this task
   */
  Collection<ListenableFuture<?>> updateFiles(
      Collection<String> cacheKeys, ListeningExecutorService executorService);

  /**
   * Remove the files in the cache based on the list of CacheKeys.
   *
   * @return the futures spawned for this task
   */
  Collection<ListenableFuture<?>> removeFiles(
      Collection<String> cacheKeys, ListeningExecutorService executorService);

  /** Log some statistics about how many files were updated or removed */
  void logStats(
      BlazeContext context, int numUpdatedFiles, int numRemovedFiles, boolean removeMissingFiles);
}
