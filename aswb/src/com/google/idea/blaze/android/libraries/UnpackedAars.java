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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.idea.blaze.android.sync.model.MergedAarLibrary.mergeAarLibraries;

import com.android.SdkConstants;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.android.sync.model.AarLibrary;
import com.google.idea.blaze.android.sync.model.MergedAarLibrary;
import com.google.idea.blaze.base.async.FutureUtil;
import com.google.idea.blaze.base.command.buildresult.BlazeArtifact;
import com.google.idea.blaze.base.command.buildresult.BlazeArtifact.LocalFileArtifact;
import com.google.idea.blaze.base.command.buildresult.OutputArtifact;
import com.google.idea.blaze.base.command.buildresult.RemoteOutputArtifact;
import com.google.idea.blaze.base.command.buildresult.SourceArtifact;
import com.google.idea.blaze.base.filecache.FileCache;
import com.google.idea.blaze.base.filecache.FileCacheDiffer;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.model.BlazeLibrary;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.RemoteOutputArtifacts;
import com.google.idea.blaze.base.prefetch.FetchExecutor;
import com.google.idea.blaze.base.prefetch.RemoteArtifactPrefetcher;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.PrintOutput;
import com.google.idea.blaze.base.scope.scopes.TimingScope.EventType;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.libraries.BlazeLibraryCollector;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.io.ZipUtil;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 *   <li>See {@link com.android.tools.idea.resources.aar.AarSourceResourceRepository} for the
 *       dependency on R.txt.
 *   <li>jars: we use the merged output jar from Bazel instead of taking jars from the AAR. It
 *       should be placed in a jars/ folder adjacent to the res/ folder. See {@link
 *       org.jetbrains.android.uipreview.ModuleClassLoader}, for that possible assumption.
 *   <li>The IDE may want the AndroidManifest.xml as well.
 * </ul>
 */
public class UnpackedAars {
  private static final Logger logger = Logger.getInstance(UnpackedAars.class);
  private static final String DOT_MERGED_AAR = ".merged.aar";

  private final Project project;
  private final File cacheDir;

  /** The state of the single aar cache as of the last call to {@link #readFileState}. */
  private volatile ImmutableMap<String, File> singleAarCacheState = ImmutableMap.of();

  /** All merged aar library directories as of the last call to {@link #readFileState}. */
  private volatile ImmutableSet<String> mergedAarCacheKey = ImmutableSet.of();

  public static UnpackedAars getInstance(Project project) {
    return ServiceManager.getService(project, UnpackedAars.class);
  }

  public UnpackedAars(Project project) {
    BlazeImportSettings importSettings =
        BlazeImportSettingsManager.getInstance(project).getImportSettings();
    this.project = project;
    this.cacheDir = getCacheDir(importSettings);
  }

  @VisibleForTesting
  public File getCacheDir() {
    return this.cacheDir;
  }

  private static File getCacheDir(BlazeImportSettings importSettings) {
    return new File(BlazeDataStorage.getProjectDataDir(importSettings), "aar_libraries");
  }

  private static class AarAndJar {
    // The merged directory to be copied to. If it's null, this is a single aar that should not be
    // merged in any case
    @Nullable private String mergedAarCacheDir;
    private final BlazeArtifact aar;
    @Nullable private final BlazeArtifact jar;

    AarAndJar(@Nullable String mergedAarCacheDir, BlazeArtifact aar, @Nullable BlazeArtifact jar) {
      this.mergedAarCacheDir = mergedAarCacheDir;
      this.aar = aar;
      this.jar = jar;
    }
  }

  void onSync(
      BlazeContext context,
      ProjectViewSet projectViewSet,
      BlazeProjectData projectData,
      @Nullable BlazeProjectData oldProjectData,
      SyncMode syncMode) {
    boolean fullRefresh = syncMode == SyncMode.FULL;
    if (fullRefresh) {
      clearCache();
    }

    // TODO(brendandouglas): add a mechanism for removing missing files for partial syncs
    boolean removeMissingFiles = syncMode == SyncMode.INCREMENTAL;
    refresh(
        context,
        projectViewSet,
        projectData,
        RemoteOutputArtifacts.fromProjectData(oldProjectData),
        removeMissingFiles);
  }

