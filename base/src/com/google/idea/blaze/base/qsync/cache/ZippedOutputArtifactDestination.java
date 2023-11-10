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

import com.google.idea.blaze.base.qsync.cache.FileCache.OutputArtifactDestinationAndLayout;
import com.google.idea.blaze.qsync.PackageStatementParser;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.util.io.FileUtil;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** A record that describes the location of an output artifact in cache directories. */
public class ZippedOutputArtifactDestination implements OutputArtifactDestinationAndLayout {

  private final String key;

  private final PackageStatementParser packageStatementParser = new PackageStatementParser();

  // If set, extracts java and kotlin files to a directory matching their package.
  private static final BoolExperiment readPackageFromFiles =
      new BoolExperiment("qsync.srcjar.read.package", true);

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

  private void extract(Path source, Path destination) {
    try {
      Files.createDirectories(destination);
      try (ZipFile zip = new ZipFile(source.toFile())) {
        for (var entries = zip.entries(); entries.hasMoreElements(); ) {
          ZipEntry entry = entries.nextElement();
          if (readPackageFromFiles.getValue()) {
            if (entry.getName().endsWith(".java") || entry.getName().endsWith(".kt")) {
              String packageName = packageStatementParser.readPackage(zip.getInputStream(entry));
              String filename = entry.getName().substring(entry.getName().lastIndexOf('/') + 1);
              Path packageDir = destination.resolve(Path.of(packageName.replace('.', '/')));
              Files.createDirectories(packageDir);
              Files.copy(
                  zip.getInputStream(entry),
                  destination.resolve(packageDir).resolve(filename),
                  StandardCopyOption.REPLACE_EXISTING);
              continue;
            }
          }

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
  public Path prepareFinalLayout() {
    if (Files.exists(finalDestination)) {
      FileUtil.delete(finalDestination.toFile());
    }
    extract(copyDestination, finalDestination);
    return finalDestination;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ZippedOutputArtifactDestination)) {
      return false;
    }
    ZippedOutputArtifactDestination that = (ZippedOutputArtifactDestination) o;
    return key.equals(that.key)
        && finalDestination.equals(that.finalDestination)
        && copyDestination.equals(that.copyDestination);
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, finalDestination, copyDestination);
  }
}
