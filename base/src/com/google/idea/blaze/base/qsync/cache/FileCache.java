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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.idea.blaze.base.command.buildresult.OutputArtifact;
import com.google.idea.blaze.base.scope.BlazeContext;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.function.Function;

/** Local cache of the .jar, .aars and other artifacts referenced by the project. */
@SuppressWarnings("InvalidBlockTag")
public class FileCache {

  /**
   * An interface that defines the layout of an IDE artifact cache directory.
   *
   * <p>The cache layout definition is a two stage process: at the first stage {@link
   * #getOutputArtifactDestination(OutputArtifact)} maps output artifacts to objects implementing
   * {@link OutputArtifactDestination} that describe the location of where the artifact fetcher
   * should place a fetched artifact and which know how to process artifacts in these locations to
   * build the final cache layout; at the second stage invocations of {@link
   * OutputArtifactDestination#prepareFinalLayout()} build the final cache layout.
   */
  public interface CacheLayout {

    /**
     * Returns a descriptor of {@code outputArtifact} in this specific cache layout.
     *
     * <p>The descriptor tells both where a fetched artifact should be placed and knows how to
     * process it to form the final cache layout.
     */
    OutputArtifactDestination getOutputArtifactDestination(OutputArtifact outputArtifact);

    /** Returns a list of paths for artifact subdirectories in this specific cache layout. */
    ImmutableList<Path> getSubdirectories() throws IOException;
  }

  /**
   * A descriptor of the artifact's locations in a specific cache layout.
   *
   * <p>Instances describe two conceptually different locations in the cache: (1) the location where
   * an artifact should be placed by an {@link ArtifactFetcher} and (2) a location where the
   * artifact was placed by the cache itself. The latter is returned by {@link
   * OutputArtifactDestination#prepareFinalLayout()}.
   */
  public interface OutputArtifactDestination {

    /**
     * The location where a fetched copy of this artifact should be placed by an {@link
     * ArtifactFetcher}.
     */
    Path getCopyDestination();

    /**
     * Prepares a file located at {@link #getCopyDestination()} for use by the IDE and returns the
     * location of the resulting file/directory.
     *
     * <p>Note, that this might be an no-op and in this case the method should simply return {@link
     * #getCopyDestination()}.
     */
    Path prepareFinalLayout() throws IOException;
  }

  private final ArtifactFetcher<OutputArtifact> artifactFetcher;
  private final CacheDirectoryManager cacheDirectoryManager;
  private final CacheLayout cacheLayout;

  public FileCache(
      ArtifactFetcher<OutputArtifact> artifactFetcher,
      CacheDirectoryManager cacheDirectoryManager,
      CacheLayout cacheLayout) {
    this.artifactFetcher = artifactFetcher;
    this.cacheDirectoryManager = cacheDirectoryManager;
    this.cacheLayout = cacheLayout;
  }

  public void initialize() {
    try {
      cacheDirectoryManager.initialize();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Caches {@code artifacts} in the local cache and returns paths that the IDE should use to find
   * them.
   *
   * @noinspection UnstableApiUsage
   */
  public ListenableFuture<ImmutableSet<Path>> cache(
      ImmutableList<OutputArtifact> artifacts, BlazeContext context) throws IOException {
    return FluentFuture.from(
            ArtifactFetcher.EXECUTOR.submit(() -> prepareDestinationPathsAndDirectories(artifacts)))
        .transformAsync(
            artifactToDestinationMap -> fetchArtifacts(context, artifactToDestinationMap),
            ArtifactFetcher.EXECUTOR)
        .transform(this::prepareFinalLayouts, ArtifactFetcher.EXECUTOR);
  }

  /**
   * Fetches the output artifacts requested in {@code artifactToDestinationMap}.
   *
   * @return {@link OutputArtifactDestination}'s from the original request.
   */
  private ListenableFuture<Collection<OutputArtifactDestination>> fetchArtifacts(
      BlazeContext context,
      ImmutableMap<OutputArtifact, OutputArtifactDestination> artifactToDestinationMap) {
    final ImmutableMap<OutputArtifact, Path> artifactToDestinationPathMap =
        ImmutableMap.copyOf(
            Maps.transformEntries(artifactToDestinationMap, (k, v) -> v.getCopyDestination()));
    return Futures.transform(
        artifactFetcher.copy(artifactToDestinationPathMap, context),
        fetchedArtifactFileList -> {
          final ImmutableSet<Path> fetchedArtifactFiles =
              ImmutableSet.copyOf(fetchedArtifactFileList);
          return Maps.filterEntries(
                  artifactToDestinationMap,
                  entry -> fetchedArtifactFiles.contains(entry.getValue().getCopyDestination()))
              .values();
        },
        MoreExecutors.directExecutor());
  }

  /**
   * Builds a map describing where artifact files should be copied to and where their content should
   * be extracted to.
   */
  private ImmutableMap<OutputArtifact, OutputArtifactDestination>
      prepareDestinationPathsAndDirectories(ImmutableList<OutputArtifact> artifacts)
          throws IOException {
    final ImmutableMap<OutputArtifact, OutputArtifactDestination> pathMap =
        getLocalPathMap(artifacts);
    // Make sure target directories exists regardless of the cache directory layout, which may
    // include directories like `.zips` etc.
    final ImmutableList<Path> copyDestinationDirectories =
        pathMap.values().stream()
            .map(it -> it.getCopyDestination().getParent())
            .distinct()
            .collect(toImmutableList());
    for (Path directory : copyDestinationDirectories) {
      Files.createDirectories(directory);
    }
    return pathMap;
  }

  private ImmutableMap<OutputArtifact, OutputArtifactDestination> getLocalPathMap(
      ImmutableList<OutputArtifact> outputArtifacts) {
    return outputArtifacts.stream()
        .collect(toImmutableMap(Function.identity(), cacheLayout::getOutputArtifactDestination));
  }

  /**
   * Extracts zip-like files in the {@code sourcePaths} into the final destination directories.
   *
   * <p>Any existing files and directories at the destination paths are deleted.
   */
  private ImmutableSet<Path> prepareFinalLayouts(
      Collection<OutputArtifactDestination> destinations) {
    ImmutableSet.Builder<Path> result = ImmutableSet.builder();
    try {
      for (OutputArtifactDestination destination : destinations) {
        result.add(destination.prepareFinalLayout());
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return result.build();
  }

  public void clear() throws IOException {
    cacheDirectoryManager.clear();
  }

  public Path getDirectory() {
    return cacheDirectoryManager.cacheDirectory;
  }

  public ImmutableList<Path> getSubdirectories() throws IOException {
    return cacheLayout.getSubdirectories();
  }
}