  private void refresh(
      BlazeContext context,
      ProjectViewSet viewSet,
      BlazeProjectData projectData,
      RemoteOutputArtifacts previousOutputs,
      boolean removeMissingFiles) {
    FileOperationProvider fileOpProvider = FileOperationProvider.getInstance();

    // Ensure the cache dir exists
    if (!fileOpProvider.exists(cacheDir)) {
      if (!fileOpProvider.mkdirs(cacheDir)) {
        logger.warn("Could not create unpacked AAR directory: " + cacheDir);
        return;
      }
    }

    ImmutableMap<String, File> cacheFiles = readFileState();
    ImmutableMap<String, AarAndJar> projectState = getArtifactsToCache(viewSet, projectData);
    ImmutableMap<String, BlazeArtifact> aarOutputs =
        projectState.entrySet().stream()
            .collect(toImmutableMap(Map.Entry::getKey, e -> e.getValue().aar));
    try {

      Set<String> updatedKeys =
          FileCacheDiffer.findUpdatedOutputs(aarOutputs, cacheFiles, previousOutputs).keySet();
      Set<BlazeArtifact> artifactsToDownload = new HashSet<>();

      for (String key : updatedKeys) {
        artifactsToDownload.add(projectState.get(key).aar);
        BlazeArtifact jar = projectState.get(key).jar;
        // jar file is introduced as a separate artifact (not jar in aar) which asks to download
        // separately. Only update jar when we decide that aar need to be updated.
        if (jar != null) {
          artifactsToDownload.add(jar);
        }
      }

      Set<String> removedKeys = new HashSet<>();
      if (removeMissingFiles) {
        removedKeys =
            cacheFiles.keySet().stream()
                .filter(file -> !projectState.containsKey(file))
                .collect(toImmutableSet());
      }

      // Prefetch all libraries to local before reading and copying content
      ListenableFuture<?> downloadArtifactsFuture =
          RemoteArtifactPrefetcher.getInstance()
              .downloadArtifacts(
                  /* projectName= */ project.getName(),
                  /* outputArtifacts= */ BlazeArtifact.getRemoteArtifacts(artifactsToDownload));

      FutureUtil.waitForFuture(context, downloadArtifactsFuture)
          .timed("FetchAars", EventType.Prefetching)
          .withProgressMessage("Fetching aar files...")
          .run();

      // update cache files, and remove files if required
      List<ListenableFuture<?>> futures = new ArrayList<>(copyLocally(projectState, updatedKeys));
      if (removeMissingFiles) {
        futures.addAll(deleteCacheEntries(removedKeys));
      }

      Futures.allAsList(futures).get();

      // recreate merged aar directories after ever refresh. According to our experiment. The copy
      // should take less than 2s, so we recreate it even during no_build sync.
      if (mergeAarLibraries.getValue()) {
        recreateMergedAarDirs(fileOpProvider, projectState);
      }
      if (!updatedKeys.isEmpty()) {
        context.output(PrintOutput.log(String.format("Copied %d AARs", updatedKeys.size())));
      }
      if (!removedKeys.isEmpty()) {
        context.output(PrintOutput.log(String.format("Removed %d AARs", removedKeys.size())));
      }

    } catch (InterruptedException e) {
      context.setCancelled();
      Thread.currentThread().interrupt();
    } catch (ExecutionException e) {
      logger.warn("Unpacked AAR synchronization didn't complete", e);
    } finally {
      // update the in-memory record of which files are cached
      readFileState();
    }
  }

