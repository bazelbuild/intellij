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

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.command.buildresult.OutputArtifactInfo;
import com.google.idea.blaze.base.qsync.cache.FileCache.CacheLayout;
import com.google.idea.blaze.base.qsync.cache.FileCache.OutputArtifactDestinationAndLayout;
import java.nio.file.Path;
import javax.annotation.Nullable;

/** Places java/kt source files in a subdirectory matching their package name. */
public class JavaSourcesCacheLayout implements CacheLayout {

  private static final ImmutableSet<String> JAVA_EXTENSIONS = ImmutableSet.of("java", "kt");

  private final Path cacheDirectory;
  private final Path dotCacheDirectory;

  public JavaSourcesCacheLayout(Path cacheDirectory, Path dotCacheDirectory) {
    this.cacheDirectory = cacheDirectory;
    this.dotCacheDirectory = dotCacheDirectory;
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
    if (!JAVA_EXTENSIONS.contains(getExtension(artifactPath))) {
      return null;
    }
    String key = CacheDirectoryManager.cacheKeyForArtifact(outputArtifact);
    return JavaSourceOutputArtifactDestination.create(
        key,
        artifactPath.getFileName().toString(),
        dotCacheDirectory.resolve(".gensrc").resolve(key),
        cacheDirectory.resolve("java"));
  }
}
