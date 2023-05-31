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
import com.google.idea.blaze.base.qsync.ArtifactTracker.UpdateResult;
import com.google.idea.blaze.base.qsync.OutputInfo;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.common.Label;
import java.io.BufferedInputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.junit.Rule;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ArtifactTrackerImplTest {

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void metadata_are_preserved() throws Throwable {
    TestArtifactFetcher testArtifactFetcher = new TestArtifactFetcher();
    ArtifactTrackerImpl artifactTracker =
        new ArtifactTrackerImpl(
            temporaryFolder.getRoot().toPath(),
            temporaryFolder.getRoot().toPath().resolve("ide_project"),
            testArtifactFetcher);
    artifactTracker.initialize();

    assertThat(
            testArtifactFetcher.runAndCollectFetches(
                () ->
                    artifactTracker.update(
                        ImmutableSet.of(Label.of("//test:test")),
                        createOutputInfo(
                            ImmutableList.of(
                                testOutputArtifact("abc", "abc_digest"),
                                testOutputArtifact("klm", "klm_digest"))),
                        BlazeContext.create())))
        .containsExactly("somewhere/abc", "somewhere/klm");
    assertThat(
            artifactTracker.cacheDirectoryManager.getStoredArtifactDigest(
                testOutputArtifact("abc", "abc_digest_diff")))
        .isEqualTo("abc_digest");
    assertThat(
            artifactTracker.cacheDirectoryManager.getStoredArtifactDigest(
                testOutputArtifact("klm", "klm_digest")))
        .isEqualTo("klm_digest");
  }

  /**
   * When fetching fails we do not know the state in which the file system has been left. Therefore,
   * we need to enforce fetching next time. We do not delete artifact files themselves as this might
   * break symbol resolution even further.
   */
  @Test
  public void failed_fetch_resets_metadata() throws Exception {
    FailingArtifactFetcher testArtifactFetcher = new FailingArtifactFetcher();
    ArtifactTrackerImpl artifactTracker =
        new ArtifactTrackerImpl(
            temporaryFolder.getRoot().toPath(),
            temporaryFolder.getRoot().toPath().resolve("ide_project"),
            testArtifactFetcher);
    artifactTracker.initialize();

    final UpdateResult unused =
        artifactTracker.update(
            ImmutableSet.of(Label.of("//test:test")),
            createOutputInfo(
                ImmutableList.of(
                    testOutputArtifact("abc", "abc_digest"),
                    testOutputArtifact("klm", "klm_digest"))),
            BlazeContext.create());

    testArtifactFetcher.shouldFail = true;
    try {
      final UpdateResult unused2 =
          artifactTracker.update(
              ImmutableSet.of(Label.of("//test:test2")),
              createOutputInfo(
                  ImmutableList.of(
                      testOutputArtifact("abc", "abc_digest"),
                      testOutputArtifact("klm", "klm_digest_diff"))),
              BlazeContext.create());
    } catch (TestException e) {
      // Do nothing.
    }

    assertThat(
            artifactTracker.cacheDirectoryManager.getStoredArtifactDigest(
                testOutputArtifact("abc", "abc_digest")))
        .isEqualTo("abc_digest"); // It shouldn't be copied since it hasn't changed.
    assertThat(
            artifactTracker.cacheDirectoryManager.getStoredArtifactDigest(
                testOutputArtifact("klm", "klm_digest_diff")))
        .isEmpty(); // It was supposed to be copied but failed.
  }

  @Test
  public void artifacts_fecthed_once() throws Throwable {
    TestArtifactFetcher testArtifactFetcher = new TestArtifactFetcher();
    ArtifactTrackerImpl artifactTracker =
        new ArtifactTrackerImpl(
            temporaryFolder.getRoot().toPath(),
            temporaryFolder.getRoot().toPath().resolve("ide_project"),
            testArtifactFetcher);
    artifactTracker.initialize();

    assertThat(
            testArtifactFetcher.runAndCollectFetches(
                () ->
                    artifactTracker.update(
                        ImmutableSet.of(Label.of("//test:test")),
                        createOutputInfo(
                            ImmutableList.of(
                                testOutputArtifact("abc", "abc_digest"),
                                testOutputArtifact("klm", "klm_digest"))),
                        BlazeContext.create())))
        .containsExactly("somewhere/abc", "somewhere/klm");

    // Second cache operation.
    assertThat(
            testArtifactFetcher.runAndCollectFetches(
                () ->
                    artifactTracker.update(
                        ImmutableSet.of(Label.of("//test:test2")),
                        createOutputInfo(
                            ImmutableList.of(
                                testOutputArtifact("abc", "abc_digest"),
                                testOutputArtifact("klm", "klm_digest_diff"),
                                testOutputArtifact("xyz", "xyz_digest"))),
                        BlazeContext.create())))
        .containsExactly("somewhere/klm", "somewhere/xyz");
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

  private static OutputInfo createOutputInfo(ImmutableList<OutputArtifact> outputArtifacts) {
    return OutputInfo.create(
        ImmutableSet.of(),
        outputArtifacts,
        ImmutableList.of(),
        ImmutableList.of(),
        ImmutableSet.of(),
        0);
  }
}
