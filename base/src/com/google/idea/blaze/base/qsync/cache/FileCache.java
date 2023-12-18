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
import com.google.idea.blaze.base.command.buildresult.OutputArtifact;
import com.google.idea.blaze.base.command.buildresult.OutputArtifactInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** Local cache of the .jar, .aars and other artifacts referenced by the project. */
@SuppressWarnings("InvalidBlockTag")
public class FileCache {

  /**
   * An interface that defines the layout of an IDE artifact cache directory.
   *
   * <p>The cache layout definition is a two stage process: at the first stage {@link
   * #getOutputArtifactDestinationAndLayout(OutputArtifactInfo)} maps output artifacts to objects
   * implementing {@link OutputArtifactDestinationAndLayout} that describe the location of where the
   * artifact fetcher should place a fetched artifact and which know how to process artifacts in
   * these locations to build the final cache layout; at the second stage invocations of {@link
   * OutputArtifactDestinationAndLayout#determineFinalDestination()} and {@link
   * OutputArtifactDestinationAndLayout#createFinalDestination(Path)} build the final cache layout.
   */
  public interface CacheLayout {

    /**
     * Returns a descriptor of {@code outputArtifact} in this specific cache layout.
     *
     * <p>The descriptor tells both where a fetched artifact should be placed and knows how to
     * process it to form the final cache layout.
     */
    OutputArtifactDestinationAndLayout getOutputArtifactDestinationAndLayout(
        OutputArtifactInfo outputArtifact);

    /**
     * Returns the set of cache paths used by this layout.
     *
     * <p>This information is used to ensure that all cache paths are created & cleared when
     * necessary.
     */
    Collection<Path> getCachePaths();
  }

  /** A descriptor of the artifact's intended fetch location in a specific cache layout. */
  public interface OutputArtifactDestination {

    /**
     * A value used by the cache system to refer to this output artifact.
     *
     * <p>The value is used as a file name (a prefix) and thus must only contain allowed symbols.
     *
     * <p>Note, it does not represent a specific version of the artifact.
     */
    String getKey();

    /**
     * The location where a fetched copy of this artifact should be placed by an {@link
     * ArtifactFetcher}.
     */
    Path getCopyDestination();
  }

  /**
   * A descriptor of the artifact's locations in a specific cache layout.
   *
   * <p>Instances describe two conceptually different locations in the cache: (1) the location where
   * an artifact should be placed by an {@link ArtifactFetcher} and (2) a location where the
   * artifact was placed by the cache itself. The latter is returned by {@link
   * OutputArtifactDestinationAndLayout#determineFinalDestination()}.
   */
  public interface OutputArtifactDestinationAndLayout extends OutputArtifactDestination {

    /**
     * Determines the final destination of this artifact. This method may read the file located ay
     * {@link #getCopyDestination()} in order to do this. The final destination may be a file or a
     * directory.
     *
     * <p>This method must <b>not</b> create the file returned by it, that is done subsequently by
     * {@link #createFinalDestination}.
     */
    Path determineFinalDestination();

    /**
     * Creates the final destination Prepares a file located at {@code copyDestination} for use by
     * the IDE and returns the location of the resulting file/directory.
     *
     * <p>Note, that this might be an no-op and in this case the method should simply return {@link
     * OutputArtifactDestinationAndLayout#getCopyDestination()}.
     */
    void createFinalDestination(Path finalDestination);
  }

  private final CacheLayout cacheLayout;

  public FileCache(CacheLayout cacheLayout) {
    this.cacheLayout = cacheLayout;
  }

  /**
   * Returns local cached artifact. Returns Optional.empty() if it does not exist in this cache
   * directory.
   */
  public Optional<Path> getCacheFile(Path artifactPath) {
    Path path =
        cacheLayout
            .getOutputArtifactDestinationAndLayout(artifactPath::toString)
            .getCopyDestination();
    return Optional.ofNullable(Files.exists(path) ? path : null);
  }

  /**
   * Builds a map describing where artifact files should be copied to and where their content should
   * be extracted to.
   */
  public ImmutableMap<OutputArtifact, OutputArtifactDestinationAndLayout>
      prepareDestinationPathsAndDirectories(List<OutputArtifact> artifacts) throws IOException {
    final ImmutableMap<OutputArtifact, OutputArtifactDestinationAndLayout> pathMap =
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

  private ImmutableMap<OutputArtifact, OutputArtifactDestinationAndLayout> getLocalPathMap(
      List<OutputArtifact> outputArtifacts) {
    return outputArtifacts.stream()
        .collect(
            toImmutableMap(
                Function.identity(), cacheLayout::getOutputArtifactDestinationAndLayout));
  }
}
