/*
 * Copyright 2021 The Bazel Authors. All rights reserved.
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

package com.google.idea.blaze.android.libraries;

import static com.android.SdkConstants.FN_LINT_JAR;

import com.android.SdkConstants;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.android.sync.model.AarLibrary;
import com.google.idea.blaze.base.command.buildresult.BlazeArtifact;
import com.google.idea.blaze.base.command.buildresult.BlazeArtifact.LocalFileArtifact;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.prefetch.FetchExecutor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.ZipUtil;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/**
 * Unzip prefetched aars to local cache directories. AARs are directories with many files. {@see
 * https://developer.android.com/studio/projects/android-library.html#aar-contents}, for a subset of
 * the contents (documentation may be outdated).
 *
 * <p>The IDE wants at least the following:
 *
 * <ul>
 *   <li>the res/ folder
 *   <li>the R.txt file adjacent to the res/ folder
 *   <li>See {@link com.android.tools.idea.resources.aar.AarSourceResourceRepository} for the
 *       dependency on R.txt.
 *   <li>jars: we use the merged output jar from Bazel instead of taking jars from the AAR. It gives
 *       us freedom in the future to use an ijar or header jar instead, which is more lightweight.
 *       It should be placed in a jars/ folder adjacent to the res/ folder. See {@link
 *       org.jetbrains.android.uipreview.ModuleClassLoader}, for that possible assumption.
 *   <li>The IDE may want the AndroidManifest.xml as well.
 * </ul>
 */
public final class Unpacker {
  private static final Logger logger = Logger.getInstance(Unpacker.class);
  private static final String DOT_MERGED_AAR = ".mergedaar";
  private static final int MAX_BUFFER_SIZE = 4096;

  public static String getAarDirName(AarLibrary aarLibrary) {
    String key = aarLibrary.key.toString();
    return UnpackedAarUtils.generateAarDirectoryName(
            /* name= */ key, /* hashCode= */ key.hashCode())
        + DOT_MERGED_AAR;
  }

  /** Updated prefetched aars to aar directory. */
  public static void unpack(
      ImmutableMap<String, AarLibraryContents> toCache,
      Set<String> updatedKeys,
      AarCache aarCache,
      boolean removeMissingFile)
      throws ExecutionException, InterruptedException {
    unpackAarsToDir(toCache, updatedKeys, aarCache);
    // do not merge aars if there's no change in aar directory (no new & removed aars)
    if (!updatedKeys.isEmpty() || removeMissingFile) {
      mergeAars(toCache.values(), aarCache);
    }
  }

  /**
   * Aars with same library key will be merged into <reosurce package name>.mergedaar and keeps file
   * structure.
   */
  private static void mergeAars(ImmutableCollection<AarLibraryContents> toMerge, AarCache aarCache)
      throws ExecutionException, InterruptedException {
    Stopwatch timer = Stopwatch.createStarted();
    Map<String, List<BlazeArtifact>> resourcePackageToAar = new HashMap<>();
    FileOperationProvider ops = FileOperationProvider.getInstance();
    toMerge.stream()
        .forEach(
            aarLibraryContents ->
                resourcePackageToAar
                    .computeIfAbsent(aarLibraryContents.libraryKey(), key -> new ArrayList<>())
                    .add(aarLibraryContents.aar()));

    List<ListenableFuture<?>> futures = new ArrayList<>();
    for (Map.Entry<String, List<BlazeArtifact>> entry : resourcePackageToAar.entrySet()) {
      futures.add(
          FetchExecutor.EXECUTOR.submit(
              () -> {
                File dest = null;
                try {
                  String resourcePackage = entry.getKey();
                  dest =
                      aarCache.recreateAarDir(
                          ops,
                          UnpackedAarUtils.generateAarDirectoryName(
                                  /* name= */ resourcePackage,
                                  /* hashCode= */ resourcePackage.hashCode())
                              + DOT_MERGED_AAR);
                  copyResourceFiles(ops, entry.getValue(), dest, aarCache);
                } catch (IOException e) {
                  logger.warn("Fail to create aar directory for " + entry.getKey(), e);
                } finally {
                  // if fails to copy any resources to dest, remove the whole empty dest directory
                  if (dest != null && dest.list().length == 0) {
                    dest.delete();
                  }
                }
              }));
    }
    Futures.allAsList(futures).get();
    logger.info("Merged " + toMerge.size() + "aars in " + timer.elapsed().getSeconds() + " sec.");
  }

