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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.command.buildresult.OutputArtifactInfo;
import com.google.idea.blaze.base.qsync.cache.FileCache.OutputArtifactDestination;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DefaultCacheLayoutTest {

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void basic_artifact() {
    CacheDirectories cacheDirectoryManager = createCacheDirectoryManager();
    DefaultCacheLayout cacheLayout =
        new DefaultCacheLayout(
            cacheDirectoryManager.cacheDirectory,
            cacheDirectoryManager.cacheDotDirectory,
            ImmutableSet.of());

    OutputArtifactInfo outputArtifact = testOutputArtifact("libfoo.jar");

    OutputArtifactDestination artifactDestination =
        cacheLayout.getOutputArtifactDestinationAndLayout(outputArtifact);
    Path relativeCopyDestination =
        cacheDirectoryManager.cacheDirectory.relativize(artifactDestination.getCopyDestination());

    String cacheKey = CacheDirectoryManager.cacheKeyForArtifact(outputArtifact);
    assertThat(relativeCopyDestination.toString()).isEqualTo(cacheKey);
  }

  @Test
  public void zipped_artifacts() {
    CacheDirectories cacheDirectoryManager = createCacheDirectoryManager();
    DefaultCacheLayout cacheLayout =
        new DefaultCacheLayout(
            cacheDirectoryManager.cacheDirectory,
            cacheDirectoryManager.cacheDotDirectory,
            ImmutableSet.of("zip"));

    // Zipped artifact
    OutputArtifactInfo outputArtifact = testOutputArtifact("archive.zip");
    Path relativeCopyDestination =
        cacheDirectoryManager.cacheDotDirectory.relativize(
            cacheLayout.getOutputArtifactDestinationAndLayout(outputArtifact).getCopyDestination());
    String cacheKey = CacheDirectoryManager.cacheKeyForArtifact(outputArtifact);
    assertThat(relativeCopyDestination.toString())
        .isEqualTo(String.format("%s/%s", DefaultCacheLayout.PACKED_FILES_DIR, cacheKey));

    // Regular artifact behavior is unchanged
    OutputArtifactInfo simpleArtifact = testOutputArtifact("lib-class.jar");
    Path relativeSimpleCopyDestination =
        cacheDirectoryManager.cacheDirectory.relativize(
            cacheLayout.getOutputArtifactDestinationAndLayout(simpleArtifact).getCopyDestination());
    assertThat(relativeSimpleCopyDestination.toString())
        .isEqualTo(CacheDirectoryManager.cacheKeyForArtifact(simpleArtifact));
  }

  private static class CacheDirectories {
    public final Path cacheDirectory;
    public final Path cacheDotDirectory;

    private CacheDirectories(Path cacheDirectory, Path cacheDotDirectory) {
      this.cacheDirectory = cacheDirectory;
      this.cacheDotDirectory = cacheDotDirectory;
    }
  }

  private CacheDirectories createCacheDirectoryManager() {
    return new CacheDirectories(
        temporaryFolder.getRoot().toPath().resolve("cache"),
        temporaryFolder.getRoot().toPath().resolve(".cache"));
  }

  private static OutputArtifactInfo testOutputArtifact(String fileName) {
    return new OutputArtifactInfo() {
      @Override
      public String getRelativePath() {
        return "somewhere/" + fileName;
      }
    };
  }
}
