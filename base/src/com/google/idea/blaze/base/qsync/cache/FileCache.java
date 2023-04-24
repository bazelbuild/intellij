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
import com.google.common.hash.Hashing;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Uninterruptibles;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.idea.blaze.base.command.buildresult.OutputArtifact;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.PathUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.annotation.Nullable;

/** Local cache of the jars referenced by the project. */
public class FileCache {

  private static final ListeningExecutorService EXECUTOR =
      MoreExecutors.listeningDecorator(
          AppExecutorUtil.createBoundedApplicationPoolExecutor("JarCacheExecutor", 128));
  private static final Logger logger = Logger.getInstance(FileCache.class);
  private static final ImmutableSet<String> ZIP_FILE_EXTENSION =
      ImmutableSet.of("aar", "jar", "srcjar");

  // TODO(xinruiy): check if need to provide multi-threading support.
  // Map from local cache key to the local cached file. The local cache key is generated from the
  // OutputArtifact.getKey(). We keep the map in memory to avoid too many IO when finding the local
  // copy of an artifact.
  private ImmutableMap<String, Path> cacheState = ImmutableMap.of();

  private final Path cacheDir;
  private final ImmutableSet<String> toCacheFileExtensions;
  private final boolean extractAfterFetch;
  private final ArtifactFetcher artifactFetcher;

  public FileCache(
      Path cacheDir,
      ImmutableSet<String> toCacheFileExtension,
      boolean extractAfterFetch,
      ArtifactFetcher artifactFetcher) {
    this.cacheDir = cacheDir;
    this.extractAfterFetch = extractAfterFetch;
    this.toCacheFileExtensions = toCacheFileExtension;
    this.artifactFetcher = artifactFetcher;
  }

  /** Updates in memory state, and returns the currently cached files. */
  @CanIgnoreReturnValue
  private ImmutableMap<String, Path> readFileState() {
    FileOperationProvider ops = FileOperationProvider.getInstance();
    try (Stream<Path> stream = Files.list(cacheDir)) {
      ImmutableMap<String, Path> cacheState =
          stream
              .filter(
                  file ->
                      ops.isFile(file.toFile())
                          && toCacheFileExtensions.contains(
                              FileUtilRt.getExtension(file.getFileName().toString())))
              .collect(toImmutableMap(path -> path.getFileName().toString(), f -> f));
      this.cacheState = cacheState;
    } catch (IOException e) {
      logger.warn("Fail to read latest cache directory status ", e);
    }
    return cacheState;
  }

  public void initialize() {
    ensureDirectoryExists(cacheDir);
    readFileState();
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

  public ListenableFuture<ImmutableSet<Path>> cache(ImmutableList<OutputArtifact> artifacts)
      throws IOException {
    ListenableFuture<List<Path>> copyArtifactFuture =
        artifactFetcher.copy(getLocalPathMap(artifacts, cacheDir));
    if (extractAfterFetch) {
      copyArtifactFuture =
          Futures.transform(copyArtifactFuture, this::extract, ArtifactFetcher.EXECUTOR);
    }
    return Futures.transform(
        copyArtifactFuture,
        list -> {
          // we do this in a transform to ensure it is run before the future completes, even though
          // it doesn't directly modify the return value.
          readFileState();
          return ImmutableSet.copyOf(list);
        },
        ArtifactFetcher.EXECUTOR);
  }

  private ImmutableMap<OutputArtifact, Path> getLocalPathMap(
      ImmutableList<OutputArtifact> outputArtifacts, Path dir) {
    return outputArtifacts.stream()
        .collect(
            toImmutableMap(
                Function.identity(),
                outputArtifact -> dir.resolve(cacheKeyForArtifact(outputArtifact.getKey()))));
  }

  private ImmutableList<Path> extract(Collection<Path> sourcePaths) {
    try {
      ImmutableList.Builder<Path> result = ImmutableList.builder();
      for (Path sourcePath : sourcePaths) {
        if (ZIP_FILE_EXTENSION.contains(
            FileUtilRt.getExtension(sourcePath.getFileName().toString()))) {
          Path tmpPath = sourcePath.resolveSibling(sourcePath.getFileName() + ".tmp");
          // Since we will keep using same file name for extracted directory, we need to rename
          // source
          // file
          Files.move(sourcePath, tmpPath);
          result.add(extract(tmpPath, sourcePath));
          Files.delete(tmpPath);
        }
      }
      return result.build();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private Path extract(Path source, Path destination) throws IOException {
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
    return destination;
  }

  public void clear() throws IOException {
    FileOperationProvider ops = FileOperationProvider.getInstance();
    if (ops.exists(cacheDir.toFile())) {
      try {
        ops.deleteDirectoryContents(cacheDir.toFile(), true);
      } finally {
        readFileState();
      }
    }
  }

  @CanIgnoreReturnValue
  public ImmutableSet<String> remove(Collection<String> artifactKeys) throws IOException {
    ImmutableList<ListenableFuture<String>> futures =
        artifactKeys.stream()
            .map(
                artifactKey -> {
                  Optional<Path> file = get(artifactKey);
                  if (file.isPresent()) {
                    return EXECUTOR.submit(
                        () -> {
                          FileOperationProvider fop = FileOperationProvider.getInstance();
                          fop.deleteRecursively(file.get().toFile());
                          return artifactKey;
                        });
                  }
                  return Futures.immediateFuture(artifactKey);
                })
            .collect(toImmutableList());
    try {
      return ImmutableSet.copyOf(Uninterruptibles.getUninterruptibly(Futures.allAsList(futures)));
    } catch (ExecutionException e) {
      if (e.getCause() instanceof IOException) {
        throw (IOException) e.getCause();
      }
      throw new FileCacheOperationException(
          "Failed to remove cache with unexpected exceptions.", e);
    } finally {
      readFileState();
    }
  }

  @Nullable
  public Optional<Path> get(OutputArtifact artifact) {
    return get(artifactKey(artifact));
  }

  @Nullable
  public Optional<Path> get(String artifactKey) {
    String key = cacheKeyForArtifact(artifactKey);
    return Optional.ofNullable(cacheState.get(key));
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

  private static String artifactKey(OutputArtifact artifact) {
    return artifact.getKey();
  }

  // TODO(xinruiy): We create FileCacheOperationException for handling unexpect exception during
  // copy/ remove files. We need to revisit it when we improve all exception handling in the future.
  static class FileCacheOperationException extends RuntimeException {
    public FileCacheOperationException(String message, Throwable e) {
      super(message, e);
    }
  }
}
