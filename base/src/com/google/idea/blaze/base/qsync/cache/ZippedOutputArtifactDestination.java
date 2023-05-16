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

import com.google.common.io.MoreFiles;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** A record that describes the location of an output artifact in cache directories. */
public class ZippedOutputArtifactDestination implements FileCache.OutputArtifactDestination {

  private final String key;

  /**
   * The location where in the cache directory the representation of the artifact for the IDE should
   * be placed.
   */
  public final Path finalDestination;

  private final Path copyDestination;

  public ZippedOutputArtifactDestination(String key, Path finalDestination, Path copyDestination) {
    this.key = key;
    this.finalDestination = finalDestination;
    this.copyDestination = copyDestination;
  }

  private static void extract(Path source, Path destination) throws IOException {
    Files.createDirectories(destination);
    try (InputStream inputStream = Files.newInputStream(source);
        ZipInputStream zis = new ZipInputStream(inputStream)) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        if (entry.isDirectory()) {
          Files.createDirectories(destination.resolve(entry.getName()));
        } else {
          // Srcjars do not contain separate directory entries
          Files.createDirectories(destination.resolve(entry.getName()).getParent());
          Files.copy(
              zis, destination.resolve(entry.getName()), StandardCopyOption.REPLACE_EXISTING);
        }
      }
    }
  }

  @Override
  public String getKey() {
    return key;
  }

  /**
   * The location where in the cache directory the artifact file itself should be placed.
   *
   * <p>The final and copy destinations are the same if the artifact file needs not to be extracted.
   */
  @Override
  public Path getCopyDestination() {
    return copyDestination;
  }

  @Override
  public Path prepareFinalLayout() throws IOException {
    if (Files.exists(finalDestination)) {
      MoreFiles.deleteRecursively(finalDestination);
    }
    extract(copyDestination, finalDestination);
    return finalDestination;
  }
}
