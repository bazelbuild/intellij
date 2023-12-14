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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.command.buildresult.OutputArtifactInfo;
import com.google.idea.blaze.base.qsync.cache.FileCache.CacheLayout;
import com.google.idea.blaze.base.qsync.cache.FileCache.OutputArtifactDestinationAndLayout;
import com.intellij.openapi.util.io.FileUtilRt;
import java.nio.file.Path;
import java.util.Collection;
import javax.annotation.Nullable;

/** A cache layout that unzips zip files. */
public class UnzippingCacheLayout implements CacheLayout {

  @VisibleForTesting public static final String PACKED_FILES_DIR = ".zips";

  private final Path cacheDotDirectory;
  private final ImmutableSet<String> zipFileExtensions;
  private final Path cacheDirectory;

  public UnzippingCacheLayout(Path cacheDirectory, ImmutableSet<String> zipFileExtensions) {
    this.cacheDirectory = cacheDirectory;
    this.cacheDotDirectory = cacheDirectory.resolveSibling("." + cacheDirectory.getFileName());
    this.zipFileExtensions = zipFileExtensions;
  }

  /**
   * Maps output artifacts to the paths of local files the artifacts should be copied to.
   *
   * <p>Output artifacts that needs to be extracted for being used in the IDE are placed into
   * sub-directories under {@code cacheDotDirectory} and the final layout is built under {@code
   * cacheDirectory}.
   *
   * <p>When artifact files are extracted, the final file system layout looks like:
   *
   * <pre>
   *     .aars/
   *         .zips/
   *             file.zip-like.aar                # the zip file being extracted
   *     aars/
   *         file.zip-like.aar/
   *             file.txt                         # a file from file.zip-like.aar
   *             res/                             # a directory from file.zip-like.aar
   *                 layout/
   *                     main.xml
   * </pre>
   */
  @Override
  @Nullable
  public OutputArtifactDestinationAndLayout getOutputArtifactDestinationAndLayout(
      OutputArtifactInfo outputArtifact) {
    if (!shouldExtractFile(Path.of(outputArtifact.getRelativePath()))) {
      return null;
    }
    String key = CacheDirectoryManager.cacheKeyForArtifact(outputArtifact);
    return ZippedOutputArtifactDestination.create(
        key, cacheDirectory.resolve(key), cacheDotDirectory.resolve(PACKED_FILES_DIR).resolve(key));
  }

  private boolean shouldExtractFile(Path sourcePath) {
    return zipFileExtensions.contains(FileUtilRt.getExtension(sourcePath.getFileName().toString()));
  }

  @Override
  public Collection<Path> getCachePaths() {
    return ImmutableList.of(cacheDirectory, cacheDotDirectory);
  }
}
