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
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.hash.Hashing;
import com.google.common.io.MoreFiles;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.idea.blaze.base.command.buildresult.OutputArtifact;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.PathUtil;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Objects;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Local cache of the .jar, .aars and other artifacts referenced by the project. */
@SuppressWarnings("InvalidBlockTag")
public class FileCache {

  private static final ImmutableSet<String> ZIP_FILE_EXTENSION =
      ImmutableSet.of("aar", "jar", "srcjar");
  private static final String PACKED_FILES_DIR = ".zips";

  private final Path cacheDir;
  private final boolean extractAfterFetch;
  private final ArtifactFetcher artifactFetcher;

  public FileCache(Path cacheDir, boolean extractAfterFetch, ArtifactFetcher artifactFetcher) {
    this.cacheDir = cacheDir;
    this.extractAfterFetch = extractAfterFetch;
    this.artifactFetcher = artifactFetcher;
  }

  public void initialize() {
    ensureDirectoryExists(cacheDir);
  }

  private void ensureDirectoryExists(Path directory) {
    if (!Files.exists(directory)) {
      FileOperationProvider ops = FileOperationProvider.getInstance();
      ops.mkdirs(directory.toFile());
      if (!ops.isDirectory(directory.toFile())) {
        throw new IllegalArgumentException(
            "Cache Directory '" + directory + "' is not a valid directory");
      }
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
    ListenableFuture<ImmutableList<Path>> copyArtifactFuture =
        Futures.transform(
            Futures.transformAsync(
                prepareDestinationPathsAndDirectories(artifacts),
                artifactToDestinationMap -> fetchArtifacts(context, artifactToDestinationMap),
                MoreExecutors.directExecutor()),
            this::maybeExtract,
            ArtifactFetcher.EXECUTOR);
    return Futures.transform(copyArtifactFuture, ImmutableSet::copyOf, ArtifactFetcher.EXECUTOR);
  }

  /** A record that describes the location of an output artifact in cache directories. */
  private static class OutputArtifactDestination {

    /**
     * The location where in the cache directory the representation of the artifact for the IDE
     * should be placed.
     */
    public final Path finalDestination;

    /**
     * The location where in the cache directory the artifact file itself should be placed.
     *
     * <p>The final and copy destinations are the same if the artifact file needs not to be
     * extracted.
     */
    public final Path copyDestination;

    public OutputArtifactDestination(Path finalDestination, Path copyDestination) {
      this.finalDestination = finalDestination;
      this.copyDestination = copyDestination;
    }

    public boolean needsExtracting() {
      return !Objects.equals(finalDestination, copyDestination);
    }
  }

  /**
   * Fetches the output artifacts requested in {@code artifactToDestinationMap}.
   *
   * @return {@link OutputArtifactDestination}'s from the original request.
   */
  private ListenableFuture<Collection<OutputArtifactDestination>> fetchArtifacts(
      BlazeContext context,
      ImmutableMap<OutputArtifact, OutputArtifactDestination> artifactToDestinationMap)
      throws IOException {
    final ImmutableMap<OutputArtifact, Path> artifactToDestinationPathMap =
        ImmutableMap.copyOf(
            Maps.transformEntries(artifactToDestinationMap, (k, v) -> v.copyDestination));
    return Futures.transform(
        artifactFetcher.copy(artifactToDestinationPathMap, context),
        fetchedArtifactFileList -> {
          final ImmutableSet<Path> fetchedArtifactFiles =
              ImmutableSet.copyOf(fetchedArtifactFileList);
          return Maps.filterEntries(
                  artifactToDestinationMap,
                  entry -> fetchedArtifactFiles.contains(entry.getValue().copyDestination))
              .values();
        },
        MoreExecutors.directExecutor());
  }

  /**
   * Builds a map describing where artifact files should be copied to and where their content should
   * be extracted to.
   */
  private ListenableFuture<ImmutableMap<OutputArtifact, OutputArtifactDestination>>
      prepareDestinationPathsAndDirectories(ImmutableList<OutputArtifact> artifacts) {
    return ArtifactFetcher.EXECUTOR.submit(
        () -> {
          final ImmutableMap<OutputArtifact, OutputArtifactDestination> pathMap =
              getLocalPathMap(artifacts, cacheDir);
          // Make sure target directories exists regardless of the cache directory layout, which may
          // include directories like `.zips` etc.
          final ImmutableList<Path> copyDestinationDirectories =
              pathMap.values().stream()
                  .map(it -> it.copyDestination.getParent())
                  .distinct()
                  .collect(toImmutableList());
          for (Path directory : copyDestinationDirectories) {
            Files.createDirectories(directory);
          }
          return pathMap;
        });
  }

  /**
   * Maps output artifacts to the paths of local files the artifacts should be copied to.
   *
   * <p>Output artifacts that needs to be extracted for being used in the IDE are placed into
   * sub-directories under {@code dir} in which their content will be extracted later.
   *
   * <p>When artifact files are extracted, the final file system layout looks like:
   *
   * <pre>
   *     aars/
   *         .zips/
   *             file.zip-like.aar                # the zip file being extracted
   *         file.zip-like.aar/
   *             file.txt                         # a file from file.zip-like.aar
   *             res/                             # a directory from file.zip-like.aar
   *                 layout/
   *                     main.xml
   * </pre>
   */
  private ImmutableMap<OutputArtifact, OutputArtifactDestination> getLocalPathMap(
      ImmutableList<OutputArtifact> outputArtifacts, Path dir) {
    return outputArtifacts.stream()
        .collect(
            toImmutableMap(
                Function.identity(),
                outputArtifact -> {
                  String key = cacheKeyForArtifact(outputArtifact.getKey());
                  final Path finalDestination = dir.resolve(key);
                  final Path copyDestination =
                      shouldExtractFile(Path.of(outputArtifact.getRelativePath()))
                          ? dir.resolve(PACKED_FILES_DIR).resolve(key)
                          : finalDestination;
                  return new OutputArtifactDestination(finalDestination, copyDestination);
                }));
  }

  /**
   * Extracts zip-like files in the {@code sourcePaths} into the final destination directories.
   *
   * <p>Any existing files and directories at the destination paths are deleted.
   */
  private ImmutableList<Path> maybeExtract(Collection<OutputArtifactDestination> destinations) {
    ImmutableList.Builder<Path> result = ImmutableList.builder();
    try {
      for (OutputArtifactDestination destination : destinations) {
        if (destination.needsExtracting()) {
          if (Files.exists(destination.finalDestination)) {
            MoreFiles.deleteRecursively(destination.finalDestination);
          }
          extract(destination.copyDestination, destination.finalDestination);
        }
        result.add(destination.finalDestination);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return result.build();
  }

  private boolean shouldExtractFile(Path sourcePath) {
    return extractAfterFetch
        && ZIP_FILE_EXTENSION.contains(
            FileUtilRt.getExtension(sourcePath.getFileName().toString()));
  }

  private void extract(Path source, Path destination) throws IOException {
    Files.createDirectories(destination);
    try (InputStream inputStream = Files.newInputStream(source);
        ZipInputStream zis = new ZipInputStream(inputStream)) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        if (entry.isDirectory()) {
          Files.createDirectories(destination.resolve(entry.getName()));
        } else {
          // Srcjars do not contain separate directory entries
          Files.createDirectories(destination.resolve(entry.getName()).getParent());
          Files.copy(
              zis, destination.resolve(entry.getName()), StandardCopyOption.REPLACE_EXISTING);
        }
      }
    }
  }

  public void clear() throws IOException {
    FileOperationProvider ops = FileOperationProvider.getInstance();
    if (ops.exists(cacheDir.toFile())) {
      ops.deleteDirectoryContents(cacheDir.toFile(), true);
    }
  }

  private static String cacheKeyForArtifact(String artifactKey) {
    return String.format(
        "%s.%s", cacheKeyInternal(artifactKey), FileUtilRt.getExtension(artifactKey));
  }

  private static String cacheKeyInternal(String artifactKey) {
    String name = FileUtil.getNameWithoutExtension(PathUtil.getFileName(artifactKey));
    return name
        + "_"
        + Integer.toHexString(Hashing.sha256().hashString(artifactKey, UTF_8).hashCode());
  }
}