  private void recreateMergedAarDirs(
      FileOperationProvider ops, ImmutableMap<String, AarAndJar> projectState) {
    Stopwatch stopwatch = Stopwatch.createStarted();
    // remove all existing .merged.aar directory
    for (String key : mergedAarCacheKey) {
      File aar = aarDirForKey(key);
      try {
        ops.deleteRecursively(aar, /* allowInsecureDelete= */ SystemInfo.isMac);
      } catch (IOException e) {
        logger.warn("Fail to remove " + aar, e);
      }
    }

    int resourceFileCounter = 0;
    long resourceFileSize = 0;
    int mergedAarLibraryCounter = 0;
    // recreate merged aar directories
    for (AarAndJar aarAndJar : projectState.values()) {
      if (aarAndJar.mergedAarCacheDir == null) {
        continue;
      }
      Path mergedAarDir = aarDirForKey(aarAndJar.mergedAarCacheDir).toPath();
      if (!Files.exists(mergedAarDir)) {
        try {
          ops.mkdirs(mergedAarDir);
        } catch (IOException e) {
          logger.warn("Fail to create merged aar directory " + mergedAarDir, e);
          continue;
        }
      }
      // path to cached single aar
      Path cachedSingleAar = aarDirForKey(cacheKeyForSingleAar(aarAndJar.aar)).toPath();
      for (Path resourceFile : ops.listFilesRecursively(cachedSingleAar)) {
        // find the relative path to created in merged aar dir
        Path dest = mergedAarDir.resolve(cachedSingleAar.relativize(resourceFile));
        try {
          if (Files.exists(dest)) {
            ops.deleteRecursively(dest, /* allowInsecureDelete= */ SystemInfo.isMac);
          } else {
            ops.mkdirs(dest.getParent());
          }
          ops.copy(resourceFile, dest);
          resourceFileCounter++;
          resourceFileSize += Files.size(resourceFile);
        } catch (IOException e) {
          logger.warn(
              "Fail to copy source file " + resourceFile + " to merged aar directory " + dest, e);
        }
      }
      mergedAarLibraryCounter++;
    }
    logger.info(
        "Merged "
            + resourceFileCounter
            + "resource files ("
            + resourceFileSize / 1024
            + "kB) into "
            + mergedAarLibraryCounter
            + " aar libraries in "
            + stopwatch.elapsed().getSeconds()
            + " seconds");
  }

  /** Returns the merged jar derived from an AAR, in the unpacked AAR directory. */
  @Nullable
  public File getClassJar(ArtifactLocationDecoder decoder, AarLibrary library) {
    if (library.libraryArtifact == null) {
      return null;
    }
    ImmutableMap<String, File> cacheState = this.singleAarCacheState;
    BlazeArtifact artifact = decoder.resolveOutput(library.libraryArtifact.jarForIntellijLibrary());
    if (cacheState.isEmpty()) {
      logger.warn("Cache state is empty");
      return getFallbackFile(artifact);
    }
    String cacheKey = cacheKeyForSingleAar(decoder.resolveOutput(library.aarArtifact));
    // check if it was actually cached
    if (!cacheState.containsKey(cacheKey)) {
      // if artifact is RemoteOutputArtifact, cacheState is expected to contains cacheKey. So it's
      // unexpected when it runs into this case.
      if (artifact instanceof RemoteOutputArtifact) {
        logger.warn(
            String.format(
                "Fail to look up %s from cache state for library [aarArtifact = %s, jar = %s]",
                cacheKey, decoder.resolveOutput(library.aarArtifact), artifact));
        logger.debug("Cache state contains the following keys: " + cacheState.keySet());
      }
      return getFallbackFile(artifact);
    }
    return jarFileForKey(cacheKey);
  }

  /** Returns the res/ directory corresponding to an unpacked AAR file. */
  @Nullable
  public File getResourceDirectory(ArtifactLocationDecoder decoder, MergedAarLibrary library) {
    File aarDir = getMergedAarDir(decoder, library);
    if (aarDir == null) {
      return aarDir;
    }
    return new File(aarDir, SdkConstants.FD_RES);
  }

  /** Returns the res/ directory corresponding to an unpacked AAR file. */
  @Nullable
  public File getResourceDirectory(ArtifactLocationDecoder decoder, AarLibrary library) {
    File aarDir = getSingleAarDir(decoder, library);
    if (aarDir == null) {
      return aarDir;
    }
    return new File(aarDir, SdkConstants.FD_RES);
  }

