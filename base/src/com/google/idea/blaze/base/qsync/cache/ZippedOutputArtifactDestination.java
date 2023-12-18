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

import com.google.auto.value.AutoValue;
import com.google.idea.blaze.base.qsync.cache.FileCache.OutputArtifactDestinationAndLayout;
import com.intellij.openapi.util.io.FileUtil;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** A record that describes the location of an output artifact in cache directories. */
@AutoValue
public abstract class ZippedOutputArtifactDestination
    implements OutputArtifactDestinationAndLayout {

  public static ZippedOutputArtifactDestination create(
      String key, Path finalDestination, Path copyDestination) {
    return new AutoValue_ZippedOutputArtifactDestination(key, finalDestination, copyDestination);
  }

  private static void extract(Path source, Path destination) {
    try {
      Files.createDirectories(destination);
      try (ZipFile zip = new ZipFile(source.toFile())) {
        for (var entries = zip.entries(); entries.hasMoreElements(); ) {
          ZipEntry entry = entries.nextElement();
          if (entry.isDirectory()) {
            Files.createDirectories(destination.resolve(entry.getName()));
          } else {
            // Srcjars do not contain separate directory entries
            Files.createDirectories(destination.resolve(entry.getName()).getParent());
            Files.copy(
                zip.getInputStream(entry),
                destination.resolve(entry.getName()),
                StandardCopyOption.REPLACE_EXISTING);
          }
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to extract " + source + " to " + destination, e);
    }
  }

  @Override
  public abstract String getKey();

  /**
   * The location where in the cache directory the representation of the artifact for the IDE should
   * be placed.
   */
  protected abstract Path getFinalDestination();

  /**
   * The location where in the cache directory the artifact file itself should be placed.
   *
   * <p>The final and copy destinations are the same if the artifact file needs not to be extracted.
   */
  @Override
  public abstract Path getCopyDestination();

  @Override
  public Path determineFinalDestination() {
    return getFinalDestination();
  }

  @Override
  public void createFinalDestination(Path finalDestination) {
    if (Files.exists(finalDestination)) {
      FileUtil.delete(finalDestination.toFile());
    }
    extract(getCopyDestination(), finalDestination);
  }
}
