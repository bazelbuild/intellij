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

import com.google.common.hash.Hashing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.PathUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** A class that knows how to manage artifact cache directories. */
class CacheDirectoryManager {

  public final Path cacheDirectory;

  public CacheDirectoryManager(Path cacheDirectory) {
    this.cacheDirectory = cacheDirectory;
  }

  /**
   * Initializes the cache.
   *
   * <p>Both in-memory and on-disk structures are initialized.
   */
  public void initialize() throws IOException {
    try {
      Files.createDirectories(cacheDirectory);
    } catch (IOException e) {
      throw new IOException("Cache Directory '" + cacheDirectory + "' cannot be initialized", e);
    }
  }

  /**
   * Clears the cache.
   *
   * <p>Both in-memory and on-disk storage is cleared.
   */
  public void clear() throws IOException {
    Files.deleteIfExists(cacheDirectory);
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
}
