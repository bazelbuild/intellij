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
import com.google.idea.blaze.base.command.buildresult.OutputArtifact;
import com.google.idea.blaze.base.filecache.ArtifactState;
import com.google.idea.blaze.base.qsync.cache.FileCache.OutputArtifactDestination;
import java.io.BufferedInputStream;
import java.nio.file.Path;
import javax.annotation.Nullable;
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
    CacheDirectoryManager cacheDirectoryManager = createCacheDirectoryManager();
    DefaultCacheLayout cacheLayout =
        new DefaultCacheLayout(
            cacheDirectoryManager.cacheDirectory,
            cacheDirectoryManager.cacheDotDirectory,
            ImmutableSet.of(),
            ImmutableSet.of());

    OutputArtifact outputArtifact = testOutputArtifact("libfoo.jar");

    OutputArtifactDestination artifactDestination =
        cacheLayout.getOutputArtifactDestination(outputArtifact);
    Path relativeCopyDestination =
        cacheDirectoryManager.cacheDirectory.relativize(artifactDestination.getCopyDestination());

    String cacheKey = CacheDirectoryManager.cacheKeyForArtifact(outputArtifact.getKey());
    assertThat(relativeCopyDestination.toString()).isEqualTo(cacheKey);
  }

  @Test
  public void artifacts_with_directories() {
    CacheDirectoryManager cacheDirectoryManager = createCacheDirectoryManager();
    DefaultCacheLayout cacheLayout =
        new DefaultCacheLayout(
            cacheDirectoryManager.cacheDirectory,
            cacheDirectoryManager.cacheDotDirectory,
            ImmutableSet.of(),
            ImmutableSet.of("java"));

    // Artifact with dedicated directory
    OutputArtifact outputArtifact = testOutputArtifact("Class.java");
    Path relativeCopyDestination =
        cacheDirectoryManager.cacheDirectory.relativize(
            cacheLayout.getOutputArtifactDestination(outputArtifact).getCopyDestination());
    String cacheKey = CacheDirectoryManager.cacheKeyForArtifact(outputArtifact.getKey());
    assertThat(relativeCopyDestination.toString())
        .isEqualTo(String.format("%s/%s", cacheKey, cacheKey));

    // Regular artifact behavior is unchanged
    OutputArtifact simpleArtifact = testOutputArtifact("lib-class.jar");
    Path relativeSimpleCopyDestination =
        cacheDirectoryManager.cacheDirectory.relativize(
            cacheLayout.getOutputArtifactDestination(simpleArtifact).getCopyDestination());
    assertThat(relativeSimpleCopyDestination.toString())
        .isEqualTo(CacheDirectoryManager.cacheKeyForArtifact(simpleArtifact.getKey()));
  }

  @Test
  public void zipped_artifacts() {
    CacheDirectoryManager cacheDirectoryManager = createCacheDirectoryManager();
    DefaultCacheLayout cacheLayout =
        new DefaultCacheLayout(
            cacheDirectoryManager.cacheDirectory,
            cacheDirectoryManager.cacheDotDirectory,
            ImmutableSet.of("zip"),
            ImmutableSet.of());

    // Zipped artifact
    OutputArtifact outputArtifact = testOutputArtifact("archive.zip");
    Path relativeCopyDestination =
        cacheDirectoryManager.cacheDotDirectory.relativize(
            cacheLayout.getOutputArtifactDestination(outputArtifact).getCopyDestination());
    String cacheKey = CacheDirectoryManager.cacheKeyForArtifact(outputArtifact.getKey());
    assertThat(relativeCopyDestination.toString())
        .isEqualTo(String.format("%s/%s", DefaultCacheLayout.PACKED_FILES_DIR, cacheKey));

    // Regular artifact behavior is unchanged
    OutputArtifact simpleArtifact = testOutputArtifact("lib-class.jar");
    Path relativeSimpleCopyDestination =
        cacheDirectoryManager.cacheDirectory.relativize(
            cacheLayout.getOutputArtifactDestination(simpleArtifact).getCopyDestination());
    assertThat(relativeSimpleCopyDestination.toString())
        .isEqualTo(CacheDirectoryManager.cacheKeyForArtifact(simpleArtifact.getKey()));
  }

  private CacheDirectoryManager createCacheDirectoryManager() {
    return new CacheDirectoryManager(
        temporaryFolder.getRoot().toPath().resolve("cache"),
        temporaryFolder.getRoot().toPath().resolve(".cache"));
  }

  private static OutputArtifact testOutputArtifact(String fileName) {
    return new OutputArtifact() {
      @Override
      public String getConfigurationMnemonic() {
        return "mnemonic";
      }

      @Override
      public String getRelativePath() {
        return "somewhere/" + fileName;
      }

      @Nullable
      @Override
      public ArtifactState toArtifactState() {
        throw new UnsupportedOperationException();
      }

      @Override
      public String getDigest() {
        return fileName + "_digest";
      }

      @Override
      public long getLength() {
        return 0;
      }

      @Override
      public BufferedInputStream getInputStream() {
        throw new UnsupportedOperationException();
      }
    };
  }
}
