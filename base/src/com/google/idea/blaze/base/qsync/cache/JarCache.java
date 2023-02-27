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

import com.google.common.collect.FluentIterable;
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
import com.google.idea.blaze.base.command.buildresult.BlazeArtifact;
import com.google.idea.blaze.base.command.buildresult.BlazeArtifact.LocalFileArtifact;
import com.google.idea.blaze.base.command.buildresult.OutputArtifact;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.annotation.Nullable;

/** Local cache of the jars referenced by the project. */
public class JarCache {

  private static final ListeningExecutorService EXECUTOR =
      MoreExecutors.listeningDecorator(
          AppExecutorUtil.createBoundedApplicationPoolExecutor("JarCacheExecutor", 128));
  private static final Logger logger = Logger.getInstance(JarCache.class);

  // TODO(xinruiy): check if need to provide multi-threading support.
  // Map from local cache key to the local cached file. The local cache key is generated from the
  // OutputArtifact.getKey(). We keep the map in memory to avoid too many IO when finding the local
  // copy of an artifact.
  private ImmutableMap<String, Path> cacheState = ImmutableMap.of();

  private final Path jarDir;
  private final Path aarDir;
  private final Path gensrcDir;

  public JarCache(Path jarDir, Path aarDir, Path gensrcDir) {
    this.jarDir = jarDir;
    this.aarDir = aarDir;
    this.gensrcDir = gensrcDir;
  }

  /** Updates in memory state, and returns the currently cached files. */
  @CanIgnoreReturnValue
  private ImmutableMap<String, Path> readFileState() {
    FileOperationProvider ops = FileOperationProvider.getInstance();
    try (Stream<Path> stream = getArtifactStream()) {
      ImmutableMap<String, Path> cacheState =
          stream
              .filter(
                  file ->
                      ops.isFile(file.toFile())
                          && (file.getFileName().endsWith(".jar")
                              || file.getFileName().endsWith(".aar")))
              .collect(toImmutableMap(path -> path.getFileName().toString(), f -> f));
      this.cacheState = cacheState;
    } catch (IOException e) {
      logger.warn("Fail to read latest cache directory status ", e);
    }
    return cacheState;
  }

  private Stream<Path> getArtifactStream() throws IOException {
    try (Stream<Path> jarStream = Files.list(jarDir);
        Stream<Path> aarStream = Files.list(aarDir)) {
      return Stream.concat(jarStream, aarStream);
    }
  }

  public void initialize() {
    ensureDirectoryExists(jarDir);
    ensureDirectoryExists(aarDir);
    ensureDirectoryExists(gensrcDir);
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

  public ImmutableSet<Path> cache(
      Collection<OutputArtifact> jars,
      Collection<OutputArtifact> aars,
      Collection<OutputArtifact> generatedSources)
      throws IOException {
    try {
      // update cache files, and remove files if required
      ImmutableList<ListenableFuture<Path>> futures =
          FluentIterable.concat(
                  copyLocally(jars, jarDir),
                  copyLocally(aars, aarDir, x -> true),
                  copyLocally(
                      generatedSources, gensrcDir, source -> source.getKey().endsWith(".srcjar")))
              .toList();
      return ImmutableSet.copyOf(Uninterruptibles.getUninterruptibly(Futures.allAsList(futures)));
    } catch (ExecutionException e) {
      if (e.getCause() instanceof IOException) {
        throw (IOException) e.getCause();
      }
      throw new JarCacheOperationException(
          "Failed to copy jars to cache with unexpected exceptions.", e);
    } finally {
      readFileState();
    }
  }

  private ImmutableList<ListenableFuture<Path>> copyLocally(
      Collection<OutputArtifact> toUpdateArtifacts, Path cacheDir) {
    return copyLocally(toUpdateArtifacts, cacheDir, x -> false);
  }

  private ImmutableList<ListenableFuture<Path>> copyLocally(
      Collection<OutputArtifact> toUpdateArtifacts,
      Path cacheDir,
      Predicate<OutputArtifact> shouldExtract) {
    ImmutableList.Builder<ListenableFuture<Path>> tasks = ImmutableList.builder();
    for (OutputArtifact toUpdateArtifact : toUpdateArtifacts) {
      tasks.add(
          EXECUTOR.submit(
              () -> {
                Path destination = cacheDir.resolve(cacheKeyForArtifact(toUpdateArtifact.getKey()));
                if (shouldExtract.test(toUpdateArtifact)) {
                  extract(toUpdateArtifact, destination);
                } else {
                  copyLocally(toUpdateArtifact, destination);
                }
                return destination;
              }));
    }
    return tasks.build();
  }

  private void copyLocally(BlazeArtifact output, Path destination) throws IOException {
    if (output instanceof LocalFileArtifact) {
      File source = ((LocalFileArtifact) output).getFile();
      Files.copy(
          Paths.get(source.getPath()),
          destination,
          StandardCopyOption.REPLACE_EXISTING,
          StandardCopyOption.COPY_ATTRIBUTES);
      return;
    }
    try (InputStream stream = output.getInputStream()) {
      // TODO(b/268420971): Files.copy does not work with grpc, we need to support it later.
      Files.copy(stream, destination, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private void extract(BlazeArtifact output, Path destination) throws IOException {
    Files.createDirectories(destination);
    try (InputStream inputStream = output.getInputStream();
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

  public void cleanupCacheDir() throws IOException {
    FileOperationProvider ops = FileOperationProvider.getInstance();
    if (ops.exists(jarDir.toFile())) {
      try {
        ops.deleteDirectoryContents(jarDir.toFile(), true);
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
      throw new JarCacheOperationException("Failed to remove cache with unexpected exceptions.", e);
    } finally {
      readFileState();
    }
  }

  @Nullable
  public Optional<Path> get(OutputArtifact jar) {
    return get(artifactKey(jar));
  }

  @Nullable
  public Optional<Path> get(String artifactKey) {
    String key = cacheKeyForArtifact(artifactKey);
    return Optional.ofNullable(cacheState.get(key));
  }

  private static String cacheKeyForArtifact(String artifactKey) {
    return String.format(
        "%s.%s", cacheKeyInternal(artifactKey), FileUtil.getExtension(artifactKey, "jar"));
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

  // TODO(xinruiy): We create JarCacheOperationException for handling unexpect exception during
  // copy/ remove files. We need to revisit it when we improve all exception handling in the future.
  static class JarCacheOperationException extends RuntimeException {
    public JarCacheOperationException(String message, Throwable e) {
      super(message, e);
    }
  }
}
