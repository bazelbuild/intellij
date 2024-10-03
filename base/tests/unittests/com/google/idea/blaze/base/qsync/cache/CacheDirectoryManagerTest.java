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

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.common.artifact.ArtifactState;
import com.google.idea.blaze.common.artifact.OutputArtifact;
import java.io.BufferedInputStream;
import java.io.IOException;
import javax.annotation.Nullable;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CacheDirectoryManagerTest {

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void initialize() throws IOException {
    CacheDirectoryManager cacheDirectoryManager = createCacheDirectoryManager();
    cacheDirectoryManager.initialize();
  }

  @Test
  public void clear() throws IOException {
    CacheDirectoryManager cacheDirectoryManager = createCacheDirectoryManager();
    cacheDirectoryManager.initialize();
    cacheDirectoryManager.clear();
  }

  @Test
  public void initialize_existing() {
    CacheDirectoryManager cacheDirectoryManager1 = createCacheDirectoryManager();
    cacheDirectoryManager1.initialize();

    CacheDirectoryManager cacheDirectoryManager2 = createCacheDirectoryManager();
    cacheDirectoryManager2.initialize();
  }

  @Test
  public void read_nonexistent_medatadata() {
    CacheDirectoryManager cacheDirectoryManager = createCacheDirectoryManager();
    cacheDirectoryManager.initialize();

    assertThat(cacheDirectoryManager.getStoredArtifactDigest(testOutputArtifact("123"))).isEmpty();
  }

  @Test
  public void set_and_read_medatadata() {
    CacheDirectoryManager cacheDirectoryManager = createCacheDirectoryManager();
    cacheDirectoryManager.initialize();
    cacheDirectoryManager.setStoredArtifactDigest(testOutputArtifact("123"), "abc");

    assertThat(cacheDirectoryManager.getStoredArtifactDigest(testOutputArtifact("123")))
        .isEqualTo("abc");
  }

  @Test
  public void set_set_and_read_medatadata() {
    CacheDirectoryManager cacheDirectoryManager = createCacheDirectoryManager();
    cacheDirectoryManager.initialize();
    cacheDirectoryManager.setStoredArtifactDigest(testOutputArtifact("123"), "abc");
    cacheDirectoryManager.setStoredArtifactDigest(testOutputArtifact("123"), "xyz");

    assertThat(cacheDirectoryManager.getStoredArtifactDigest(testOutputArtifact("123")))
        .isEqualTo("xyz");
  }

  @Test
  public void set_clear_and_read_medatadata() throws IOException {
    CacheDirectoryManager cacheDirectoryManager = createCacheDirectoryManager();
    cacheDirectoryManager.initialize();
    cacheDirectoryManager.setStoredArtifactDigest(testOutputArtifact("123"), "abc");
    cacheDirectoryManager.clear();

    assertThat(cacheDirectoryManager.getStoredArtifactDigest(testOutputArtifact("123"))).isEmpty();
  }

  private CacheDirectoryManager createCacheDirectoryManager() {
    return new CacheDirectoryManager(
        temporaryFolder.getRoot().toPath().resolve(".digest"),
        ImmutableList.of(
            temporaryFolder.getRoot().toPath().resolve("cache"),
            temporaryFolder.getRoot().toPath().resolve(".cache")));
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
        throw new UnsupportedOperationException();
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