  /* Get all files in src directory, copy it to dest directory and keep same file structure. */
  private static void copyResourceFiles(
      FileOperationProvider ops, List<BlazeArtifact> aars, File dest, AarCache aarCache)
      throws IOException {
    String errorMessage = "";
    for (BlazeArtifact aar : aars) {
      File srcDir = aarCache.aarDirForKey(UnpackedAarUtils.getAarDirName(aar));
      for (Path srcResourceFilePath :
          ops.listFilesRecursively(
              srcDir,
              Integer.MAX_VALUE,
              // list all xml files
              (path, attrs) ->
                  attrs.isRegularFile()
                      && path.getFileName().toString().endsWith(SdkConstants.DOT_XML))) {
        // find the relative path to created in merged aar dir
        Path destResourceFilePath =
            dest.toPath().resolve(srcDir.toPath().relativize(srcResourceFilePath));
        try {
          Path parentDir = destResourceFilePath.getParent();
          ops.mkdirs(parentDir.toFile());
          if (Files.exists(destResourceFilePath)) {
            // Only need one manifest file, we can just ignore duplicates.
            String destResourceFilename = destResourceFilePath.getFileName().toString();
            if (destResourceFilename.equals(SdkConstants.ANDROID_MANIFEST_XML)
                || isSameContent(destResourceFilePath, srcResourceFilePath)) {
              continue;
            }
            // If we have 2 values xml files with the same name, rename one.
            destResourceFilePath =
                destResourceFilePath
                    .getParent()
                    .resolve(
                        FileUtil.getNameWithoutExtension(destResourceFilename)
                            + "-"
                            + FileUtil.getNameWithoutExtension(srcDir)
                            + SdkConstants.DOT_XML);
          }
          ops.copy(srcResourceFilePath.toFile(), destResourceFilePath.toFile());
        } catch (IOException e) {
          // keep copying the other resource files and throw exception at the end.
          errorMessage +=
              "Fail to copy source file "
                  + srcResourceFilePath
                  + " to merged aar directory "
                  + destResourceFilePath
                  + ": "
                  + e.getMessage();
        }
      }
    }
    if (!errorMessage.isEmpty()) {
      throw new IOException(errorMessage);
    }
  }

  private static boolean isSameContent(Path file1, Path file2) throws IOException {
    final long size = Files.size(file1);
    if (size != Files.size(file2)) {
      return false;
    }

    if (size < MAX_BUFFER_SIZE) {
      return Arrays.equals(Files.readAllBytes(file1), Files.readAllBytes(file2));
    }

    try (InputStream is1 = Files.newInputStream(file1);
        InputStream is2 = Files.newInputStream(file2)) {
      int data;
      while ((data = is1.read()) != -1) {
        if (data != is2.read()) {
          return false;
        }
      }
    }

    return true;
  }

  private static void unpackAarsToDir(
      ImmutableMap<String, AarLibraryContents> toCache, Set<String> updatedKeys, AarCache aarCache)
      throws ExecutionException, InterruptedException {
    FileOperationProvider ops = FileOperationProvider.getInstance();
    List<ListenableFuture<?>> futures = new ArrayList<>();
    updatedKeys.forEach(
        key ->
            futures.add(
                FetchExecutor.EXECUTOR.submit(
                    () -> unpackAarToDir(ops, toCache.get(key), aarCache))));
    Futures.allAsList(futures).get();
  }

  /**
   * Each .aar file will be unpacked as <key_from_artifact_location>.aar directories in cache
   * directory. A timestamp file will be created to decide if updated is needed when a new .aar file
   * with same name is found next time.
   */
  private static void unpackAarToDir(
      FileOperationProvider ops, AarLibraryContents aarAndJar, AarCache aarCache) {
    String cacheKey = UnpackedAarUtils.getAarDirName(aarAndJar.aar());
    try {
      File aarDir = aarCache.recreateAarDir(ops, cacheKey);
      // TODO(brendandouglas): decompress via ZipInputStream so we don't require a local file
      File toCopy = getOrCreateLocalFile(aarAndJar.aar());
      ZipUtil.extract(
          toCopy,
          aarDir,
          // Skip jars except lint.jar. We will copy jar in AarLibraryContents instead.
          // That could give us freedom in the future to use an ijar or header jar instead,
          // which is more lightweight. But it's not applied to lint.jar
          (dir, name) -> name.equals(FN_LINT_JAR) || !name.endsWith(".jar"));

      try {
        File stampFile = aarCache.createTimeStampFile(cacheKey);
        if (!(aarAndJar.aar() instanceof LocalFileArtifact)) {
          // no need to set the timestamp for remote artifacts
          return;
        }
        long sourceTime = ops.getFileModifiedTime(((LocalFileArtifact) aarAndJar.aar()).getFile());
        if (!ops.setFileModifiedTime(stampFile, sourceTime)) {
          logger.warn("Failed to set AAR cache timestamp for " + aarAndJar.aar());
        }
      } catch (IOException e) {
        logger.warn("Failed to set AAR cache timestamp for " + aarAndJar.aar(), e);
      }

      // copy merged jar
      if (aarAndJar.jar() != null) {
        try (InputStream stream = aarAndJar.jar().getInputStream()) {
          Path destination = Paths.get(UnpackedAarUtils.getJarFile(aarDir).getPath());
          ops.mkdirs(destination.getParent().toFile());
          Files.copy(stream, destination, StandardCopyOption.REPLACE_EXISTING);
        }
      }

    } catch (IOException e) {
      logger.warn(
          String.format(
              "Failed to extract AAR %s to %s", aarAndJar.aar(), aarCache.aarDirForKey(cacheKey)),
          e);
    }
  }

  /** Returns a locally-accessible file mirroring the contents of this {@link BlazeArtifact}. */
  private static File getOrCreateLocalFile(BlazeArtifact artifact) throws IOException {
    if (artifact instanceof LocalFileArtifact) {
      return ((LocalFileArtifact) artifact).getFile();
    }
    File tmpFile =
        FileUtil.createTempFile(
            "local-aar-file",
            Integer.toHexString(UnpackedAarUtils.getArtifactKey(artifact).hashCode()),
            /* deleteOnExit= */ true);
    try (InputStream stream = artifact.getInputStream()) {
      Files.copy(stream, Paths.get(tmpFile.getPath()), StandardCopyOption.REPLACE_EXISTING);
      return tmpFile;
    }
  }

  private Unpacker() {}
}