  /**
   * Return the merged cache directory for {@link MergedAarLibrary} if it merged more than one aar
   * library. Otherwise, return that directory for the single {@link AarLibrary}.
   */
  @Nullable
  public File getMergedAarDir(ArtifactLocationDecoder decoder, MergedAarLibrary library) {
    if (library.useSingleAar()) {
      return getSingleAarDir(decoder, library.aars.get(0));
    }

    String cacheKey = cacheKeyForMergedAar(library.key.toString());
    return getMergedAarDir(cacheKey);
  }

  /** Return path to merged aar directory if that directory exists. Otherwise, return null. */
  @Nullable
  public File getMergedAarDir(String cacheKey) {
    ImmutableSet<String> cacheState = this.mergedAarCacheKey;
    if (!cacheState.contains(cacheKey)) {
      return null;
    }
    return aarDirForKey(cacheKey);
  }

  /** Return the cache directory for {@link AarLibrary} */
  @Nullable
  public File getSingleAarDir(ArtifactLocationDecoder decoder, AarLibrary library) {
    BlazeArtifact artifact = decoder.resolveOutput(library.aarArtifact);
    String cacheKey = cacheKeyForSingleAar(artifact);
    return getSingleAarDir(cacheKey);
  }

  /** Return path to the cached aar directory if that directory exists. Otherwise, return null. */
  @Nullable
  public File getSingleAarDir(String cacheKey) {
    ImmutableMap<String, File> cacheState = this.singleAarCacheState;
    if (!cacheState.containsKey(cacheKey)) {
      return null;
    }
    return aarDirForKey(cacheKey);
  }

  private File aarDirForKey(String key) {
    return new File(cacheDir, key);
  }

  private File jarFileForKey(String key) {
    File jarsDirectory = new File(aarDirForKey(key), SdkConstants.FD_JARS);
    // At this point, we don't know the name of the original jar, but we must give the cache
    // file a name. Just use a name similar to what bazel currently uses, and that conveys
    // the origin of the jar (merged from classes.jar and libs/*.jar).
    return new File(jarsDirectory, "classes_and_libs_merged.jar");
  }

  /** The file to return if there's no locally cached version. */
  private static File getFallbackFile(BlazeArtifact output) {
    if (output instanceof RemoteOutputArtifact) {
      // TODO(brendandouglas): copy locally on the fly?
      throw new RuntimeException("The AAR cache must be enabled when syncing remotely");
    }
    return ((LocalFileArtifact) output).getFile();
  }

  private void clearCache() {
    FileOperationProvider fileOperationProvider = FileOperationProvider.getInstance();
    if (fileOperationProvider.exists(cacheDir)) {
      try {
        fileOperationProvider.deleteRecursively(cacheDir, true);
      } catch (IOException e) {
        logger.warn("Failed to clear unpacked AAR directory: " + cacheDir, e);
      }
    }
    singleAarCacheState = ImmutableMap.of();
    mergedAarCacheKey = ImmutableSet.of();
  }

  private static String artifactKey(BlazeArtifact artifact) {
    if (artifact instanceof OutputArtifact) {
      return ((OutputArtifact) artifact).getKey();
    }
    if (artifact instanceof SourceArtifact) {
      return ((SourceArtifact) artifact).getFile().getPath();
    }
    throw new RuntimeException("Unhandled BlazeArtifact type: " + artifact.getClass());
  }

  private static String cacheKeyForSingleAar(BlazeArtifact aar) {
    return cacheKeyForSingleAar(artifactKey(aar));
  }

  // TODO(one-studio): remove this method: tests should pass through a BlazeArtifact instead of
  //  making assumptions about the artifact key format
  @VisibleForTesting
  public static String cacheKeyForSingleAar(String key) {
    return cacheKeyInternal(key) + SdkConstants.DOT_AAR;
  }

