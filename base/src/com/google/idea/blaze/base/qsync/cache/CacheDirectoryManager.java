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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.hash.Hashing;
import com.google.idea.blaze.base.command.buildresult.OutputArtifact;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.PathUtil;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** A class that knows how to manage artifact cache directories. */
class CacheDirectoryManager {
  private static final String DIGESTS_DIRECTORY_NAME = ".digests";

  public final Path cacheDirectory;
  private final Path digestsDirectory;
  @VisibleForTesting public final Path cacheDotDirectory;

  public CacheDirectoryManager(Path cacheDirectory, Path cacheDotDirectory) {
    if (cacheDotDirectory.startsWith(cacheDirectory)) {
      throw new IllegalArgumentException(
          String.format(
              "cacheDotDirectory cannot be under cacheDirectory. cacheDirectory: %s,"
                  + " cacheDotDirectory: %s",
              cacheDirectory, cacheDotDirectory));
    }
    this.cacheDirectory = cacheDirectory;
    this.digestsDirectory = cacheDotDirectory.resolve(DIGESTS_DIRECTORY_NAME);
    this.cacheDotDirectory = cacheDotDirectory;
  }

  /**
   * Initializes the cache.
   *
   * <p>Both in-memory and on-disk structures are initialized.
   */
  public void initialize() throws IOException {
    try {
      Files.createDirectories(cacheDirectory);
      Files.createDirectories(cacheDotDirectory);
      Files.createDirectories(digestsDirectory);
    } catch (IOException e) {
      throw new IOException(
          String.format(
              "Cannot initialize cache. Directories:\n  %s,\n  %s",
              cacheDirectory, cacheDotDirectory),
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
    if (Files.exists(cacheDotDirectory)) {
      FileUtil.delete(cacheDotDirectory.toFile());
    }
    if (Files.exists(cacheDirectory)) {
      FileUtil.delete(cacheDirectory.toFile());
    }
    initialize();
  }

  static String cacheKeyForArtifact(String artifactKey) {
    return String.format(
        "%s.%s",
        CacheDirectoryManager.cacheKeyInternal(artifactKey), FileUtilRt.getExtension(artifactKey));
  }

  private static String cacheKeyInternal(String artifactKey) {
    String name = FileUtil.getNameWithoutExtension(PathUtil.getFileName(artifactKey));
    return name
        + "_"
        + Integer.toHexString(Hashing.sha256().hashString(artifactKey, UTF_8).hashCode());
  }

  /** Gets the previously stored digest of the given artifact. */
  public String getStoredArtifactDigest(OutputArtifact outputArtifact) {
    return fileContentOrEmptyString(
        digestsDirectory.resolve(cacheKeyForArtifact(outputArtifact.getKey()) + ".txt"));
  }

  /**
   * Gets the path of artifact in cache directory. The path is calculated according to artifact
   * relative but but it's not guaranteed the existence of file.
   */
  public Path getArtifactLocalPath(String artifactPath) {
    return cacheDirectory.resolve(cacheKeyForArtifact(artifactPath));
  }

  /** Stores the digest of the given artifact for later use. */
  public void setStoredArtifactDigest(OutputArtifact outputArtifact, String value) {
    try {
      Path artifactDigestFile =
          digestsDirectory.resolve(cacheKeyForArtifact(outputArtifact.getKey()) + ".txt");
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
