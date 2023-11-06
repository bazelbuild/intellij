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
import static one.util.streamex.MoreCollectors.onlyOne;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.intellij.qsync.ArtifactTrackerData.JavaArtifacts;
import com.google.devtools.intellij.qsync.ArtifactTrackerData.JavaTargetArtifacts;
import com.google.idea.blaze.base.command.buildresult.OutputArtifact;
import com.google.idea.blaze.base.filecache.ArtifactState;
import com.google.idea.blaze.base.qsync.AppInspectorInfo;
import com.google.idea.blaze.base.qsync.ArtifactTrackerUpdateResult;
import com.google.idea.blaze.base.qsync.GroupedOutputArtifacts;
import com.google.idea.blaze.base.qsync.OutputGroup;
import com.google.idea.blaze.base.qsync.OutputInfo;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.qsync.project.ProjectDefinition;
import com.google.idea.blaze.qsync.project.ProjectPath.Resolver;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
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
            testArtifactFetcher,
            Resolver.EMPTY_FOR_TESTING,
            ProjectDefinition.EMPTY);

    artifactTracker.initialize();

    assertThat(
            testArtifactFetcher
                .runAndCollectFetches(
                    () ->
                        artifactTracker.update(
                            ImmutableSet.of(Label.of("//test:test")),
                            OutputInfo.builder()
                                .setOutputGroups(
                                    GroupedOutputArtifacts.builder()
                                        .putAll(
                                            OutputGroup.JARS,
                                            artifactWithNameAndDigest("abc", "abc_digest"),
                                            artifactWithNameAndDigest("klm", "klm_digest"))
                                        .build())
                                .build(),
                            BlazeContext.create()))
                .keySet())
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
            testArtifactFetcher,
            Resolver.EMPTY_FOR_TESTING,
            ProjectDefinition.EMPTY);
    artifactTracker.initialize();

    final ArtifactTrackerUpdateResult unused =
        artifactTracker.update(
            ImmutableSet.of(Label.of("//test:test")),
            OutputInfo.builder()
                .setOutputGroups(
                    GroupedOutputArtifacts.builder()
                        .putAll(
                            OutputGroup.JARS,
                            artifactWithNameAndDigest("abc", "abc_digest"),
                            artifactWithNameAndDigest("klm", "klm_digest"))
                        .build())
                .build(),
            BlazeContext.create());

    testArtifactFetcher.shouldFail = true;
    try {
      final ArtifactTrackerUpdateResult unused2 =
          artifactTracker.update(
              ImmutableSet.of(Label.of("//test:test2")),
              OutputInfo.builder()
                  .setOutputGroups(
                      GroupedOutputArtifacts.builder()
                          .putAll(
                              OutputGroup.JARS,
                              artifactWithNameAndDigest("abc", "abc_digest"),
                              artifactWithNameAndDigest("klm", "klm_digest_diff"))
                          .build())
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
            testArtifactFetcher,
            Resolver.EMPTY_FOR_TESTING,
            ProjectDefinition.EMPTY);
    artifactTracker.initialize();

    assertThat(
            testArtifactFetcher
                .runAndCollectFetches(
                    () ->
                        artifactTracker.update(
                            ImmutableSet.of(Label.of("//test:test")),
                            OutputInfo.builder()
                                .setOutputGroups(
                                    GroupedOutputArtifacts.builder()
                                        .putAll(
                                            OutputGroup.JARS,
                                            artifactWithNameAndDigest("abc", "abc_digest"),
                                            artifactWithNameAndDigest("klm", "klm_digest"))
                                        .build())
                                .build(),
                            BlazeContext.create()))
                .keySet())
        .containsExactly("somewhere/abc", "somewhere/klm");

    // Second cache operation.
    assertThat(
            testArtifactFetcher
                .runAndCollectFetches(
                    () ->
                        artifactTracker.update(
                            ImmutableSet.of(Label.of("//test:test2")),
                            OutputInfo.builder()
                                .setOutputGroups(
                                    GroupedOutputArtifacts.builder()
                                        .putAll(
                                            OutputGroup.JARS,
                                            artifactWithNameAndDigest("abc", "abc_digest"),
                                            artifactWithNameAndDigest("klm", "klm_digest_diff"),
                                            artifactWithNameAndDigest("xyz", "xyz_digest"))
                                        .build())
                                .build(),
                            BlazeContext.create()))
                .keySet())
        .containsExactly("somewhere/klm", "somewhere/xyz");
  }

  @Test
  public void library_sources() throws Throwable {
    TestArtifactFetcher testArtifactFetcher = new TestArtifactFetcher();
    ArtifactTrackerImpl artifactTracker =
        new ArtifactTrackerImpl(
            temporaryFolder.getRoot().toPath(),
            temporaryFolder.getRoot().toPath().resolve("ide_project"),
            testArtifactFetcher,
            Resolver.EMPTY_FOR_TESTING,
            ProjectDefinition.EMPTY);
    artifactTracker.initialize();

    final ArtifactTrackerUpdateResult unused =
        artifactTracker.update(
            ImmutableSet.of(Label.of("//test:test"), Label.of("//test:anothertest")),
            OutputInfo.builder()
                .setOutputGroups(
                    GroupedOutputArtifacts.builder()
                        .putAll(
                            OutputGroup.JARS,
                            TestOutputArtifact.builder()
                                .setRelativePath("out/test.jar")
                                .setDigest("jar_digest")
                                .build(),
                            TestOutputArtifact.builder()
                                .setRelativePath("out/anothertest.jar")
                                .setDigest("anotherjar_digest")
                                .build())
                        .build())
                .setArtifactInfo(
                    JavaArtifacts.newBuilder()
                        .addArtifacts(
                            JavaTargetArtifacts.newBuilder()
                                .setTarget("//test:test")
                                .addJars("out/test.jar")
                                .addSrcs("test/Test.java")
                                .build())
                        .addArtifacts(
                            JavaTargetArtifacts.newBuilder()
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
  public void library_sources_unknown_lib() throws Throwable {
    TestArtifactFetcher testArtifactFetcher = new TestArtifactFetcher();
    ArtifactTrackerImpl artifactTracker =
        new ArtifactTrackerImpl(
            temporaryFolder.getRoot().toPath(),
            temporaryFolder.getRoot().toPath().resolve("ide_project"),
            testArtifactFetcher,
            Resolver.EMPTY_FOR_TESTING,
            ProjectDefinition.EMPTY);
    artifactTracker.initialize();

    final ArtifactTrackerUpdateResult unused =
        artifactTracker.update(
            ImmutableSet.of(Label.of("//test:test"), Label.of("//test:anothertest")),
            OutputInfo.builder()
                .setOutputGroups(
                    GroupedOutputArtifacts.builder()
                        .put(
                            OutputGroup.JARS,
                            TestOutputArtifact.builder()
                                .setRelativePath("out/test.jar")
                                .setDigest("jar_digest")
                                .build())
                        .build())
                .setArtifactInfo(
                    JavaArtifacts.newBuilder()
                        .addArtifacts(
                            JavaTargetArtifacts.newBuilder()
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

  @Test
  public void generated_headers() throws Throwable {
    TestArtifactFetcher testArtifactFetcher = new TestArtifactFetcher();
    ArtifactTrackerImpl artifactTracker =
        new ArtifactTrackerImpl(
            temporaryFolder.getRoot().toPath(),
            temporaryFolder.getRoot().toPath().resolve("ide_project"),
            testArtifactFetcher,
            Resolver.EMPTY_FOR_TESTING,
            ProjectDefinition.EMPTY);
    artifactTracker.initialize();

    assertThat(
            testArtifactFetcher
                .runAndCollectFetches(
                    () ->
                        artifactTracker.update(
                            ImmutableSet.of(
                                Label.of("//test:test"), Label.of("//test:anothertest")),
                            OutputInfo.builder()
                                .setOutputGroups(
                                    GroupedOutputArtifacts.builder()
                                        .putAll(
                                            OutputGroup.CC_HEADERS,
                                            TestOutputArtifact.builder()
                                                .setRelativePath("build-out/path/to/header.h")
                                                .setDigest("header_digest")
                                                .build(),
                                            TestOutputArtifact.builder()
                                                .setRelativePath(
                                                    "build-out/path/to/another/header.h")
                                                .setDigest("anotherheader_digest")
                                                .build())
                                        .build())
                                .build(),
                            BlazeContext.create()))
                .values()
                .stream()
                .map(artifactTracker.generatedHeadersDirectory::relativize))
        .containsExactly(
            Path.of("build-out/path/to/header.h"), Path.of("build-out/path/to/another/header.h"));
  }

  @Test
  public void update_appInspectorArtifacts() throws Throwable {
    TestArtifactFetcher testArtifactFetcher = new TestArtifactFetcher();
    Path root = temporaryFolder.getRoot().toPath();
    ArtifactTrackerImpl artifactTracker =
        new ArtifactTrackerImpl(
            root,
            root.resolve("ide_project"),
            testArtifactFetcher,
            Resolver.EMPTY_FOR_TESTING,
            ProjectDefinition.EMPTY);
    artifactTracker.initialize();

    ImmutableSet<Path> update =
        artifactTracker.update(
            ImmutableSet.of(Label.of("//app/inspector:inspector")),
            AppInspectorInfo.create(
                ImmutableList.of(artifactWithNameAndDigest("inspector.jar", "digest")), 0),
            BlazeContext.create());
    assertThat(update).hasSize(1);
    assertThat(update.stream().collect(onlyOne()).orElseThrow().toString())
        .startsWith(root.resolve("app_inspectors").toString());
  }

  @Test
  public void update_appInspectorArtifacts_notChanged() throws Throwable {
    TestArtifactFetcher testArtifactFetcher = new TestArtifactFetcher();
    Path root = temporaryFolder.getRoot().toPath();
    ArtifactTrackerImpl artifactTracker =
        new ArtifactTrackerImpl(
            root,
            root.resolve("ide_project"),
            testArtifactFetcher,
            Resolver.EMPTY_FOR_TESTING,
            ProjectDefinition.EMPTY);
    artifactTracker.initialize();

    ImmutableSet<Path> unused =
        artifactTracker.update(
            ImmutableSet.of(Label.of("//app/inspector:inspector")),
            AppInspectorInfo.create(
                ImmutableList.of(artifactWithNameAndDigest("inspector.jar", "digest")), 0),
            BlazeContext.create());
    ImmutableSet<Path> update =
        artifactTracker.update(
            ImmutableSet.of(Label.of("//app/inspector:inspector")),
            AppInspectorInfo.create(
                ImmutableList.of(artifactWithNameAndDigest("inspector.jar", "digest")), 0),
            BlazeContext.create());
    assertThat(update).hasSize(1);
  }

  private static class TestArtifactFetcher implements ArtifactFetcher<OutputArtifact> {

    private final Map<String, Path> collectedArtifactOriginToDestPathPap = Maps.newHashMap();

    public Map<String, Path> runAndCollectFetches(ThrowingRunnable runnable) throws Throwable {
      try {
        runnable.run();
        return ImmutableMap.copyOf(collectedArtifactOriginToDestPathPap);
      } finally {
        collectedArtifactOriginToDestPathPap.clear();
      }
    }

    @Override
    public Class<OutputArtifact> supportedArtifactType() {
      return OutputArtifact.class;
    }

    @Override
    public ListenableFuture<?> copy(
        ImmutableMap<? extends OutputArtifact, ArtifactDestination> artifactToDest,
        Context<?> context) {
      artifactToDest
          .entrySet()
          .forEach(
              e ->
                  collectedArtifactOriginToDestPathPap.put(
                      e.getKey().getRelativePath(), e.getValue().path));
      return Futures.immediateFuture(null);
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
    public ListenableFuture<?> copy(
        ImmutableMap<? extends OutputArtifact, ArtifactDestination> artifactToDest,
        Context<?> context) {
      if (shouldFail) {
        throw new TestException();
      }
      return Futures.immediateFuture(null);
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
