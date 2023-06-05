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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.common.hash.Hashing;
import com.google.idea.blaze.base.command.buildresult.OutputArtifactInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.PathUtil;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A class that knows how to manage artifact caches.
 *
 * <p>(1) This class manages cache directories and (2) this class keeps track of the digests of
 * artifacts stored in the cache.
 */
class CacheDirectoryManager {

  private final Path digestDirectory;
  private final ImmutableList<Path> cacheDirectories;

  public CacheDirectoryManager(Path digestDirectory, ImmutableList<Path> cacheDirectories) {
    this.digestDirectory = digestDirectory;
    this.cacheDirectories = cacheDirectories;
  }

  /**
   * Initializes the cache.
   *
   * <p>Both in-memory and on-disk structures are initialized.
   */
  public void initialize() {
    try {
      for (Path cacheDirectory : cacheDirectories) {
        Files.createDirectories(cacheDirectory);
      }
      Files.createDirectories(digestDirectory);
    } catch (IOException e) {
      throw new UncheckedIOException(
          String.format(
              "Cannot initialize cache. Directories:\n  %s,\n  %s",
              digestDirectory, cacheDirectories),
          e);
    }
  }

  /**
   * Clears the cache.
   *
   * <p>Both in-memory and on-disk storage is cleared.
   */
  public void clear() throws IOException {
    // Delete dot directory first to ensure invalidation if interrupted.
    for (Path cacheDirectory : cacheDirectories) {
      if (Files.exists(cacheDirectory)) {
        FileUtil.delete(cacheDirectory.toFile());
      }
    }
    if (Files.exists(digestDirectory)) {
      FileUtil.delete(digestDirectory.toFile());
    }
    initialize();
  }

  static String cacheKeyForArtifact(OutputArtifactInfo artifactInfo) {
    return String.format(
        "%s.%s",
        CacheDirectoryManager.cacheKeyInternal(artifactInfo),
        FileUtilRt.getExtension(artifactInfo.getRelativePath()));
  }

  private static String cacheKeyInternal(OutputArtifactInfo artifactInfo) {
    String name =
        FileUtil.getNameWithoutExtension(PathUtil.getFileName(artifactInfo.getRelativePath()));
    return name
        + "_"
        + Integer.toHexString(
            Hashing.sha256().hashString(artifactInfo.getRelativePath(), UTF_8).hashCode());
  }

  /** Gets the previously stored digest of the given artifact. */
  public String getStoredArtifactDigest(OutputArtifactInfo artifactInfo) {
    return fileContentOrEmptyString(
        digestDirectory.resolve(cacheKeyForArtifact(artifactInfo) + ".txt"));
  }

  /** Stores the digest of the given artifact for later use. */
  public void setStoredArtifactDigest(OutputArtifactInfo artifactInfo, String value) {
    try {
      Path artifactDigestFile = digestDirectory.resolve(cacheKeyForArtifact(artifactInfo) + ".txt");
      if (value.isEmpty()) {
        Files.deleteIfExists(artifactDigestFile);
      } else {
        Files.writeString(artifactDigestFile, value);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static String fileContentOrEmptyString(Path path) {
    if (Files.isRegularFile(path)) {
      try {
        return Files.readString(path);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
    return "";
  }
}
