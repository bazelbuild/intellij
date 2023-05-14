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

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toCollection;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.logging.EventLoggingService;
import com.google.idea.blaze.base.prefetch.FetchExecutor;
import com.intellij.openapi.diagnostic.Logger;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Util class that provides methods to maintain caches that used by {@link LocalCache} and its
 * implementations.
 */
final class LocalCacheUtils {
  private static final Logger logger = Logger.getInstance(LocalCacheUtils.class);

  /** Name of file that contains the cache state. */
  @VisibleForTesting static final String CACHE_DATA_FILENAME = "cache_data.json";

  /** Clear cache state and remove all the files in cacheDir */
  public static ImmutableList<ListenableFuture<?>> clearCache(Path cacheDir)
      throws LocalCacheOperationException {
    // List and delete all files in the cache directory
    File[] filesInDir = FileOperationProvider.getInstance().listFiles(cacheDir.toFile());
    if (filesInDir == null) {
      throw new LocalCacheOperationException(String.format("Could not list files in %s", cacheDir));
    }
    return deleteFiles(ImmutableList.copyOf(filesInDir));
  }

  private static ImmutableList<ListenableFuture<?>> deleteFiles(Collection<File> removed) {
    return removed.stream()
        .map(
            f ->
                FetchExecutor.EXECUTOR.submit(
                    () -> {
                      try {
                        Files.deleteIfExists(f.toPath());
                      } catch (IOException e) {
                        logger.warn(e);
                      }
                    }))
        .collect(ImmutableList.toImmutableList());
  }

  /** Get All files in cache directory except the serialized cache state json. */
  public static Set<File> getCacheFiles(Path cacheDir) {
    File cacheDirFile = cacheDir.toFile();

    // mkdirs is no-op if the directory already exists.
    FileOperationProvider.getInstance().mkdirs(cacheDirFile);

    if (!FileOperationProvider.getInstance().isDirectory(cacheDirFile)) {
      throw new IllegalArgumentException(
          "Cache Directory '" + cacheDirFile + "' is not a valid directory");
    }

    // List files in the cache directory to ensure that the cache is in a consistent state.
    File[] allFilesInCacheDir = FileOperationProvider.getInstance().listFiles(cacheDirFile);
    if (allFilesInCacheDir == null) {
      throw new IllegalArgumentException("Could not list files in directory: " + cacheDirFile);
    }

    return stream(allFilesInCacheDir)
        .filter(s -> !s.getName().equals(CACHE_DATA_FILENAME))
        .collect(toCollection(HashSet::new));
  }

  /** Load cache state. */
  public static void loadCacheDataFileToCacheState(
      File cacheDataFile, Map<String, CacheEntry> cacheState) {
    // Read cache state from disk
    CacheData cacheData = readJsonFromDisk(cacheDataFile);
    cacheState.clear();
    cacheData.getCacheEntries().forEach(e -> cacheState.put(e.getCacheKey(), e));
  }

  @VisibleForTesting
  public static CacheData readJsonFromDisk(File cacheDataFile) {
    if (!FileOperationProvider.getInstance().exists(cacheDataFile)) {
      return new CacheData(ImmutableList.of());
    }

    try (InputStream inputStream = new FileInputStream(cacheDataFile)) {
      return CacheData.readJson(inputStream);
    } catch (IOException e) {
      logger.warn("Could not read " + cacheDataFile, e);
    }
    return new CacheData(ImmutableList.of());
  }

  /** Removes entries in cacheState that do not have a corresponding file in {@code cachedFiles}. */
  public static ImmutableCollection<String> removeStaleReferences(
      Set<File> cachedFiles, Map<String, CacheEntry> cacheState) {
    ImmutableSet<String> cachedFilenames =
        cachedFiles.stream().map(File::getName).collect(toImmutableSet());
    ImmutableCollection<String> removedKeys =
        cacheState.values().stream()
            .filter(cacheEntry -> !cachedFilenames.contains(cacheEntry.getFileName()))
            .collect(toImmutableMap(CacheEntry::getFileName, CacheEntry::getCacheKey))
            .values();

    removedKeys.forEach(cacheState::remove);
    return removedKeys;
  }

  /** Deletes files in {@code cachedFiles} that are not tracked in cacheState. */
  public static ImmutableList<ListenableFuture<?>> removeUntrackedFiles(
      Set<File> cachedFiles, Map<String, CacheEntry> cacheState) {
    // Files in cachedFiles not referenced by cacheState
    Set<File> untrackedFiles = new HashSet<>(cachedFiles);
    ImmutableSet<String> trackedFilenames =
        cacheState.values().stream().map(CacheEntry::getFileName).collect(toImmutableSet());

    untrackedFiles.removeIf(file -> !trackedFilenames.contains(file.getName()));

    if (untrackedFiles.isEmpty()) {
      return ImmutableList.of();
    }

    return deleteFiles(untrackedFiles);
  }

  /** Writes cache data to disk. */
  public static ListenableFuture<?> writeCacheData(
      Path cacheDir, File cacheDataFile, CacheData cacheData) {
    // Logger will catch any issues with writing the cache state
    return FetchExecutor.EXECUTOR.submit(
        () -> {
          try {
            writeJsonToDisk(cacheDataFile, cacheData);
            logCacheInfo(cacheDir.toFile());
          } catch (IOException e) {
            logger.warn(String.format("Failed to write cache state file %s", cacheDataFile));
          }
        });
  }

  private static void writeJsonToDisk(File cacheDataFile, CacheData cacheData) throws IOException {
    try (OutputStream stream = new FileOutputStream(cacheDataFile)) {
      cacheData.writeJson(stream);
    }
  }

  private static void logCacheInfo(File cacheDirFile) {
    long cacheDirSize = FileOperationProvider.getInstance().getFileSize(cacheDirFile);

    int numFiles = 0;
    File[] filesInDir = FileOperationProvider.getInstance().listFiles(cacheDirFile);
    if (filesInDir != null) {
      numFiles = filesInDir.length - 1;
    }

    ImmutableMap.Builder<String, String> data = ImmutableMap.builder();
    data.put("CacheDir", cacheDirFile.toString());
    data.put("CacheSize", Long.toString(cacheDirSize));
    data.put("NumCachedFiles", Integer.toString(numFiles));
    EventLoggingService.getInstance()
        .logEvent(LocalArtifactCache.class, "CacheDataWritten", data.build());
  }

  /** Returns the {@link File} containing serialized cache data. */
  public static File getCacheDataFile(Path cacheDir) {
    return cacheDir.resolve(CACHE_DATA_FILENAME).toFile();
  }

  /** Returns the {@link File} in cacheDir. */
  public static Path getPathToCachedFile(Path cacheDir, String fileName) {
    return cacheDir.resolve(fileName);
  }

  private LocalCacheUtils() {}
}