  /** cache key for merged {@link MergedAarLibrary} */
  public static String cacheKeyForMergedAar(String key) {
    // do not use cacheKeyInternal since key is package name (i.e. com.foo.bar). We do not want the
    // last extension is removed.
    return key + "_" + Integer.toHexString(key.hashCode()) + DOT_MERGED_AAR;
  }

  private static String cacheKeyInternal(String key) {
    String name = FileUtil.getNameWithoutExtension(PathUtil.getFileName(key));
    return name + "_" + Integer.toHexString(key.hashCode());
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
        @Nullable BlazeProjectData oldProjectData,
        SyncMode syncMode) {
      getInstance(project).onSync(context, projectViewSet, projectData, oldProjectData, syncMode);
    }

    @Override
    public void refreshFiles(Project project, BlazeContext context) {
      ProjectViewSet viewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
      BlazeProjectData projectData =
          BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
      if (viewSet == null || projectData == null || !projectData.getRemoteOutputs().isEmpty()) {
        // if we have remote artifacts, only refresh during sync
        return;
      }
      getInstance(project)
          .refresh(
              context,
              viewSet,
              projectData,
              projectData.getRemoteOutputs(),
              /* removeMissingFiles= */ false);
    }

    @Override
    public void initialize(Project project) {
      getInstance(project).readFileState();
    }
  }

  /**
   * Returns a map from cache key to {@link AarAndJar}, for all the artifacts which should be
   * cached.
   */
  private static ImmutableMap<String, AarAndJar> getArtifactsToCache(
      ProjectViewSet projectViewSet, BlazeProjectData projectData) {
    Collection<BlazeLibrary> libraries =
        BlazeLibraryCollector.getLibraries(projectViewSet, projectData);
    List<MergedAarLibrary> mergedAarLibraries =
        libraries.stream()
            .filter(library -> library instanceof MergedAarLibrary)
            .map(library -> (MergedAarLibrary) library)
            .collect(Collectors.toList());

    ArtifactLocationDecoder decoder = projectData.getArtifactLocationDecoder();
    Map<String, AarAndJar> outputs = new HashMap<>();
    for (MergedAarLibrary mergedAarLibrary : mergedAarLibraries) {
      for (AarLibrary library : mergedAarLibrary.aars) {
        BlazeArtifact aar = decoder.resolveOutput(library.aarArtifact);
        BlazeArtifact jar =
            library.libraryArtifact != null
                ? decoder.resolveOutput(library.libraryArtifact.jarForIntellijLibrary())
                : null;
        outputs.put(
            cacheKeyForSingleAar(aar),
            new AarAndJar(
                mergedAarLibrary.useSingleAar()
                    ? null
                    : cacheKeyForMergedAar(mergedAarLibrary.key.toString()),
                aar,
                jar));
      }
    }
    return ImmutableMap.copyOf(outputs);
  }

  private static final String STAMP_FILE_NAME = "aar.timestamp";

  /**
   * Returns a map of cache keys for the currently-cached files, along with a representative file
   * used for timestamp-based diffing.
   *
   * <p>We use a stamp file instead of the directory itself to stash the timestamp. Directory
   * timestamps are bit more brittle and can change whenever an operation is done to a child of the
   * directory.
   *
   * <p>Also sets the in-memory @link #cacheState}.
   */
  private ImmutableMap<String, File> readFileState() {
    FileOperationProvider ops = FileOperationProvider.getInstance();
    // Go through all of the aar directories, and get the stamp file.
    File[] unpackedAarDirectories = ops.listFiles(cacheDir);
    if (unpackedAarDirectories == null) {
      return ImmutableMap.of();
    }

    ImmutableMap.Builder<String, File> cachedAarFiles = ImmutableMap.builder();
    ImmutableSet.Builder<String> cachedMergedAarFiles = ImmutableSet.builder();
    for (File unpackedAarDirectory : unpackedAarDirectories) {
      if (unpackedAarDirectory.getName().endsWith(DOT_MERGED_AAR)) {
        cachedMergedAarFiles.add(unpackedAarDirectory.getName());
      } else {
        cachedAarFiles.put(
            unpackedAarDirectory.getName(), new File(unpackedAarDirectory, STAMP_FILE_NAME));
      }
    }
    singleAarCacheState = cachedAarFiles.build();
    mergedAarCacheKey = cachedMergedAarFiles.build();
    return singleAarCacheState;
  }

  private Collection<ListenableFuture<?>> copyLocally(
      ImmutableMap<String, AarAndJar> toCache, Set<String> updatedKeys) {
    FileOperationProvider ops = FileOperationProvider.getInstance();
    List<ListenableFuture<?>> futures = new ArrayList<>();
    updatedKeys.forEach(
        key ->
            futures.add(FetchExecutor.EXECUTOR.submit(() -> copyLocally(ops, toCache.get(key)))));
    return futures;
  }

  private void copyLocally(FileOperationProvider ops, AarAndJar aarAndJar) {
    String cacheKey = cacheKeyForSingleAar(aarAndJar.aar);
    File aarDir = aarDirForKey(cacheKey);
    try {
      if (ops.exists(aarDir)) {
        ops.deleteRecursively(aarDir, true);
      }
      ops.mkdirs(aarDir);
      // TODO(brendandouglas): decompress via ZipInputStream so we don't require a local file
      File toCopy = getOrCreateLocalFile(aarAndJar.aar);
      ZipUtil.extract(
          toCopy,
          aarDir,
          // Skip jars. The merged jar will be synchronized by JarTraits.
          (dir, name) -> !name.endsWith(".jar"));

      createStampFile(ops, aarDir, aarAndJar.aar);

      // copy merged jar
      if (aarAndJar.jar != null) {
        try (InputStream stream = aarAndJar.jar.getInputStream()) {
          Path destination = Paths.get(jarFileForKey(cacheKey).getPath());
          ops.mkdirs(destination.getParent().toFile());
          Files.copy(stream, destination, StandardCopyOption.REPLACE_EXISTING);
        }
      }

    } catch (IOException e) {
      logger.warn(String.format("Failed to extract AAR %s to %s", aarAndJar.aar, aarDir), e);
    }
  }

  private static void createStampFile(
      FileOperationProvider fileOps, File aarDir, BlazeArtifact aar) {
    File stampFile = new File(aarDir, STAMP_FILE_NAME);
    try {
      stampFile.createNewFile();
      if (!(aar instanceof LocalFileArtifact)) {
        // no need to set the timestamp for remote artifacts
        return;
      }
      long sourceTime = fileOps.getFileModifiedTime(((LocalFileArtifact) aar).getFile());
      if (!fileOps.setFileModifiedTime(stampFile, sourceTime)) {
        logger.warn("Failed to set AAR cache timestamp for " + aar);
      }
    } catch (IOException e) {
      logger.warn("Failed to set AAR cache timestamp for " + aar, e);
    }
  }

  private Collection<ListenableFuture<?>> deleteCacheEntries(Collection<String> cacheKeys) {
    FileOperationProvider ops = FileOperationProvider.getInstance();
    return cacheKeys.stream()
        .map(
            key ->
                FetchExecutor.EXECUTOR.submit(
                    () -> {
                      try {
                        ops.deleteRecursively(aarDirForKey(key), true);
                      } catch (IOException e) {
                        logger.warn(e);
                      }
                    }))
        .collect(toImmutableList());
  }

  /** Returns a locally-accessible file mirroring the contents of this {@link BlazeArtifact}. */
  private static File getOrCreateLocalFile(BlazeArtifact artifact) throws IOException {
    if (artifact instanceof LocalFileArtifact) {
      return ((LocalFileArtifact) artifact).getFile();
    }
    File tmpFile =
        FileUtil.createTempFile(
            "local-aar-file",
            Integer.toHexString(artifactKey(artifact).hashCode()),
            /* deleteOnExit= */ true);
    try (InputStream stream = artifact.getInputStream()) {
      Files.copy(stream, Paths.get(tmpFile.getPath()), StandardCopyOption.REPLACE_EXISTING);
      return tmpFile;
    }
  }
}
