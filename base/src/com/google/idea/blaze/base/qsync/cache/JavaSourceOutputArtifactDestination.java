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

import static java.util.stream.Collectors.joining;

import com.google.auto.value.AutoValue;
import com.google.common.collect.Iterables;
import com.google.idea.blaze.base.qsync.cache.FileCache.ConflictResolutionStrategy;
import com.google.idea.blaze.base.qsync.cache.FileCache.OutputArtifactDestinationAndLayout;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.qsync.java.PackageStatementParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;

/** Write generated java/kt sources to a directory matching their package name. */
@AutoValue
public abstract class JavaSourceOutputArtifactDestination
    implements OutputArtifactDestinationAndLayout {

  private final PackageStatementParser packageStatementParser = new PackageStatementParser();

  public abstract Path getBuildOutPath();

  @Override
  public abstract Path getCopyDestination();

  abstract String getOriginalFilename();

  abstract Path getDestinationJavaSourceDir();

  public static JavaSourceOutputArtifactDestination create(
      Path buildOutPath,
      String originalFileName,
      Path copyDestination,
      Path destinationJavaSourceDir) {
    return new AutoValue_JavaSourceOutputArtifactDestination(
        buildOutPath, copyDestination, originalFileName, destinationJavaSourceDir);
  }

  @Override
  public Path determineFinalDestination() {
    try {
      String pkg = packageStatementParser.readPackage(getCopyDestination());
      return getDestinationJavaSourceDir()
          .resolve(Path.of(pkg.replace('.', '/')))
          .resolve(getOriginalFilename());
    } catch (IOException e) {
      throw new UncheckedIOException(
          "Failed to determine the final destination for " + getCopyDestination(), e);
    }
  }

  @Override
  public void createFinalDestination(Path finalDest) {
    Path copyDestination = getCopyDestination();
    try {
      Files.createDirectories(finalDest.getParent());
      Files.copy(copyDestination, finalDest, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      throw new UncheckedIOException(
          "Failed to copy " + copyDestination + " to its final destination " + finalDest, e);
    }
  }

  @Override
  public ConflictResolutionStrategy getConflictStrategy() {
    return SelectSingleJavaSourceStrategy.INSTANCE;
  }

  private static class SelectSingleJavaSourceStrategy implements ConflictResolutionStrategy {

    static final SelectSingleJavaSourceStrategy INSTANCE = new SelectSingleJavaSourceStrategy();

    private SelectSingleJavaSourceStrategy() {}

    @Override
    public OutputArtifactDestinationAndLayout resolveConflicts(
        Path finalDest,
        Collection<OutputArtifactDestinationAndLayout> conflicting,
        BlazeContext context) {
      context.output(
          PrintOutput.error(
              "WARNING: your build produced conflicting generated java sources:\n  %s",
              conflicting.stream()
                  .map(JavaSourceOutputArtifactDestination.class::cast)
                  .map(JavaSourceOutputArtifactDestination::getBuildOutPath)
                  .map(Path::toString)
                  .collect(joining("\n  "))));
      context.setHasWarnings();
      return Iterables.getFirst(conflicting, null);
    }
  }
}
