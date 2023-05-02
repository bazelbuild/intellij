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

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.hash.Hashing;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
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
import java.util.List;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Local cache of the jars referenced by the project. */
public class FileCache {

  private static final ImmutableSet<String> ZIP_FILE_EXTENSION =
      ImmutableSet.of("aar", "jar", "srcjar");

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

  public ListenableFuture<ImmutableSet<Path>> cache(
      ImmutableList<OutputArtifact> artifacts, BlazeContext context) throws IOException {
    ListenableFuture<List<Path>> copyArtifactFuture =
        artifactFetcher.copy(getLocalPathMap(artifacts, cacheDir), context);
    if (extractAfterFetch) {
      copyArtifactFuture =
          Futures.transform(copyArtifactFuture, this::extract, ArtifactFetcher.EXECUTOR);
    }
    return Futures.transform(copyArtifactFuture, ImmutableSet::copyOf, ArtifactFetcher.EXECUTOR);
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
          // source file
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
