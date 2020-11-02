/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.java.libraries;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.async.FutureUtil;
import com.google.idea.blaze.base.command.buildresult.BlazeArtifact;
import com.google.idea.blaze.base.command.buildresult.BlazeArtifact.LocalFileArtifact;
import com.google.idea.blaze.base.command.buildresult.OutputArtifact;
import com.google.idea.blaze.base.command.buildresult.RemoteOutputArtifact;
import com.google.idea.blaze.base.command.buildresult.SourceArtifact;
import com.google.idea.blaze.base.filecache.FileCache;
import com.google.idea.blaze.base.filecache.FileCacheDiffer;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.io.FileSizeScanner;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.RemoteOutputArtifacts;
import com.google.idea.blaze.base.prefetch.FetchExecutor;
import com.google.idea.blaze.base.prefetch.RemoteArtifactPrefetcher;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.output.PrintOutput;
import com.google.idea.blaze.base.scope.scopes.TimingScope.EventType;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.libraries.BlazeLibraryCollector;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.java.settings.BlazeJavaUserSettings;
import com.google.idea.blaze.java.sync.model.BlazeJarLibrary;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.PathUtil;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** Local cache of the jars referenced by the project. */
public class JarCache {

  public static JarCache getInstance(Project project) {
    return ServiceManager.getService(project, JarCache.class);
  }

  private static File getCacheDir(BlazeImportSettings importSettings) {
    return new File(BlazeDataStorage.getProjectDataDir(importSettings), "libraries");
  }

  private static final Logger logger = Logger.getInstance(JarCache.class);

  private final Project project;
  private final File cacheDir;

  /** The state of the cache as of the last call to {@link #readFileState}. */
  private volatile ImmutableMap<String, File> cacheState = ImmutableMap.of();

  private boolean enabled;

  public JarCache(Project project) {
    BlazeImportSettings importSettings =
        BlazeImportSettingsManager.getInstance(project).getImportSettings();
    this.project = project;
    this.cacheDir = getCacheDir(importSettings);
  }

  public boolean isEnabled() {
    return enabled;
  }

  private boolean updateEnabled() {
    // force-enable the jar cache if syncing remotely
    this.enabled =
        !ApplicationManager.getApplication().isUnitTestMode()
            && (BlazeJavaUserSettings.getInstance().getUseJarCache()
                || Blaze.getBuildSystemProvider(project).syncingRemotely());
    return enabled;
  }

  /** Returns the currently cached files, as well as setting {@link #cacheState}. */
  private ImmutableMap<String, File> readFileState() {
    FileOperationProvider ops = FileOperationProvider.getInstance();
    File[] files =
        cacheDir.listFiles((dir, name) -> ops.isFile(new File(dir, name)) && name.endsWith(".jar"));
    ImmutableMap<String, File> cacheState =
        files == null
            ? ImmutableMap.of()
            : Arrays.stream(files).collect(toImmutableMap(File::getName, f -> f));
    this.cacheState = cacheState;
    return cacheState;
  }

