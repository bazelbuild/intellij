/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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

import static com.google.common.io.Files.asByteSource;

import com.android.SdkConstants;
import com.google.common.base.Preconditions;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.hash.Hashing;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.idea.blaze.android.sync.model.AarLibrary;
import com.google.idea.blaze.base.filecache.FileCache;
import com.google.idea.blaze.base.filecache.FileCacheSynchronizer;
import com.google.idea.blaze.base.filecache.FileCacheSynchronizerTraits;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.model.BlazeLibrary;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.PrintOutput;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.google.idea.blaze.base.sync.libraries.BlazeLibraryCollector;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.ZipUtil;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * Local copy of unzipped AARs that are part of a project's libraries. Updated whenever the original
 * AAR is changed. Unpacked AARs are directories with many files. {@see
 * https://developer.android.com/studio/projects/android-library.html#aar-contents}, for a subset of
 * the contents (documentation may be outdated).
 *
 * <p>The IDE wants at least the following:
 *
 * <ul>
 *   <li>the res/ folder
 *   <li>the R.txt file adjacent to the res/ folder
 *   <li>See {@link com.android.tools.idea.res.aar.AarSourceResourceRepository} for the dependency
 *       on R.txt.
 *   <li>jars: we use the merged output jar from Bazel instead of taking jars from the AAR. It
 *       should be placed in a jars/ folder adjacent to the res/ folder. See {@link
 *       org.jetbrains.android.uipreview.ModuleClassLoader}, for that possible assumption.
 *   <li>The IDE may want the AndroidManifest.xml as well.
 * </ul>
 */
public class UnpackedAars {
  private static final Logger logger = Logger.getInstance(UnpackedAars.class);

  private final File cacheDir;
  private final boolean isUnitTestMode;

  @Nullable private AarTraits aarTraits;
  @Nullable private JarTraits jarTraits;

  public static UnpackedAars getInstance(Project project) {
    return ServiceManager.getService(project, UnpackedAars.class);
  }

  public UnpackedAars(Project project) {
    BlazeImportSettings importSettings =
        BlazeImportSettingsManager.getInstance(project).getImportSettings();
    this.cacheDir = getCacheDir(importSettings);
    // We want this to be isUnitTestMode in normal operation, so there's no user setting.
    isUnitTestMode = ApplicationManager.getApplication().isUnitTestMode();
  }

  void onSync(
      BlazeContext context,
      ProjectViewSet projectViewSet,
      BlazeProjectData projectData,
      SyncMode syncMode) {
    Collection<BlazeLibrary> libraries =
        BlazeLibraryCollector.getLibraries(projectViewSet, projectData);
    boolean fullRefresh = syncMode == SyncMode.FULL;
    boolean removeMissingFiles = syncMode == SyncMode.INCREMENTAL;
    if (isUnitTestMode || fullRefresh) {
      clearCache();
    }
    if (isUnitTestMode) {
      return;
    }

    List<AarLibrary> aarLibraries =
        libraries.stream()
            .filter(library -> library instanceof AarLibrary)
            .map(library -> (AarLibrary) library)
            .collect(Collectors.toList());

    ArtifactLocationDecoder artifactLocationDecoder = projectData.getArtifactLocationDecoder();
    BiMap<File, String> sourceAarFileToCacheKey = HashBiMap.create(aarLibraries.size());
    BiMap<File, String> sourceJarFileToCacheKey = HashBiMap.create(aarLibraries.size());
    for (AarLibrary library : aarLibraries) {
      File aarFile = artifactLocationDecoder.decode(library.aarArtifact);
      String cacheKey = cacheKeyForAar(aarFile);
      sourceAarFileToCacheKey.put(aarFile, cacheKey);
      File jarFile =
          artifactLocationDecoder.decode(library.libraryArtifact.jarForIntellijLibrary());
      // Use the aar key for the jar as well.
      sourceJarFileToCacheKey.put(jarFile, cacheKey);
    }
    this.aarTraits = new AarTraits(cacheDir, sourceAarFileToCacheKey);
    this.jarTraits = new JarTraits(cacheDir, sourceJarFileToCacheKey);

    refresh(context, removeMissingFiles);
  }

  /** Refreshes any updated files in the cache. Does not add or remove any files */
  void refresh(BlazeContext context) {
    refresh(context, false);
  }

  private void refresh(BlazeContext context, boolean removeMissingFiles) {
    if (isUnitTestMode || jarTraits == null || aarTraits == null) {
      return;
    }
    FileOperationProvider fileOpProvider = FileOperationProvider.getInstance();

    // Ensure the cache dir exists
    if (!fileOpProvider.exists(cacheDir)) {
      if (!fileOpProvider.mkdirs(cacheDir)) {
        logger.warn("Could not create unpacked AAR directory: " + cacheDir);
        return;
      }
    }

    try {
      FileCacheSynchronizer aarSynchronizer = new FileCacheSynchronizer(aarTraits);
      aarSynchronizer.synchronize(context, removeMissingFiles);
      FileCacheSynchronizer aarJarSynchronizer = new FileCacheSynchronizer(jarTraits);
      aarJarSynchronizer.synchronize(context, removeMissingFiles);
    } catch (InterruptedException e) {
      context.setCancelled();
      Thread.currentThread().interrupt();
    } catch (ExecutionException e) {
      logger.warn("Unpacked AAR synchronization didn't complete", e);
    }
  }

  /** Returns the merged jar derived from an AAR, in the unpacked AAR directory. */
  public File getClassJar(ArtifactLocationDecoder decoder, AarLibrary library) {
    File file = decoder.decode(library.libraryArtifact.jarForIntellijLibrary());
    if (isUnitTestMode || jarTraits == null) {
      return file;
    }
    String cacheKey = jarTraits.sourceFileToCacheKey(file);
    if (cacheKey == null) {
      return file;
    }
    return jarTraits.cacheFileForKey(cacheKey);
  }

  /** Returns the res/ directory corresponding to an unpacked AAR file. */
  @Nullable
  public File getResourceDirectory(ArtifactLocationDecoder decoder, AarLibrary library) {
    File aarFile = decoder.decode(library.aarArtifact);
    // Provide a resource directory as <aarFile>/res for unit test.
    if (isUnitTestMode) {
      return new File(aarFile, SdkConstants.FD_RES);
    }
    if (aarTraits == null) {
      return null;
    }
    String cacheKey = aarTraits.sourceFileToCacheKey(aarFile);
    if (cacheKey == null) {
      return null;
    }
    File cacheFile = aarTraits.cacheFileForKey(cacheKey);
    File cacheDirectory = aarTraits.cacheDirectoryForCacheFile(cacheFile);
    if (cacheDirectory == null) {
      return null;
    }
    return new File(cacheDirectory, SdkConstants.FD_RES);
  }

  private void clearCache() {
    FileOperationProvider fileOperationProvider = FileOperationProvider.getInstance();
    if (fileOperationProvider.exists(cacheDir)) {
      try {
        fileOperationProvider.deleteRecursively(cacheDir);
      } catch (IOException e) {
        logger.warn("Failed to clear unpacked AAR directory: " + cacheDir, e);
      }
    }
    aarTraits = null;
    jarTraits = null;
  }

  private static String cacheKeyForAar(File aar) {
    return cacheKeyInternal(aar) + SdkConstants.DOT_AAR;
  }

  private static String cacheKeyInternal(File aar) {
    long hash = aar.getParent().hashCode();
    try {
      hash = asByteSource(aar).hash(Hashing.farmHashFingerprint64()).asLong();
    } catch (IOException e) {
      logger.warn("Fail to calculate checksum of file " + aar, e);
    }
    return FileUtil.getNameWithoutExtension(aar) + "_" + Long.toHexString(hash);
  }

  private static File getCacheDir(BlazeImportSettings importSettings) {
    return new File(BlazeDataStorage.getProjectDataDir(importSettings), "aar_libraries");
  }

  static class FileCacheAdapter implements FileCache {
    @Override
    public String getName() {
      return "Unpacked AAR libraries";
    }

    @Override
    public void onSync(
        Project project,
        BlazeContext context,
        ProjectViewSet projectViewSet,
        BlazeProjectData projectData,
        SyncMode syncMode) {
      getInstance(project).onSync(context, projectViewSet, projectData, syncMode);
    }

    @Override
    public void refreshFiles(Project project, BlazeContext context) {
      getInstance(project).refresh(context);
    }
  }

  /**
   * Traits for synchronizing .aar file vs an unpacked aar directory. So the timestamps of the file
   * vs the directory will be synchronized.
   *
   * <p>The representative of the AAR for timestamping will be a stamp file. We use a stamp file
   * instead of the directory itself to stash the timestamp. Directory timestamps are bit more
   * brittle and can change whenever an operation is done to a child of the directory. E.g., if
   * {@link JarTraits} copies a jar into the directory.
   */
  static class AarTraits implements FileCacheSynchronizerTraits {

    private static final String STAMP_FILE_NAME = "aar.timestamp";
    private final File cacheDir;
    private final BiMap<File, String> sourceFileToCacheKey;
    private final FileOperationProvider fileOpProvider;

    AarTraits(File cacheDir, BiMap<File, String> sourceFileToCacheKey) {
      this.cacheDir = cacheDir;
      this.sourceFileToCacheKey = sourceFileToCacheKey;
      this.fileOpProvider = FileOperationProvider.getInstance();
    }

    @Override
    public Collection<File> sourceFiles() {
      return sourceFileToCacheKey.keySet();
    }

    @Override
    public String sourceFileToCacheKey(File sourceFile) {
      return sourceFileToCacheKey.get(sourceFile);
    }

    @Override
    public Collection<File> enumerateCacheFiles() {
      // Go through all of the aar directories, and get the stamp file.
      File[] unpackedAarDirectories = fileOpProvider.listFiles(cacheDir);
      Preconditions.checkNotNull(unpackedAarDirectories);
      ImmutableList.Builder<File> stampFiles = ImmutableList.builder();
      for (File aarDirectory : unpackedAarDirectories) {
        stampFiles.add(new File(aarDirectory, STAMP_FILE_NAME));
      }
      return stampFiles.build();
    }

    @Override
    public String cacheFileToCacheKey(File cacheFile) {
      // Cache key == cache subdirectory name (but the file will be stamp file)
      File cacheDirectory = cacheFile.getParentFile();
      return cacheDirectory.getName();
    }

    @Override
    public File cacheFileForKey(String key) {
      return new File(new File(cacheDir, key), STAMP_FILE_NAME);
    }

    File cacheDirectoryForCacheFile(File cacheFile) {
      return cacheFile.getParentFile();
    }

    @Override
    public Collection<ListenableFuture<?>> updateFiles(
        Collection<String> cacheKeys, ListeningExecutorService executor) {
      List<ListenableFuture<?>> futures = new ArrayList<>();
      Map<String, File> cacheKeyToSourceFile = sourceFileToCacheKey.inverse();
      for (String cacheKey : cacheKeys) {
        File sourceFile = cacheKeyToSourceFile.get(cacheKey);
        File cacheFile = cacheFileForKey(cacheKey);
        futures.add(executor.submit(() -> unpackAar(fileOpProvider, sourceFile, cacheFile)));
      }
      return futures;
    }

    private void unpackAar(
        FileOperationProvider fileOperationProvider, File sourceFile, File cacheFile) {
      File cacheDirectory = cacheDirectoryForCacheFile(cacheFile);
      try {
        if (fileOperationProvider.exists(cacheDirectory)) {
          // NOTE: this forces AAR synchronizer to run before the Jar synchronizer (which puts
          // files within the cacheDirectory).
          fileOperationProvider.deleteRecursively(cacheDirectory);
        }
        ZipUtil.extract(
            sourceFile,
            cacheDirectory,
            // Skip jars. The merged jar will be synchronized by JarTraits.
            (dir, name) -> !name.endsWith(".jar"));
        createStampFile(fileOperationProvider, sourceFile, cacheFile);
      } catch (IOException e) {
        logger.warn(String.format("Failed to extract AAR %s to %s", sourceFile, cacheDirectory), e);
      }
    }

    private void createStampFile(
        FileOperationProvider fileOperationProvider, File sourceFile, File stampFile) {
      long sourceTime = fileOperationProvider.getFileModifiedTime(sourceFile);
      String warningMessage =
          String.format(
              "Failed to set AAR stamp file last modified time (%s, %s)", stampFile, sourceTime);
      try {
        if (!stampFile.createNewFile()) {
          logger.warn(warningMessage);
          return;
        }
        if (!fileOperationProvider.setFileModifiedTime(stampFile, sourceTime)) {
          logger.warn(warningMessage);
        }
      } catch (IOException e) {
        logger.warn(warningMessage, e);
      }
    }

    @Override
    public Collection<ListenableFuture<?>> removeFiles(
        Collection<String> cacheKeys, ListeningExecutorService executor) {
      List<ListenableFuture<?>> futures = new ArrayList<>();
      for (String cacheKey : cacheKeys) {
        File cacheFile = cacheFileForKey(cacheKey);
        File cacheDirectory = cacheDirectoryForCacheFile(cacheFile);
        futures.add(
            executor.submit(
                () -> {
                  try {
                    fileOpProvider.deleteRecursively(cacheDirectory);
                  } catch (IOException e) {
                    logger.warn("Failed to delete old AAR directory " + cacheDirectory, e);
                  }
                }));
      }
      return futures;
    }

    @Override
    public void logStats(
        BlazeContext context,
        int numUpdatedFiles,
        int numRemovedFiles,
        boolean removeMissingFiles) {
      if (numUpdatedFiles > 0) {
        context.output(PrintOutput.log(String.format("Copied %s AARs", numUpdatedFiles)));
      }
      if (numRemovedFiles > 0 && removeMissingFiles) {
        context.output(PrintOutput.log(String.format("Removed %d AARs", numRemovedFiles)));
      }
    }
  }

  /** Traits for a synchronizing a .jar file which is derived from an .aar. */
  static class JarTraits implements FileCacheSynchronizerTraits {
    private final File cacheDir;
    private final BiMap<File, String> sourceFileToCacheKey;
    private final FileOperationProvider fileOpProvider;

    JarTraits(File cacheDir, BiMap<File, String> sourceFileToCacheKey) {
      this.cacheDir = cacheDir;
      this.sourceFileToCacheKey = sourceFileToCacheKey;
      this.fileOpProvider = FileOperationProvider.getInstance();
    }

    @Override
    public Collection<File> sourceFiles() {
      return sourceFileToCacheKey.keySet();
    }

    @Override
    public String sourceFileToCacheKey(File sourceFile) {
      return sourceFileToCacheKey.get(sourceFile);
    }

    @Override
    public Collection<File> enumerateCacheFiles() {
      // Go through all of the aar directories, and dive into the jar/*.jar
      File[] unpackedAarDirectories = fileOpProvider.listFiles(cacheDir);
      Preconditions.checkNotNull(unpackedAarDirectories);
      ImmutableList.Builder<File> jarFiles = ImmutableList.builder();
      for (File aarDirectory : unpackedAarDirectories) {
        File jarsDirectory = new File(aarDirectory, SdkConstants.FD_JARS);
        File[] jars = jarsDirectory.listFiles();
        if (jars != null) {
          jarFiles.add(jars);
        }
      }
      return jarFiles.build();
    }

    @Override
    public String cacheFileToCacheKey(File cacheFile) {
      // The cache key is the name of the grandparent AAR directory.
      // aar_id/jars/aar_id.jar
      return cacheFile.getParentFile().getParentFile().getName();
    }

    @Override
    public File cacheFileForKey(String key) {
      File aarDirectory = new File(cacheDir, key);
      File jarsDirectory = new File(aarDirectory, SdkConstants.FD_JARS);
      // At this point, we don't know the name of the original jar, but we must give the cache
      // file a name. Just use a name similar to what bazel currently uses, and that conveys
      // the origin of the jar (merged from classes.jar and libs/*.jar).
      return new File(jarsDirectory, "classes_and_libs_merged.jar");
    }

    @Override
    public Collection<ListenableFuture<?>> updateFiles(
        Collection<String> cacheKeys, ListeningExecutorService executor) {
      List<ListenableFuture<?>> futures = new ArrayList<>();
      Map<String, File> cacheKeyToSourceFile = sourceFileToCacheKey.inverse();
      for (String cacheKey : cacheKeys) {
        File sourceFile = cacheKeyToSourceFile.get(cacheKey);
        File cacheFile = cacheFileForKey(cacheKey);
        futures.add(
            executor.submit(
                () -> {
                  try {
                    fileOpProvider.mkdirs(cacheFile.getParentFile());
                    Files.copy(
                        Paths.get(sourceFile.getPath()),
                        Paths.get(cacheFile.getPath()),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.COPY_ATTRIBUTES);
                  } catch (IOException e) {
                    logger.warn(e);
                  }
                }));
      }
      return futures;
    }

    @Override
    public Collection<ListenableFuture<?>> removeFiles(
        Collection<String> cacheKeys, ListeningExecutorService executor) {
      List<ListenableFuture<?>> futures = new ArrayList<>();
      for (String cacheKey : cacheKeys) {
        File cacheFile = cacheFileForKey(cacheKey);
        futures.add(
            executor.submit(
                () -> {
                  try {
                    Files.deleteIfExists(Paths.get(cacheFile.getPath()));
                  } catch (IOException e) {
                    logger.warn(e);
                  }
                }));
      }
      return futures;
    }

    @Override
    public void logStats(
        BlazeContext context,
        int numUpdatedFiles,
        int numRemovedFiles,
        boolean removeMissingFiles) {
      if (numUpdatedFiles > 0) {
        context.output(PrintOutput.log(String.format("Copied %d AAR jars", numUpdatedFiles)));
      }
      if (numRemovedFiles > 0 && removeMissingFiles) {
        context.output(PrintOutput.log(String.format("Removed %d AAR jars", numRemovedFiles)));
      }
    }
  }
}
