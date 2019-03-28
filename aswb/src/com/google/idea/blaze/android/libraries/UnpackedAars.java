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

import com.android.SdkConstants;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.android.sync.model.AarLibrary;
import com.google.idea.blaze.base.command.buildresult.LocalFileOutputArtifact;
import com.google.idea.blaze.base.command.buildresult.OutputArtifact;
import com.google.idea.blaze.base.command.buildresult.RemoteOutputArtifact;
import com.google.idea.blaze.base.filecache.FileCache;
import com.google.idea.blaze.base.filecache.FileCacheDiffer;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.model.BlazeLibrary;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.RemoteOutputArtifacts;
import com.google.idea.blaze.base.prefetch.FetchExecutor;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.PrintOutput;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.libraries.BlazeLibraryCollector;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.io.ZipUtil;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
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

  @Nullable private volatile InMemoryState inMemoryState = null;

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

  private static class AarAndJar {
    private final OutputArtifact aar;
    private final OutputArtifact jar;

    AarAndJar(OutputArtifact aar, OutputArtifact jar) {
      this.aar = aar;
      this.jar = jar;
    }
  }

  /** In-memory state representing the currently-cached files their corresponding artifacts. */
  private static class InMemoryState {
    private final ImmutableMap<String, AarAndJar> projectOutputs;

    InMemoryState(ImmutableMap<String, AarAndJar> projectOutputs) {
      this.projectOutputs = projectOutputs;
    }

    ImmutableMap<String, OutputArtifact> getAarOutputs() {
      return projectOutputs.entrySet().stream()
          .collect(toImmutableMap(Map.Entry::getKey, e -> e.getValue().aar));
    }
  }

  void onSync(
      BlazeContext context,
      ProjectViewSet projectViewSet,
      BlazeProjectData projectData,
      @Nullable BlazeProjectData oldProjectData,
      SyncMode syncMode) {
    boolean fullRefresh = syncMode == SyncMode.FULL;
    if (isUnitTestMode || fullRefresh) {
      clearCache();
    }
    if (isUnitTestMode) {
      return;
    }
    // TODO(brendandouglas): add a mechanism for removing missing files for partial syncs
    boolean removeMissingFiles = syncMode == SyncMode.INCREMENTAL;

    InMemoryState inMemoryState = readState(projectViewSet, projectData);
    this.inMemoryState = inMemoryState;

    refresh(
        context,
        inMemoryState,
        RemoteOutputArtifacts.fromProjectData(oldProjectData),
        removeMissingFiles);
  }

  private void refresh(BlazeContext context, @Nullable BlazeProjectData projectData) {
    InMemoryState inMemoryState = this.inMemoryState;
    if (inMemoryState == null
        || inMemoryState.projectOutputs.values().stream()
            .anyMatch(a -> a.aar instanceof RemoteOutputArtifact)) {
      // if we have remote artifacts, only refresh during sync
      return;
    }
    refresh(context, inMemoryState, RemoteOutputArtifacts.fromProjectData(projectData), false);
  }

  private void refresh(
      BlazeContext context,
      InMemoryState inMemoryState,
      RemoteOutputArtifacts previousOutputs,
      boolean removeMissingFiles) {
    if (isUnitTestMode) {
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

    ImmutableMap<String, File> cacheFiles = readCachedFiles();
    try {
      Set<String> updatedKeys =
          FileCacheDiffer.findUpdatedOutputs(
                  inMemoryState.getAarOutputs(), cacheFiles, previousOutputs)
              .keySet();

      Set<String> removedKeys = new HashSet<>();
      if (removeMissingFiles) {
        removedKeys =
            cacheFiles.entrySet().stream()
                .filter(e -> !inMemoryState.projectOutputs.containsKey(e.getKey()))
                .map(Map.Entry::getKey)
                .collect(toImmutableSet());
      }

      // Update cache files, and remove files if required.
      List<ListenableFuture<?>> futures = new ArrayList<>(copyLocally(inMemoryState, updatedKeys));
      if (removeMissingFiles) {
        futures.addAll(deleteCacheEntries(removedKeys));
      }

      Futures.allAsList(futures).get();
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
    }
  }

  /** Returns the merged jar derived from an AAR, in the unpacked AAR directory. */
  public File getClassJar(ArtifactLocationDecoder decoder, AarLibrary library) {
    InMemoryState inMemoryState = this.inMemoryState;
    OutputArtifact artifact =
        decoder.resolveOutput(library.libraryArtifact.jarForIntellijLibrary());
    if (isUnitTestMode || inMemoryState == null) {
      return getFallbackFile(artifact);
    }
    String cacheKey = cacheKeyForAar(decoder.resolveOutput(library.aarArtifact));
    // check if it was actually cached
    if (!inMemoryState.projectOutputs.containsKey(cacheKey)) {
      return getFallbackFile(artifact);
    }
    return jarFileForKey(cacheKey);
  }

  /** Returns the res/ directory corresponding to an unpacked AAR file. */
  @Nullable
  public File getResourceDirectory(ArtifactLocationDecoder decoder, AarLibrary library) {
    InMemoryState inMemoryState = this.inMemoryState;
    OutputArtifact artifact = decoder.resolveOutput(library.aarArtifact);
    // Provide a resource directory as <aarFile>/res for unit test.
    if (isUnitTestMode) {
      return new File(getFallbackFile(artifact), SdkConstants.FD_RES);
    }
    if (inMemoryState == null) {
      return null;
    }
    String cacheKey = cacheKeyForAar(artifact);
    if (!inMemoryState.projectOutputs.containsKey(cacheKey)) {
      return null;
    }
    return new File(aarDirForKey(cacheKey), SdkConstants.FD_RES);
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
  private static File getFallbackFile(OutputArtifact output) {
    if (output instanceof RemoteOutputArtifact) {
      // TODO(brendandouglas): copy locally on the fly?
      throw new RuntimeException("The AAR cache must be enabled when syncing remotely");
    }
    return ((LocalFileOutputArtifact) output).getFile();
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
    inMemoryState = null;
  }

  private static String cacheKeyForAar(OutputArtifact aar) {
    return cacheKeyInternal(aar) + SdkConstants.DOT_AAR;
  }

  private static String cacheKeyInternal(OutputArtifact output) {
    String key = output.getKey();
    String name = FileUtil.getNameWithoutExtension(PathUtil.getFileName(key));
    return name + "_" + Integer.toHexString(key.hashCode());
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
        @Nullable BlazeProjectData oldProjectData,
        SyncMode syncMode) {
      getInstance(project).onSync(context, projectViewSet, projectData, oldProjectData, syncMode);
    }

    @Override
    public void refreshFiles(Project project, BlazeContext context) {
      BlazeProjectData projectData =
          BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
      getInstance(project).refresh(context, projectData);
    }
  }

  private InMemoryState readState(ProjectViewSet projectViewSet, BlazeProjectData projectData) {
    Collection<BlazeLibrary> libraries =
        BlazeLibraryCollector.getLibraries(projectViewSet, projectData);
    List<AarLibrary> aarLibraries =
        libraries.stream()
            .filter(library -> library instanceof AarLibrary)
            .map(library -> (AarLibrary) library)
            .collect(Collectors.toList());

    ArtifactLocationDecoder decoder = projectData.getArtifactLocationDecoder();
    Map<String, AarAndJar> outputs = new HashMap<>();
    for (AarLibrary library : aarLibraries) {
      OutputArtifact aar = decoder.resolveOutput(library.aarArtifact);
      OutputArtifact jar = decoder.resolveOutput(library.libraryArtifact.jarForIntellijLibrary());
      if (aar == null || jar == null) {
        // this implies either the aar or jar are source artifacts, which should never happen
        logger.warn(
            String.format(
                "Can't resolve AAR output artifacts:\n%s\n%s",
                library.aarArtifact, library.libraryArtifact.jarForIntellijLibrary()));
        continue;
      }
      outputs.put(cacheKeyForAar(aar), new AarAndJar(aar, jar));
    }
    return new InMemoryState(ImmutableMap.copyOf(outputs));
  }

  private static final String STAMP_FILE_NAME = "aar.timestamp";

  /**
   * Returns a map of cache keys for the currently-cached files, along with a representative file
   * used for timestamp-based diffing.
   *
   * <p>We use a stamp file instead of the directory itself to stash the timestamp. Directory
   * timestamps are bit more brittle and can change whenever an operation is done to a child of the
   * directory.
   */
  private ImmutableMap<String, File> readCachedFiles() {
    FileOperationProvider ops = FileOperationProvider.getInstance();
    // Go through all of the aar directories, and get the stamp file.
    File[] unpackedAarDirectories = ops.listFiles(cacheDir);
    if (unpackedAarDirectories == null) {
      return ImmutableMap.of();
    }
    Map<String, File> cachedFiles = new HashMap<>();
    for (File aarDirectory : unpackedAarDirectories) {
      cachedFiles.put(aarDirectory.getName(), new File(aarDirectory, STAMP_FILE_NAME));
    }
    return ImmutableMap.copyOf(cachedFiles);
  }

  private Collection<ListenableFuture<?>> copyLocally(
      InMemoryState inMemoryState, Set<String> updatedKeys) {
    FileOperationProvider ops = FileOperationProvider.getInstance();
    List<ListenableFuture<?>> futures = new ArrayList<>();
    updatedKeys.forEach(
        key ->
            futures.add(
                FetchExecutor.EXECUTOR.submit(
                    () -> copyLocally(ops, inMemoryState.projectOutputs.get(key)))));
    return futures;
  }

  private void copyLocally(FileOperationProvider ops, AarAndJar aarAndJar) {
    String cacheKey = cacheKeyForAar(aarAndJar.aar);
    File aarDir = aarDirForKey(cacheKey);
    try {
      if (ops.exists(aarDir)) {
        ops.deleteRecursively(aarDir);
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
      try (InputStream stream = aarAndJar.jar.getInputStream()) {
        Files.copy(
            stream,
            Paths.get(jarFileForKey(cacheKey).getPath()),
            StandardCopyOption.REPLACE_EXISTING);
      }

    } catch (IOException e) {
      logger.warn(
          String.format("Failed to extract AAR %s to %s", aarAndJar.aar.getKey(), aarDir), e);
    }
  }

  private void createStampFile(FileOperationProvider fileOps, File aarDir, OutputArtifact aar) {
    File stampFile = new File(aarDir, STAMP_FILE_NAME);
    try {
      stampFile.createNewFile();
      if (!(aar instanceof LocalFileOutputArtifact)) {
        // no need to set the timestamp for remote artifacts
        return;
      }
      long sourceTime = fileOps.getFileModifiedTime(((LocalFileOutputArtifact) aar).getFile());
      if (!fileOps.setFileModifiedTime(stampFile, sourceTime)) {
        logger.warn("Failed to set AAR cache timestamp for " + aar.getKey());
      }
    } catch (IOException e) {
      logger.warn("Failed to set AAR cache timestamp for " + aar.getKey(), e);
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
                        ops.deleteRecursively(aarDirForKey(key));
                      } catch (IOException e) {
                        logger.warn(e);
                      }
                    }))
        .collect(toImmutableList());
  }

  /** Returns a locally-accessible file mirroring the contents of this {@link OutputArtifact}. */
  private static File getOrCreateLocalFile(OutputArtifact artifact) throws IOException {
    if (artifact instanceof LocalFileOutputArtifact) {
      return ((LocalFileOutputArtifact) artifact).getFile();
    }
    File tmpFile =
        FileUtil.createTempFile(
            "local-aar-file",
            Integer.toHexString(artifact.getKey().hashCode()),
            /* deleteOnExit= */ true);
    try (InputStream stream = artifact.getInputStream()) {
      Files.copy(stream, Paths.get(tmpFile.getPath()), StandardCopyOption.REPLACE_EXISTING);
      return tmpFile;
    }
  }
}
