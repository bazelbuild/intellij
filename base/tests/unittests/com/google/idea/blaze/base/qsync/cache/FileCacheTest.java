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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.command.buildresult.OutputArtifact;
import com.google.idea.blaze.base.filecache.ArtifactState;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.common.Context;
import java.io.BufferedInputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;
import org.junit.Rule;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class FileCacheTest {

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void metadata_are_preserved() throws Throwable {
    CacheDirectoryManager cacheDirectoryManager = createCacheDirectoryManager();
    TestArtifactFetcher testArtifactFetcher = new TestArtifactFetcher();
    FileCache fileCache =
        new FileCache(
            testArtifactFetcher,
            cacheDirectoryManager,
            new DefaultCacheLayout(
                cacheDirectoryManager.cacheDirectory,
                cacheDirectoryManager.cacheDotDirectory,
                ImmutableSet.of()));
    fileCache.initialize();

    assertThat(
            testArtifactFetcher.runAndCollectFetches(
                () ->
                    fileCache
                        .cache(
                            ImmutableList.of(
                                testOutputArtifact("abc", "abc_digest"),
                                testOutputArtifact("klm", "klm_digest")),
                            BlazeContext.create())
                        .get()))
        .containsExactly("somewhere/abc", "somewhere/klm");
    assertThat(
            cacheDirectoryManager.getStoredArtifactDigest(
                testOutputArtifact("abc", "abc_digest_diff")))
        .isEqualTo("abc_digest");
    assertThat(
            cacheDirectoryManager.getStoredArtifactDigest(testOutputArtifact("klm", "klm_digest")))
        .isEqualTo("klm_digest");
  }

  /**
   * When fetching fails we do not know the state in which the file system has been left. Therefore,
   * we need to enforce fetching next time. We do not delete artifact files themselves as this might
   * break symbol resolution even further.
   */
  @Test
  public void failed_fetch_resets_metadata() throws Exception {
    CacheDirectoryManager cacheDirectoryManager = createCacheDirectoryManager();
    FailingArtifactFetcher testArtifactFetcher = new FailingArtifactFetcher();
    FileCache fileCache =
        new FileCache(
            testArtifactFetcher,
            cacheDirectoryManager,
            new DefaultCacheLayout(
                cacheDirectoryManager.cacheDirectory,
                cacheDirectoryManager.cacheDotDirectory,
                ImmutableSet.of()));
    fileCache.initialize();

    fileCache
        .cache(
            ImmutableList.of(
                testOutputArtifact("abc", "abc_digest"), testOutputArtifact("klm", "klm_digest")),
            BlazeContext.create())
        .get();

    testArtifactFetcher.shouldFail = true;
    try {
      fileCache
          .cache(
              ImmutableList.of(
                  testOutputArtifact("abc", "abc_digest"),
                  testOutputArtifact("klm", "klm_digest_diff")),
              BlazeContext.create())
          .get();
    } catch (ExecutionException e) {
      if (!(e.getCause() instanceof TestException)) {
        throw e;
      }
      // Do nothing.
    }

    assertThat(
            cacheDirectoryManager.getStoredArtifactDigest(testOutputArtifact("abc", "abc_digest")))
        .isEqualTo("abc_digest"); // It shouldn't be copied since it hasn't changed.
    assertThat(
            cacheDirectoryManager.getStoredArtifactDigest(
                testOutputArtifact("klm", "klm_digest_diff")))
        .isEmpty(); // It was supposed to be copied but failed.
  }

  @Test
  public void artifacts_fecthed_once() throws Throwable {
    CacheDirectoryManager cacheDirectoryManager = createCacheDirectoryManager();
    TestArtifactFetcher testArtifactFetcher = new TestArtifactFetcher();
    FileCache fileCache =
        new FileCache(
            testArtifactFetcher,
            cacheDirectoryManager,
            new DefaultCacheLayout(
                cacheDirectoryManager.cacheDirectory,
                cacheDirectoryManager.cacheDotDirectory,
                ImmutableSet.of()));
    fileCache.initialize();

    assertThat(
            testArtifactFetcher.runAndCollectFetches(
                () ->
                    fileCache
                        .cache(
                            ImmutableList.of(
                                testOutputArtifact("abc", "abc_digest"),
                                testOutputArtifact("klm", "klm_digest")),
                            BlazeContext.create())
                        .get()))
        .containsExactly("somewhere/abc", "somewhere/klm");

    // Second cache operation.
    assertThat(
            testArtifactFetcher.runAndCollectFetches(
                () ->
                    fileCache
                        .cache(
                            ImmutableList.of(
                                testOutputArtifact("abc", "abc_digest"),
                                testOutputArtifact("klm", "klm_digest_diff"),
                                testOutputArtifact("xyz", "xyz_digest")),
                            BlazeContext.create())
                        .get()))
        .containsExactly("somewhere/klm", "somewhere/xyz");
  }

  private CacheDirectoryManager createCacheDirectoryManager() {
    return new CacheDirectoryManager(
        temporaryFolder.getRoot().toPath().resolve("cache"),
        temporaryFolder.getRoot().toPath().resolve(".cache"));
  }

  private static class TestArtifactFetcher implements ArtifactFetcher<OutputArtifact> {

    private List<String> collectedArtifactKeyToMetadata = new ArrayList<>();

    public ImmutableList<String> runAndCollectFetches(ThrowingRunnable runnable) throws Throwable {
      try {
        runnable.run();
        return ImmutableList.copyOf(collectedArtifactKeyToMetadata);
      } finally {
        collectedArtifactKeyToMetadata.clear();
      }
    }

    @Override
    public Class<OutputArtifact> supportedArtifactType() {
      return OutputArtifact.class;
    }

    @Override
    public ListenableFuture<List<Path>> copy(
        ImmutableMap<? extends OutputArtifact, ArtifactDestination> artifactToDest,
        Context<?> context) {
      return Futures.immediateFuture(
          ImmutableList.copyOf(
              Maps.transformEntries(
                      artifactToDest,
                      (k, v) -> {
                        collectedArtifactKeyToMetadata.add(k.getKey());
                        return v.path;
                      })
                  .values()));
    }
  }

  private static class TestException extends RuntimeException {}

  private static class FailingArtifactFetcher implements ArtifactFetcher<OutputArtifact> {
    public boolean shouldFail = false;

    @Override
    public Class<OutputArtifact> supportedArtifactType() {
      return OutputArtifact.class;
    }

    @Override
    public ListenableFuture<List<Path>> copy(
        ImmutableMap<? extends OutputArtifact, ArtifactDestination> artifactToDest,
        Context<?> context) {
      if (shouldFail) {
        throw new TestException();
      }
      return Futures.immediateFuture(
          artifactToDest.values().stream()
              .map(it -> it.path)
              .collect(ImmutableList.toImmutableList()));
    }
  }

  private static OutputArtifact testOutputArtifact(String fileName, String digest) {
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
        return digest;
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
