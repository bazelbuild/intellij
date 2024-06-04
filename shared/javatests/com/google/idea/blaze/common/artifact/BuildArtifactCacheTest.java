/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.common.artifact;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteSource;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.common.NoopContext;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Future;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class BuildArtifactCacheTest {
  @Rule public TemporaryFolder cacheDir = new TemporaryFolder();

  private final TestArtifactFetcher artifactFetcher = new TestArtifactFetcher();
  private BuildArtifactCacheDirectory cache;

  @Before
  public void createCache() throws Exception {
    cache =
        new BuildArtifactCacheDirectory(
            cacheDir.getRoot().toPath(), artifactFetcher, newDirectExecutorService());
  }

  @After
  public void checkLocks() {
    artifactFetcher.flushTasks();
    assertThat(cache.readLockCount()).isEqualTo(0);
    assertThat(cache.writeLockCount()).isEqualTo(0);
  }

  @Test
  public void basic_fetch() throws Exception {
    OutputArtifact artifact = TestOutputArtifact.forDigest("abc");
    ListenableFuture<?> fetch = cache.addAll(ImmutableList.of(artifact), new NoopContext());
    assertThat(fetch.isDone()).isFalse();
    assertThat(cache.get(artifact.getDigest()).map(Future::isDone)).hasValue(false);

    artifactFetcher.executePendingTasks();

    assertThat(fetch.isDone()).isTrue();
    assertThat(cache.get(artifact.getDigest()).map(Future::isDone)).hasValue(true);

    assertThat(Futures.getDone(cache.get(artifact.getDigest()).get()).asCharSource(UTF_8).read())
        .isEqualTo(artifactFetcher.getExpectedArtifactContents("abc"));
  }

  @Test
  public void get_already_present() throws Exception {
    OutputArtifact artifact = TestOutputArtifact.forDigest("abc");
    ListenableFuture<?> unused = cache.addAll(ImmutableList.of(artifact), new NoopContext());
    artifactFetcher.executePendingTasks();
    assertThat(artifactFetcher.takeRequestedDigests()).containsExactly("abc");

    unused = cache.addAll(ImmutableList.of(artifact), new NoopContext());
    assertThat(cache.get(artifact.getDigest())).isPresent();
    assertThat(cache.get(artifact.getDigest()).get().isDone()).isTrue();
    artifactFetcher.executePendingTasks();
    assertThat(artifactFetcher.takeRequestedDigests()).isEmpty();
  }

  @Test
  public void get_multiple() throws Exception {
    OutputArtifact artifact1 = TestOutputArtifact.forDigest("a1digest");
    OutputArtifact artifact2 = TestOutputArtifact.forDigest("a2digest");
    ListenableFuture<?> fetch1 = cache.addAll(ImmutableList.of(artifact1), new NoopContext());
    ListenableFuture<?> fetch2 = cache.addAll(ImmutableList.of(artifact2), new NoopContext());

    assertThat(fetch1.isDone()).isFalse();
    assertThat(fetch2.isDone()).isFalse();
    assertThat(cache.get("a1digest").map(Future::isDone)).hasValue(false);
    assertThat(cache.get("a2digest").map(Future::isDone)).hasValue(false);

    artifactFetcher.executePendingTasks();
    assertThat(artifactFetcher.takeRequestedDigests()).containsExactly("a1digest", "a2digest");

    assertThat(fetch1.isDone()).isTrue();
    assertThat(fetch2.isDone()).isTrue();
    assertThat(cache.get("a1digest").map(Future::isDone)).hasValue(true);
    assertThat(cache.get("a2digest").map(Future::isDone)).hasValue(true);

    assertThat(Futures.getDone(cache.get("a1digest").get()).asCharSource(UTF_8).read())
        .isEqualTo(artifactFetcher.getExpectedArtifactContents("a1digest"));
    assertThat(Futures.getDone(cache.get("a2digest").get()).asCharSource(UTF_8).read())
        .isEqualTo(artifactFetcher.getExpectedArtifactContents("a2digest"));
  }

  @Test
  public void get_multiple_partial_completion() throws Exception {
    OutputArtifact artifact1 = TestOutputArtifact.forDigest("abc");
    OutputArtifact artifact2 = TestOutputArtifact.forDigest("def");
    ListenableFuture<?> fetch1 = cache.addAll(ImmutableList.of(artifact1), new NoopContext());
    ListenableFuture<?> fetch2 = cache.addAll(ImmutableList.of(artifact2), new NoopContext());

    artifactFetcher.executeOldestTask();
    assertThat(artifactFetcher.getCompletedDigests()).containsExactly("abc");

    assertThat(fetch1.isDone()).isTrue();
    assertThat(fetch2.isDone()).isFalse();

    assertThat(cache.get("abc").map(Future::isDone)).hasValue(true);
    assertThat(cache.get("def").map(Future::isDone)).hasValue(false);
    assertThat(Futures.getDone(cache.get("abc").get()).asCharSource(UTF_8).read())
        .isEqualTo(artifactFetcher.getExpectedArtifactContents("abc"));
  }

  @Test
  public void get_multiple_out_of_order_completion() throws Exception {
    OutputArtifact artifact1 = TestOutputArtifact.forDigest("abc");
    OutputArtifact artifact2 = TestOutputArtifact.forDigest("def");
    ListenableFuture<?> fetch1 = cache.addAll(ImmutableList.of(artifact1), new NoopContext());
    ListenableFuture<?> fetch2 = cache.addAll(ImmutableList.of(artifact2), new NoopContext());

    artifactFetcher.executeNewestTask();
    assertThat(artifactFetcher.getCompletedDigests()).containsExactly("def");

    assertThat(fetch1.isDone()).isFalse();
    assertThat(fetch2.isDone()).isTrue();

    assertThat(cache.get("abc").map(Future::isDone)).hasValue(false);
    assertThat(cache.get("def").map(Future::isDone)).hasValue(true);
    assertThat(Futures.getDone(cache.get("def").get()).asCharSource(UTF_8).read())
        .isEqualTo(artifactFetcher.getExpectedArtifactContents("def"));
  }

  private static InputStream fileOfSize(int size) throws IOException {
    return ByteSource.wrap(new byte[size]).openStream();
  }

  @Test
  public void clean_verify_test_functionality() throws Exception {
    Instant now = Instant.now();
    cache.insertForTest(fileOfSize(10), "a", now.minus(Duration.ofMinutes(1)));
    cache.insertForTest(fileOfSize(10), "b", now.minus(Duration.ofMinutes(2)));
    cache.insertForTest(fileOfSize(10), "c", now.minus(Duration.ofMinutes(3)));
    cache.insertForTest(fileOfSize(10), "d", now.minus(Duration.ofMinutes(4)));

    assertThat(cache.listDigests()).containsExactly("a", "b", "c", "d");
    assertThat(cache.readAccessTime("a"))
        .isEquivalentAccordingToCompareTo(FileTime.from(now.minus(Duration.ofMinutes(1))));
    assertThat(Files.size(cache.artifactPath("a"))).isEqualTo(10);
  }

  @Test
  public void clean_target_size_reached() throws Exception {
    Instant now = Instant.now();

    cache.insertForTest(fileOfSize(10), "a", now.minus(Duration.ofMinutes(10)));
    cache.insertForTest(fileOfSize(10), "b", now.minus(Duration.ofMinutes(20)));
    cache.insertForTest(fileOfSize(10), "c", now.minus(Duration.ofMinutes(30)));
    cache.insertForTest(fileOfSize(10), "d", now.minus(Duration.ofMinutes(40)));
    assertThat(cache.listDigests()).containsExactly("a", "b", "c", "d");

    cache.clean(20, now.minus(Duration.ofMinutes(5)));

    assertThat(cache.listDigests()).containsExactly("a", "b");
  }

  @Test
  public void clean_min_age_reached() throws Exception {
    Instant now = Instant.now();
    cache.insertForTest(fileOfSize(10), "a", now.minus(Duration.ofMinutes(5)));
    cache.insertForTest(fileOfSize(10), "b", now.minus(Duration.ofMinutes(15)));
    cache.insertForTest(fileOfSize(10), "c", now.minus(Duration.ofMinutes(25)));
    cache.insertForTest(fileOfSize(10), "d", now.minus(Duration.ofMinutes(35)));
    assertThat(cache.listDigests()).containsExactly("a", "b", "c", "d");

    cache.clean(20, now.minus(Duration.ofMinutes(30)));

    // We keep C despite it being over the max size because it's newer than max age.
    assertThat(cache.listDigests()).containsExactly("a", "b", "c");
  }
}
