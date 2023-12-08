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
import com.google.idea.blaze.qsync.java.PackageStatementParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Write generated java/kt sources to a directory matching their package name. */
@AutoValue
public abstract class JavaSourceOutputArtifactDestination
    implements OutputArtifactDestinationAndLayout {

  private final PackageStatementParser packageStatementParser = new PackageStatementParser();

  @Override
  public abstract String getKey();

  @Override
  public abstract Path getCopyDestination();

  abstract String getOriginalFilename();

  abstract Path getDestinationJavaSourceDir();

  public static JavaSourceOutputArtifactDestination create(
      String key, String originalFileName, Path copyDestination, Path destinationJavaSourceDir) {
    return new AutoValue_JavaSourceOutputArtifactDestination(
        key, copyDestination, originalFileName, destinationJavaSourceDir);
  }

  @Override
  public Path prepareFinalLayout() {
    Path finalDest;
    try {
      String pkg = packageStatementParser.readPackage(getCopyDestination());
      finalDest =
          getDestinationJavaSourceDir()
              .resolve(Path.of(pkg.replace('.', '/')))
              .resolve(getOriginalFilename());
    } catch (IOException e) {
      throw new UncheckedIOException(
          "Failed to determine the final destination for " + getCopyDestination(), e);
    }
    try {
      Files.createDirectories(finalDest.getParent());
      Files.copy(getCopyDestination(), finalDest, StandardCopyOption.REPLACE_EXISTING);
      return finalDest;
    } catch (IOException e) {
      throw new UncheckedIOException(
          "Failed to copy " + getCopyDestination() + " to its final destination " + finalDest, e);
    }
  }
}