  private void onSync(
      BlazeContext context,
      ProjectViewSet projectViewSet,
      BlazeProjectData projectData,
      @Nullable BlazeProjectData oldProjectData,
      SyncMode syncMode) {
    boolean enabled = updateEnabled();
    if (!enabled) {
      clearCache(context, /* blockOnCompletion= */ false);
      return;
    }
    boolean fullRefresh = syncMode == SyncMode.FULL;
    if (fullRefresh) {
      clearCache(context, /* blockOnCompletion= */ true);
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
      ProjectViewSet projectViewSet,
      BlazeProjectData projectData,
      RemoteOutputArtifacts previousOutputs,
      boolean removeMissingFiles) {
    if (!enabled) {
      return;
    }
    // Ensure the cache dir exists
    if (!cacheDir.exists()) {
      if (!cacheDir.mkdirs()) {
        logger.error("Could not create jar cache directory");
        return;
      }
    }

    ImmutableMap<String, BlazeArtifact> projectState =
        getArtifactsToCache(projectViewSet, projectData);
    ImmutableMap<String, File> cachedFiles = readFileState();
    try {
      Map<String, BlazeArtifact> updated =
          FileCacheDiffer.findUpdatedOutputs(projectState, cachedFiles, previousOutputs);

      List<File> removed = new ArrayList<>();
      if (removeMissingFiles) {
        removed =
            cachedFiles.entrySet().stream()
                .filter(e -> !projectState.containsKey(e.getKey()))
                .map(Map.Entry::getValue)
                .collect(toImmutableList());
      }

      // Prefetch all libraries to local before reading and copying content
      ListenableFuture<?> downloadArtifactsFuture =
          RemoteArtifactPrefetcher.getInstance()
              .downloadArtifacts(
                  /* projectName= */ project.getName(),
                  /* outputArtifacts= */ BlazeArtifact.getRemoteArtifacts(updated.values()));
      FutureUtil.waitForFuture(context, downloadArtifactsFuture)
          .timed("FetchJars", EventType.Prefetching)
          .withProgressMessage("Fetching jar files...")
          .run();

      // update cache files, and remove files if required
      List<ListenableFuture<?>> futures = new ArrayList<>(copyLocally(updated));
      if (removeMissingFiles) {
        futures.addAll(deleteCacheFiles(removed));
      }

      Futures.allAsList(futures).get();
      if (!updated.isEmpty()) {
        context.output(PrintOutput.log(String.format("Copied %d jars", updated.size())));
      }
      if (!removed.isEmpty()) {
        context.output(PrintOutput.log(String.format("Removed %d jars", removed.size())));
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      context.setCancelled();
    } catch (ExecutionException e) {
      logger.warn("Jar Cache synchronization didn't complete", e);
      IssueOutput.warn("Jar Cache synchronization didn't complete").submit(context);
    } finally {
      // update the in-memory record of which files are cached
      ImmutableMap<String, File> state = readFileState();
      logCacheSize(context, state);
    }
  }

  private static void logCacheSize(BlazeContext context, ImmutableMap<String, File> cachedFiles) {
    try {
      ImmutableMap<File, Long> cacheFileSizes = FileSizeScanner.readFilesizes(cachedFiles.values());
      long total = cacheFileSizes.values().stream().mapToLong(x -> x).sum();
      String msg =
          String.format("Total Jar Cache size: %d kB (%d files)", total / 1024, cachedFiles.size());
      context.output(PrintOutput.log(msg));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      context.setCancelled();
    } catch (ExecutionException e) {
      // ignore any errors reading file sizes for logging purposes
    }
  }

  /**
   * Returns a map from cache key to BlazeArtifact, for all the artifacts which should be cached.
   */
  private static ImmutableMap<String, BlazeArtifact> getArtifactsToCache(
      ProjectViewSet projectViewSet, BlazeProjectData projectData) {
    List<LibraryArtifact> jarLibraries =
        BlazeLibraryCollector.getLibraries(projectViewSet, projectData).stream()
            .filter(library -> library instanceof BlazeJarLibrary)
            .map(library -> ((BlazeJarLibrary) library).libraryArtifact)
            .collect(Collectors.toList());

    ArtifactLocationDecoder decoder = projectData.getArtifactLocationDecoder();
    Map<String, BlazeArtifact> newOutputs = new HashMap<>();
    for (LibraryArtifact lib : jarLibraries) {
      BlazeArtifact jar = decoder.resolveOutput(lib.jarForIntellijLibrary());
      newOutputs.put(cacheKeyForJar(jar), jar);

      for (ArtifactLocation sourceJar : lib.getSourceJars()) {
        BlazeArtifact srcJar = decoder.resolveOutput(sourceJar);
        newOutputs.put(cacheKeyForSourceJar(srcJar), srcJar);
      }
    }
    return ImmutableMap.copyOf(newOutputs);
  }

  private Collection<ListenableFuture<?>> copyLocally(Map<String, BlazeArtifact> updated) {
    List<ListenableFuture<?>> futures = new ArrayList<>();
    updated.forEach(
        (key, artifact) ->
            futures.add(
                FetchExecutor.EXECUTOR.submit(
                    () -> {
                      try {
                        copyLocally(artifact, cacheFileForKey(key));
                      } catch (IOException e) {
                        logger.warn(
                            String.format("Fail to copy artifact %s to %s", artifact, cacheDir), e);
                      }
                    })));
    return futures;
  }

  private static void copyLocally(BlazeArtifact output, File destination) throws IOException {
    if (output instanceof LocalFileArtifact) {
      File source = ((LocalFileArtifact) output).getFile();
      Files.copy(
          Paths.get(source.getPath()),
          Paths.get(destination.getPath()),
          StandardCopyOption.REPLACE_EXISTING,
          StandardCopyOption.COPY_ATTRIBUTES);
      return;
    }
    try (InputStream stream = output.getInputStream()) {
      Files.copy(stream, Paths.get(destination.getPath()), StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private Collection<ListenableFuture<?>> deleteCacheFiles(Collection<File> files) {
    return files.stream()
        .map(
            f ->
                FetchExecutor.EXECUTOR.submit(
                    () -> {
                      try {
                        Files.deleteIfExists(Paths.get(f.getPath()));
                      } catch (IOException e) {
                        logger.warn(e);
                      }
                    }))
        .collect(toImmutableList());
  }

  private File cacheFileForKey(String key) {
    return new File(cacheDir, key);
  }

  private void clearCache(BlazeContext context, boolean blockOnCompletion) {
    cacheState = ImmutableMap.of();
    File[] cacheFiles = cacheDir.listFiles();
    if (cacheFiles == null) {
      return;
    }
    Collection<ListenableFuture<?>> futures = deleteCacheFiles(ImmutableList.copyOf(cacheFiles));
    if (!blockOnCompletion) {
      return;
    }
    try {
      Futures.allAsList(futures).get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      context.setCancelled();
    } catch (ExecutionException e) {
      logger.warn("Jar Cache synchronization didn't complete", e);
      IssueOutput.warn("Jar Cache synchronization didn't complete").submit(context);
    }
  }

  /**
   * Gets the cached file for a jar. If it doesn't exist, we return the file from the library, or
   * null if that also can't be accessed locally.
   */
  @Nullable
  public File getCachedJar(ArtifactLocationDecoder decoder, BlazeJarLibrary library) {
    boolean enabled = isEnabled();
    BlazeArtifact artifact = decoder.resolveOutput(library.libraryArtifact.jarForIntellijLibrary());
    if (!enabled) {
      return getFallbackFile(artifact);
    }
    String cacheKey = cacheKeyForJar(artifact);
    return getCacheFile(cacheKey).orElseGet(() -> getFallbackFile(artifact));
  }

  /**
   * Gets the cached file for a source jar. If it doesn't exist, we return the file from the
   * library, or null if that also can't be accessed locally.
   */
  @Nullable
  public File getCachedSourceJar(ArtifactLocationDecoder decoder, ArtifactLocation sourceJar) {
    boolean enabled = isEnabled();
    BlazeArtifact artifact = decoder.resolveOutput(sourceJar);
    if (!enabled) {
      return getFallbackFile(artifact);
    }
    String cacheKey = cacheKeyForSourceJar(artifact);
    return getCacheFile(cacheKey).orElseGet(() -> getFallbackFile(artifact));
  }

  private Optional<File> getCacheFile(String cacheKey) {
    return Optional.ofNullable(cacheState.get(cacheKey));
  }

  /** The file to return if there's no locally cached version. */
  @Nullable
  private static File getFallbackFile(BlazeArtifact output) {
    if (output instanceof RemoteOutputArtifact) {
      // TODO(brendandouglas): copy locally on the fly?
      return null;
    }
    return patchExternalFilePath(((LocalFileArtifact) output).getFile());
  }

  /**
   * A workaround for https://github.com/bazelbuild/intellij/issues/1256. Point external workspace
   * symlinks to the corresponding fixed location.
   */
  private static File patchExternalFilePath(File maybeExternal) {
    String externalString = maybeExternal.toString();
    if (externalString.contains("/external/")
        && !externalString.contains("/bazel-out/")
        && !externalString.contains("/blaze-out/")) {
      return new File(externalString.replaceAll("/execroot.*/external/", "/external/"));
    }
    return maybeExternal;
  }

  private static String cacheKeyInternal(BlazeArtifact output) {
    String key = artifactKey(output);
    String name = FileUtil.getNameWithoutExtension(PathUtil.getFileName(key));
    return name + "_" + Integer.toHexString(key.hashCode());
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

  private static String cacheKeyForJar(BlazeArtifact jar) {
    return cacheKeyInternal(jar) + ".jar";
  }

  private static String cacheKeyForSourceJar(BlazeArtifact srcjar) {
    return cacheKeyInternal(srcjar) + "-src.jar";
  }

  static class FileCacheAdapter implements FileCache {
    @Override
    public String getName() {
      return "Jar Cache";
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
      JarCache cache = getInstance(project);
      cache.updateEnabled();
      cache.readFileState();
    }
  }
}
