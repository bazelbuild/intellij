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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.command.buildresult.OutputArtifactInfo;
import com.google.idea.blaze.base.qsync.cache.FileCache.CacheLayout;
import com.google.idea.blaze.base.qsync.cache.FileCache.OutputArtifactDestinationAndLayout;
import java.nio.file.Path;
import java.util.Collection;
import javax.annotation.Nullable;

/** Places java source archives in a dedicated subdirectory. */
public class JavaSourcesArchiveCacheLayout implements CacheLayout {

  private static final ImmutableSet<String> JAVA_ARCHIVE_EXTENSIONS =
      ImmutableSet.of("jar", "srcjar");

  /** Cache subdirectory in which all source jars are placed. */
  public static final String ROOT_DIRECTORY_NAME = "java_srcjars";

  private final Path cacheDirectory;

  public JavaSourcesArchiveCacheLayout(Path cacheDirectory) {
    this.cacheDirectory = cacheDirectory;
  }

  private static String getExtension(Path p) {
    String name = p.getFileName().toString();
    if (name.contains(".")) {
      return name.substring(name.indexOf('.') + 1);
    }
    return "";
  }

  @Nullable
  @Override
  public OutputArtifactDestinationAndLayout getOutputArtifactDestinationAndLayout(
      OutputArtifactInfo outputArtifact) {
    Path artifactPath = Path.of(outputArtifact.getRelativePath());
    if (!JAVA_ARCHIVE_EXTENSIONS.contains(getExtension(artifactPath))) {
      return null;
    }
    String key = CacheDirectoryManager.cacheKeyForArtifact(outputArtifact);
    return new PreparedOutputArtifactDestination(
        key, cacheDirectory.resolve(ROOT_DIRECTORY_NAME).resolve(artifactPath));
  }

  @Override
  public Collection<Path> getCachePaths() {
    return ImmutableList.of(cacheDirectory);
  }
}
