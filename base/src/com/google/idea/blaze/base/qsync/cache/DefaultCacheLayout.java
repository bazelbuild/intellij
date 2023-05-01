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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.command.buildresult.OutputArtifact;
import com.google.idea.blaze.base.qsync.cache.FileCache.CacheLayout;
import com.google.idea.blaze.base.qsync.cache.FileCache.OutputArtifactDestination;
import com.intellij.openapi.util.io.FileUtilRt;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * A cache layout implementation building its cache directory in a way that can be consumed by the
 * IDE.
 *
 * <p>The implementation supports the following use cases:
 *
 * <ol>
 *   <li>.jar libraries
 *   <li>.aar libraries
 *   <li>.srcjar bundles
 * </ol>
 *
 * <p>See {@link #getOutputArtifactDestination} for the details of the layout.
 */
public class DefaultCacheLayout implements CacheLayout {

  private static final String PACKED_FILES_DIR = ".zips";

  private final ImmutableSet<String> zipFileExtensions;
  private final Path cacheDirectory;

  public DefaultCacheLayout(Path cacheDirectory, ImmutableSet<String> zipFileExtensions) {
    this.cacheDirectory = cacheDirectory;
    this.zipFileExtensions = zipFileExtensions;
  }

  /**
   * Maps output artifacts to the paths of local files the artifacts should be copied to.
   *
   * <p>Output artifacts that needs to be extracted for being used in the IDE are placed into
   * sub-directories under {@code dir} in which their content will be extracted later.
   *
   * <p>When artifact files are extracted, the final file system layout looks like:
   *
   * <pre>
   *     aars/
   *         .zips/
   *             file.zip-like.aar                # the zip file being extracted
   *         file.zip-like.aar/
   *             file.txt                         # a file from file.zip-like.aar
   *             res/                             # a directory from file.zip-like.aar
   *                 layout/
   *                     main.xml
   * </pre>
   */
  @Override
  public OutputArtifactDestination getOutputArtifactDestination(OutputArtifact outputArtifact) {
    String key = CacheDirectoryManager.cacheKeyForArtifact(outputArtifact.getKey());
    final Path finalDestination = cacheDirectory.resolve(key);
    if (shouldExtractFile(Path.of(outputArtifact.getRelativePath()))) {
      return new ZippedOutputArtifactDestination(
          finalDestination, cacheDirectory.resolve(PACKED_FILES_DIR).resolve(key));
    } else {
      return new PreparedOutputArtifactDestination(finalDestination);
    }
  }

  private boolean shouldExtractFile(Path sourcePath) {
    return zipFileExtensions.contains(FileUtilRt.getExtension(sourcePath.getFileName().toString()));
  }

  @Override
  public List<Path> getSubdirectories() throws IOException {
    try (Stream<Path> pathStream = Files.list(cacheDirectory)) {
      return pathStream
          .filter(p -> Files.isDirectory(p) && !p.getFileName().toString().equals(PACKED_FILES_DIR))
          .collect(toImmutableList());
    }
  }
}
