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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Functions;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimaps;
import com.google.common.io.ByteSource;
import com.google.common.io.MoreFiles;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.common.artifact.ArtifactFetcher.ArtifactDestination;
import com.google.idea.blaze.exception.BuildException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * A cache of build artifacts.
 *
 * <p>Downloads build artifacts on request, identifying them based on their digest as provided by
 * {@link OutputArtifact#getDigest()}.
 *
 * <p>For artifacts that have previously been requested via {@link #addAll(ImmutableCollection,
 * Context)}, provides access to their contents as a local file via {@link #get(String)}.
 *
 * <p>Access times are updated when artifacts downloads are requested, and when the contents are
 * requested, to enable unused cache entries to be cleaned up later on (not implemented yet).
 *
 * <p>An instance of this class is expected to be the sole user of the provided cache directory.
 */
class BuildArtifactCacheDirectory implements BuildArtifactCache {

  private static final Logger logger =
      Logger.getLogger(BuildArtifactCacheDirectory.class.getName());

  private final Path cacheDir;
  private final ListeningExecutorService executor;
  private final ArtifactFetcher<OutputArtifact> fetcher;

  private final Map<String, ListenableFuture<?>> activeFetches;

  /**
   * Read-write lock where the "read" is also used to adding items to the cache. The write is only
   * acquired for cleaning the cache whcih allows all other functionality to assume that cache items
   * are never deleted.
   */
  private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

  public BuildArtifactCacheDirectory(
      Path cacheDir, ArtifactFetcher<OutputArtifact> fetcher, ListeningExecutorService executor)
      throws BuildException {
    this.cacheDir = cacheDir;
    this.fetcher = fetcher;
    this.executor = executor;
    this.activeFetches = Maps.newHashMap();

    if (!Files.exists(cacheDir)) {
      try {
        Files.createDirectories(cacheDir);
      } catch (IOException e) {
        throw new BuildException(e);
      }
    }
    if (!Files.isDirectory(cacheDir)) {
      throw new BuildException("Cache dir is not a directory: " + cacheDir);
    }
  }

  @VisibleForTesting
  int readLockCount() {
    return lock.getReadHoldCount();
  }

  @VisibleForTesting
  int writeLockCount() {
    return lock.getWriteHoldCount();
  }

  @VisibleForTesting
  Path artifactPath(String digest) {
    return cacheDir.resolve(digest);
  }

  private Path artifactPath(OutputArtifact a) {
    return artifactPath(a.getDigest());
  }

  private ArtifactDestination artifactDestination(OutputArtifact a) {
    return new ArtifactDestination(artifactPath(a));
  }

  /**
   * Returns true if the given artifact is present in the cache. May return true even if the
   * artifact is still being fetched (i.e. a true value does not mean the artifact is ready to be
   * used).
   */
  private boolean contains(OutputArtifact artifact) {
    return Files.exists(artifactPath(artifact));
  }

  /**
   * Updates the metadata for a cache entry.
   *
   * <p>For now, we juse use the filesystem last access timestamp for this, in future we may
   * consider adding an explicit metadata file if the timestamp alone proves insufficient.
   *
   * <p>Note we return Void to make this method easier to use with {@link
   * java.util.concurrent.ExecutorService#submit(Callable)}. }
   */
  private Void updateMetadata(String digest, Instant lastAccess) throws IOException {
    // note, when we pass null in here, the existing timestamps are left unchanged:
    Files.getFileAttributeView(artifactPath(digest), BasicFileAttributeView.class)
        .setTimes(
            /* lastModifiedTime */ null,
            /* lastAccessTime */ FileTime.from(lastAccess),
            /* createTime */ null);
    return null;
  }

  /** Updates the metadata for many files concurrently. */
  private ListenableFuture<?> updateMetadata(
      ImmutableCollection<OutputArtifact> artifacts, Instant lastAccess) {
    return Futures.allAsList(
        artifacts.stream()
            .map(a -> executor.submit(() -> updateMetadata(a.getDigest(), lastAccess)))
            .collect(toImmutableList()));
  }

  /**
   * Marks a set of artifacts as being actively fetched while an asynchronous process is running.
   *
   * @param artifacts The affected artifacts
   * @param done The fetch future. Once this future completes, and artifacts will be un-marked as
   *     being actively fetched.
   * @return The passed future, for convenience.
   */
  @CanIgnoreReturnValue
  private ListenableFuture<?> markActiveUntilComplete(
      ImmutableCollection<OutputArtifact> artifacts, ListenableFuture<?> done) {
    ImmutableSet<String> digests =
        artifacts.stream().map(OutputArtifact::getDigest).collect(toImmutableSet());
    synchronized (activeFetches) {
      // mark the  artifacts as being actively fetched. If they are requested in the meantime,
      // the future will be used to wait until the fetch is complete.
      digests.forEach(d -> activeFetches.put(d, done));
      // and when that's done, un-mark them as active fetches:
      done.addListener(
          () -> {
            synchronized (activeFetches) {
              activeFetches.keySet().removeAll(digests);
            }
          },
          executor);
    }
    return done;
  }

  /**
   * Updates metadata for a set of artifacts, marking that as active while this takes place, to
   * avoid any potential race conditions.
   *
   * @param artifacts The affected artifacts.
   * @param lastAccess The last access time to set.
   * @return A future of the update operation.
   */
  private ListenableFuture<?> safeUpdateMetadata(
      ImmutableCollection<OutputArtifact> artifacts, Instant lastAccess) {
    ListenableFuture<?> update = updateMetadata(artifacts, lastAccess);
    return markActiveUntilComplete(artifacts, update);
  }

  /**
   * Kicks off an artifacts fetch, marking the artifacts as active while it's running.
   *
   * @param artifacts Artifatcs to fetch.
   * @param accessTime The time that the artifacts were requested.
   * @param context Context
   * @return A future for the fetch operation.
   */
  private ListenableFuture<?> startFetch(
      ImmutableCollection<OutputArtifact> artifacts, Instant accessTime, Context<?> context) {

    // kick off the copy process for new artifacts:
    ListenableFuture<?> newFetch =
        fetcher.copy(
            artifacts.stream()
                .distinct()
                .collect(toImmutableMap(Functions.identity(), this::artifactDestination)),
            context);
    // when that's done, set their metadata:
    ListenableFuture<?> done =
        Futures.transformAsync(newFetch, unused -> updateMetadata(artifacts, accessTime), executor);

    return markActiveUntilComplete(artifacts, done);
  }

  private <T> Optional<ListenableFuture<T>> performWithReadLock(
      Supplier<Optional<ListenableFuture<T>>> method) {
    Lock readLock = lock.readLock();
    readLock.lock();
    try {
      Optional<ListenableFuture<T>> future = method.get();
      if (future.isEmpty()) {
        return future;
      }
      future.get().addListener(readLock::unlock, directExecutor());
      readLock = null;
      return future;
    } finally {
      if (readLock != null) {
        readLock.unlock();
      }
    }
  }

  /**
   * Requests that the given artifacts are added to the cache.
   *
   * @return A future map of (digest)->(absolute path of the artifact) that will complete once all
   *     artifacts have been added to the cache. The future will fail if we fail to add any artifact
   *     to the cache.
   */
  @Override
  public ListenableFuture<?> addAll(
      ImmutableCollection<OutputArtifact> artifacts, Context<?> context) {
    return performWithReadLock(() -> Optional.of(performAdd(artifacts, context))).get();
  }

  private ListenableFuture<?> performAdd(
      ImmutableCollection<OutputArtifact> artifacts, Context<?> context) {
    synchronized (activeFetches) {
      Instant accessTime = Instant.now();
      // filter out any artifacts for which there is already a fetch pending:
      ImmutableList<OutputArtifact> allArtifacts =
          artifacts.stream()
              .filter(a -> !activeFetches.containsKey(a.getDigest()))
              .collect(toImmutableList());

      // group them based on whether the artifact is already cached
      ImmutableListMultimap<Boolean, OutputArtifact> artifactsByPresence =
          Multimaps.index(allArtifacts, this::contains);

      // Fetch absent artifacts
      ListenableFuture<?> fetch = startFetch(artifactsByPresence.get(false), accessTime, context);

      // Update the metadata of present artifacts
      ListenableFuture<?> metadataUpdate =
          safeUpdateMetadata(artifactsByPresence.get(true), accessTime);

      return Futures.allAsList(fetch, metadataUpdate);
    }
  }

  @Nullable
  private Path getArtifactIfPresent(String digest) {
    Path artifactPath = artifactPath(digest);
    if (!Files.exists(artifactPath)) {
      return null;
    }
    ListenableFuture<?> unused = executor.submit(() -> updateMetadata(digest, Instant.now()));
    return artifactPath;
  }

  /**
   * Returns the path to an artifact that was previously added to the cache.
   *
   * @return A future of the artifact path if the artifact is already present, or is in the process
   *     of being requested. Empty if the artifact has never been added to the cache, or has been
   *     deleted since then.
   */
  @Override
  public Optional<ListenableFuture<ByteSource>> get(String digest) {
    return performWithReadLock(() -> performGet(digest));
  }

  public Optional<ListenableFuture<ByteSource>> performGet(String digest) {
    ListenableFuture<?> activeFetch;
    synchronized (activeFetches) {
      activeFetch = activeFetches.get(digest);
    }
    Path artifactPath = artifactPath(digest);
    if (activeFetch == null) {
      return Optional.ofNullable(getArtifactIfPresent(digest))
          .map(MoreFiles::asByteSource)
          .map(Futures::immediateFuture);
    } else {
      ListenableFuture<?> unused = executor.submit(() -> updateMetadata(digest, Instant.now()));
      return Optional.of(
          Futures.transform(
              activeFetch, unused2 -> MoreFiles.asByteSource(artifactPath), directExecutor()));
    }
  }

  @VisibleForTesting
  void insertForTest(InputStream content, String digest, Instant lastAccessTime)
      throws IOException {
    MoreFiles.asByteSink(artifactPath(digest)).writeFrom(content);
    updateMetadata(digest, lastAccessTime);
  }

  @AutoValue
  abstract static class Entry {
    abstract Path path();

    abstract FileTime lastAccessTime();

    abstract long size();

    static Entry create(Path p, FileTime lastAccessTime, long size) {
      return new AutoValue_BuildArtifactCacheDirectory_Entry(p, lastAccessTime, size);
    }
  }

  private ImmutableList<Path> list() throws IOException {
    return MoreFiles.listFiles(cacheDir);
  }

  @VisibleForTesting
  ImmutableList<String> listDigests() throws IOException {
    return list().stream().map(Path::getFileName).map(Path::toString).collect(toImmutableList());
  }

  @VisibleForTesting
  FileTime readAccessTime(String digest) throws IOException {
    return readAccessTime(artifactPath(digest));
  }

  FileTime readAccessTime(Path entry) throws IOException {
    return Files.getFileAttributeView(entry, BasicFileAttributeView.class)
        .readAttributes()
        .lastAccessTime();
  }

  @Override
  public void clean() throws IOException {
    Lock writeLock = lock.writeLock();
    writeLock.lock();
    try {
      // We keep 1GB of artifacts, or any accessed in the last 24 hours if this is more:
      clean(1024L * 1024L * 1024L, Instant.now().minus(Duration.ofHours(24)));
    } finally {
      writeLock.unlock();
    }
  }

  @VisibleForTesting
  void clean(long maxTargetSize, Instant minAgeToDelete) throws IOException {
    ImmutableList<Path> entries = list();
    int failed = 0;
    long totalSize = 0;
    PriorityQueue<Entry> queue = new PriorityQueue<>(Comparator.comparing(Entry::lastAccessTime));
    for (Path p : entries) {
      try {
        Entry e = Entry.create(p, readAccessTime(p), Files.size(p));
        queue.add(e);
        totalSize += e.size();
      } catch (IOException e) {
        // If we fail read the attributes for a file, we just ignore it and clean up the rest of the
        // cache as best we can.
        failed += 1;
      }
    }
    if (failed > 0) {
      logger.warning("Failed to read attributes from " + failed + " cache files when cleaning");
    }
    long remainingSize = totalSize;
    while (!queue.isEmpty()) {
      if (remainingSize <= maxTargetSize) {
        // size target reached
        logger.info("Reached target cache size: " + remainingSize + "<=" + maxTargetSize);
        return;
      }
      if (queue.peek().lastAccessTime().toInstant().isAfter(minAgeToDelete)) {
        // the oldest artifact is newer than the minimum age, so we stop deleting artifacts even
        // though the cache is bigger than the max size.
        logger.info(
            "Not deleting entries accessed since "
                + minAgeToDelete
                + "; remaining cache size="
                + remainingSize);
        return;
      }

      Entry toDelete = queue.poll();
      remainingSize -= toDelete.size();
      Files.delete(toDelete.path());
    }
  }
}
