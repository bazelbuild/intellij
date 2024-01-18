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

import static com.intellij.openapi.util.io.FileUtilRt.getExtension;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.command.buildresult.OutputArtifactInfo;
import com.google.idea.blaze.base.qsync.cache.FileCache.CacheLayout;
import com.google.idea.blaze.base.qsync.cache.FileCache.OutputArtifactDestinationAndLayout;
import java.nio.file.Path;
import java.util.Collection;
import javax.annotation.Nullable;

/** Places java/kt source files in a subdirectory matching their package name. */
public class JavaSourcesCacheLayout implements CacheLayout {

  private static final ImmutableSet<String> JAVA_EXTENSIONS = ImmutableSet.of("java", "kt");

  /** Cache subdirectory in which all source files (w/ package subdirectories) are placed. */
  public static final String ROOT_DIRECTORY_NAME = "java";

  private final Path cacheDirectory;
  private final Path dotCacheDirectory;

  public JavaSourcesCacheLayout(Path cacheDirectory) {
    this.cacheDirectory = cacheDirectory;
    this.dotCacheDirectory = cacheDirectory.resolveSibling("." + cacheDirectory.getFileName());
  }

  @Nullable
  @Override
  public OutputArtifactDestinationAndLayout getOutputArtifactDestinationAndLayout(
      OutputArtifactInfo outputArtifact) {
    Path artifactPath = Path.of(outputArtifact.getRelativePath());
    if (!JAVA_EXTENSIONS.contains(getExtension(artifactPath.toString()))) {
      return null;
    }
    String key = CacheDirectoryManager.cacheKeyForArtifact(outputArtifact);
    return JavaSourceOutputArtifactDestination.create(
        artifactPath,
        artifactPath.getFileName().toString(),
        dotCacheDirectory.resolve(".gensrc").resolve(key),
        cacheDirectory.resolve(ROOT_DIRECTORY_NAME));
  }

  @Override
  public Collection<Path> getCachePaths() {
    return ImmutableList.of(cacheDirectory, dotCacheDirectory);
  }
}
