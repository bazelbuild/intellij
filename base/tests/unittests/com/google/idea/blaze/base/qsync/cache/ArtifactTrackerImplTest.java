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
import static com.google.common.truth.Truth8.assertThat;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.intellij.qsync.ArtifactTrackerData.BuildArtifacts;
import com.google.devtools.intellij.qsync.ArtifactTrackerData.TargetArtifacts;
import com.google.idea.blaze.base.command.buildresult.OutputArtifact;
import com.google.idea.blaze.base.filecache.ArtifactState;
import com.google.idea.blaze.base.qsync.ArtifactTracker.UpdateResult;
import com.google.idea.blaze.base.qsync.OutputInfo;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.common.Label;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
                        OutputInfo.builder()
                            .setJars(
                                artifactWithNameAndDigest("abc", "abc_digest"),
                                artifactWithNameAndDigest("klm", "klm_digest"))
                            .build(),
                        BlazeContext.create())))
        .containsExactly("somewhere/abc", "somewhere/klm");
    assertThat(
            artifactTracker.cacheDirectoryManager.getStoredArtifactDigest(
                artifactWithNameAndDigest("abc", "abc_digest_diff")))
        .isEqualTo("abc_digest");
    assertThat(
            artifactTracker.cacheDirectoryManager.getStoredArtifactDigest(
                artifactWithNameAndDigest("klm", "klm_digest")))
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
            OutputInfo.builder()
                .setJars(
                    artifactWithNameAndDigest("abc", "abc_digest"),
                    artifactWithNameAndDigest("klm", "klm_digest"))
                .build(),
            BlazeContext.create());

    testArtifactFetcher.shouldFail = true;
    try {
      final UpdateResult unused2 =
          artifactTracker.update(
              ImmutableSet.of(Label.of("//test:test2")),
              OutputInfo.builder()
                  .setJars(
                      artifactWithNameAndDigest("abc", "abc_digest"),
                      artifactWithNameAndDigest("klm", "klm_digest_diff"))
                  .build(),
              BlazeContext.create());
    } catch (TestException e) {
      // Do nothing.
    }

    assertThat(
            artifactTracker.cacheDirectoryManager.getStoredArtifactDigest(
                artifactWithNameAndDigest("abc", "abc_digest")))
        .isEqualTo("abc_digest"); // It shouldn't be copied since it hasn't changed.
    assertThat(
            artifactTracker.cacheDirectoryManager.getStoredArtifactDigest(
                artifactWithNameAndDigest("klm", "klm_digest_diff")))
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
                        OutputInfo.builder()
                            .setJars(
                                artifactWithNameAndDigest("abc", "abc_digest"),
                                artifactWithNameAndDigest("klm", "klm_digest"))
                            .build(),
                        BlazeContext.create())))
        .containsExactly("somewhere/abc", "somewhere/klm");

    // Second cache operation.
    assertThat(
            testArtifactFetcher.runAndCollectFetches(
                () ->
                    artifactTracker.update(
                        ImmutableSet.of(Label.of("//test:test2")),
                        OutputInfo.builder()
                            .setJars(
                                artifactWithNameAndDigest("abc", "abc_digest"),
                                artifactWithNameAndDigest("klm", "klm_digest_diff"),
                                artifactWithNameAndDigest("xyz", "xyz_digest"))
                            .build(),
                        BlazeContext.create())))
        .containsExactly("somewhere/klm", "somewhere/xyz");
  }

  @Test
  public void library_sources() throws Throwable {
    TestArtifactFetcher testArtifactFetcher = new TestArtifactFetcher();
    ArtifactTrackerImpl artifactTracker =
        new ArtifactTrackerImpl(
            temporaryFolder.getRoot().toPath(),
            temporaryFolder.getRoot().toPath().resolve("ide_project"),
            testArtifactFetcher);
    artifactTracker.initialize();

    final UpdateResult unused =
        artifactTracker.update(
            ImmutableSet.of(Label.of("//test:test"), Label.of("//test:anothertest")),
            OutputInfo.builder()
                .setJars(
                    TestOutputArtifact.builder()
                        .setRelativePath("out/test.jar")
                        .setDigest("jar_digest")
                        .build(),
                    TestOutputArtifact.builder()
                        .setRelativePath("out/anothertest.jar")
                        .setDigest("anotherjar_digest")
                        .build())
                .setArtifacts(
                    BuildArtifacts.newBuilder()
                        .addArtifacts(
                            TargetArtifacts.newBuilder()
                                .setTarget("//test:test")
                                .addJars("out/test.jar")
                                .addSrcs("test/Test.java")
                                .build())
                        .addArtifacts(
                            TargetArtifacts.newBuilder()
                                .setTarget("//test:anothertest")
                                .addJars("out/anothertest.jar")
                                .addSrcs("test/AnotherTest.java")
                                .build())
                        .build())
                .build(),
            BlazeContext.create());
    Optional<ImmutableSet<Path>> testArtifacts =
        artifactTracker.getCachedFiles(Label.of("//test:test"));
    assertThat(testArtifacts).isPresent();
    assertThat(testArtifacts.get()).hasSize(1);
    ImmutableSet<Path> testSources =
        artifactTracker.getTargetSources(testArtifacts.get().asList().get(0));
    assertThat(testSources).containsExactly(Path.of("test/Test.java"));
  }

  @Test
  public void library_sources_unbknown_lib() throws Throwable {
    TestArtifactFetcher testArtifactFetcher = new TestArtifactFetcher();
    ArtifactTrackerImpl artifactTracker =
        new ArtifactTrackerImpl(
            temporaryFolder.getRoot().toPath(),
            temporaryFolder.getRoot().toPath().resolve("ide_project"),
            testArtifactFetcher);
    artifactTracker.initialize();

    final UpdateResult unused =
        artifactTracker.update(
            ImmutableSet.of(Label.of("//test:test"), Label.of("//test:anothertest")),
            OutputInfo.builder()
                .setJars(
                    TestOutputArtifact.builder()
                        .setRelativePath("out/test.jar")
                        .setDigest("jar_digest")
                        .build())
                .setArtifacts(
                    BuildArtifacts.newBuilder()
                        .addArtifacts(
                            TargetArtifacts.newBuilder()
                                .setTarget("//test:test")
                                .addJars("out/test.jar")
                                .addSrcs("test/Test.java")
                                .build())
                        .build())
                .build(),
            BlazeContext.create());
    ImmutableSet<Path> testSources =
        artifactTracker.getTargetSources(Path.of("some/unknown/file.jar"));
    assertThat(testSources).isEmpty();
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

  @AutoValue
  abstract static class TestOutputArtifact implements OutputArtifact {

    public static final TestOutputArtifact EMPTY =
        new AutoValue_ArtifactTrackerImplTest_TestOutputArtifact.Builder()
            .setLength(0)
            .setDigest("digest")
            .setRelativePath("path/file")
            .setConfigurationMnemonic("mnemonic")
            .build();

    public static Builder builder() {
      return EMPTY.toBuilder();
    }

    @Override
    public abstract long getLength();

    @Override
    public BufferedInputStream getInputStream() throws IOException {
      throw new UnsupportedOperationException();
    }

    @Override
    public abstract String getDigest();

    @Override
    public abstract String getRelativePath();

    @Override
    public abstract String getConfigurationMnemonic();

    @Nullable
    @Override
    public ArtifactState toArtifactState() {
      throw new UnsupportedOperationException();
    }

    public abstract Builder toBuilder();

    @AutoValue.Builder
    abstract static class Builder {

      public abstract Builder setLength(long value);

      public abstract Builder setDigest(String value);

      public abstract Builder setRelativePath(String value);

      public abstract Builder setConfigurationMnemonic(String value);

      public abstract TestOutputArtifact build();
    }
  }

  private static OutputArtifact artifactWithNameAndDigest(String fileName, String digest) {
    return TestOutputArtifact.builder()
        .setRelativePath("somewhere/" + fileName)
        .setDigest(digest)
        .build();
  }
}
