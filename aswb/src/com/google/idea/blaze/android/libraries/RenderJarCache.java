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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Arrays.stream;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.android.projectsystem.RenderJarClassFileFinder;
import com.google.idea.blaze.android.sync.importer.BlazeImportUtil;
import com.google.idea.blaze.base.async.FutureUtil;
import com.google.idea.blaze.base.command.buildresult.BlazeArtifact;
import com.google.idea.blaze.base.command.buildresult.BlazeArtifact.LocalFileArtifact;
import com.google.idea.blaze.base.command.buildresult.OutputArtifact;
import com.google.idea.blaze.base.command.buildresult.SourceArtifact;
import com.google.idea.blaze.base.filecache.FileCache;
import com.google.idea.blaze.base.filecache.FileCacheDiffer;
import com.google.idea.blaze.base.ideinfo.AndroidIdeInfo;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
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
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.PathUtil;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.jetbrains.annotations.Nullable;

/**
 * Local cache for RenderJars.
 *
 * <p>RenderJARs are used by {@link RenderJarClassFileFinder} to lookup classes for rendering. See
 * {@link RenderJarClassFileFinder} for more information about RenderJARs.
 */
public class RenderJarCache {
  public static RenderJarCache getInstance(Project project) {
    return project.getService(RenderJarCache.class);
  }

  private static File getCacheDirForProject(Project project) {
    BlazeImportSettings importSettings =
        BlazeImportSettingsManager.getInstance(project).getImportSettings();

    if (importSettings == null) {
      throw new IllegalArgumentException(
          String.format("Could not directory for project '%s'", project.getName()));
    }

    return new File(BlazeDataStorage.getProjectDataDir(importSettings), "renderjars");
  }

  private static final Logger logger = Logger.getInstance(RenderJarCache.class);

  private final Project project;
  private final File cacheDir;

  /**
   * The state of cache as last read by {@link #readFileState}. Maps the key of a {@link
   * BlazeArtifact} to a file in local FS. The file name matches the key. This enables us to look up
   * the local File corresponding to a given {@link BlazeArtifact}. See {@link #cacheKeyForJar} for
   * how key for a {@link BlazeArtifact} is generated.
   *
   * <p>Marked `volatile` because it can be updated in one thread after sync/build, and then read by
   * a completely different thread while attempting to fetch RenderJar for a target in quick
   * succession
   */
  private volatile ImmutableMap<String, File> cacheState = ImmutableMap.of();

  public RenderJarCache(Project project) {
    this.project = project;
    this.cacheDir = getCacheDirForProject(project);
  }

  private boolean isEnabled() {
    return RenderJarClassFileFinder.isEnabled();
  }

  @VisibleForTesting
  public File getCacheDir() {
    return cacheDir;
  }

  /**
   * Reads in and sets the cache state from local file system. All files in the cache are named
   * according to the key of the {@link BlazeArtifact} they correspond to. This methods reads in all
   * the files in {@link #cacheDir} and creates a map of key to file.
   *
   * <p>This method is functionally similar to {@link
   * com.google.idea.blaze.java.libraries.JarCache#readFileState}
   */
  private ImmutableMap<String, File> readFileState() {
    FileOperationProvider ops = FileOperationProvider.getInstance();
    File[] files =
        cacheDir.listFiles((dir, name) -> ops.isFile(new File(dir, name)) && name.endsWith(".jar"));
    this.cacheState =
        files == null
            ? ImmutableMap.of()
            : stream(files).collect(ImmutableMap.toImmutableMap(File::getName, f -> f));
    return cacheState;
  }

  private void onSync(
      BlazeContext context,
      ProjectViewSet projectViewSet,
      BlazeProjectData projectData,
      BlazeProjectData oldProjectData,
      SyncMode syncMode) {
    if (!isEnabled()) {
      clearCache(context, false);
      return;
    }
    boolean fullRefresh = syncMode == SyncMode.FULL;
    if (fullRefresh) {
      clearCache(context, true);
    }

    boolean removeMissingFiles = syncMode == SyncMode.INCREMENTAL;

    refresh(
        context,
        projectViewSet,
        projectData,
        RemoteOutputArtifacts.fromProjectData(oldProjectData),
        removeMissingFiles);
  }

  /**
   * This method is called after a sync/build and redownloads the updated artifacts as calculated by
   * comparing {@code projectData} and {@code previousOutputs}. Can optionally remove stale
   * artifacts in cache when {@code removeMissingFiles} is true.
   */
  private void refresh(
      BlazeContext context,
      ProjectViewSet projectViewSet,
      BlazeProjectData projectData,
      RemoteOutputArtifacts previousOutputs,
      boolean removeMissingFiles) {
    if (!isEnabled()) {
      return;
    }

    if (!cacheDir.exists()) {
      if (!cacheDir.mkdirs()) {
        logger.error("Could not create renderjar cache directory");
        return;
      }
    }

    ImmutableMap<String, BlazeArtifact> artifactsToCache =
        getArtifactsToCache(projectViewSet, projectData);
    ImmutableMap<String, File> cachedFiles = readFileState();

    try {
      // Calculate artifacts we need to update or remove
      Map<String, BlazeArtifact> updated =
          FileCacheDiffer.findUpdatedOutputs(artifactsToCache, cachedFiles, previousOutputs);
      List<File> removed = ImmutableList.of();
      if (removeMissingFiles) {
        removed =
            cachedFiles.entrySet().stream()
                .filter(e -> !artifactsToCache.containsKey(e.getKey()))
                .map(Map.Entry::getValue)
                .collect(toImmutableList());
      }

      // Prefect artifacts from ObjFS (if required)
      ListenableFuture<?> downloadArtifactsFuture =
          RemoteArtifactPrefetcher.getInstance()
              .downloadArtifacts(
                  project.getName(), BlazeArtifact.getRemoteArtifacts(updated.values()));
      FutureUtil.waitForFuture(context, downloadArtifactsFuture)
          .timed("FetchRenderJars", EventType.Prefetching)
          .withProgressMessage("Fetching Render JARs...")
          .run();

      // Remove stale artifacts from and copy updated ones to local cache
      List<ListenableFuture<?>> futures = new ArrayList<>(copyLocally(updated));
      if (removeMissingFiles) {
        futures.addAll(deleteCacheFiles(removed));
      }
      Futures.allAsList(futures).get();

      if (!updated.isEmpty()) {
        context.output(PrintOutput.log(String.format("Copied %d Render JARs", updated.size())));
      }
      if (removeMissingFiles && !removed.isEmpty()) {
        context.output(PrintOutput.log(String.format("Removed %d Render JARs", removed.size())));
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      context.setCancelled();
    } catch (ExecutionException e) {
      logger.warn("Render JAR Cache synchronization didn't complete", e);
      IssueOutput.warn(
              "JARs used for rendering were not updated; layout previews may not work, or might be"
                  + " stale")
          .submit(context);
    } finally {
      ImmutableMap<String, File> state = readFileState();
      logCacheSize(context, state);
    }
  }

  private void logCacheSize(BlazeContext context, ImmutableMap<String, File> cachedFiles) {
    try {
      ImmutableMap<File, Long> cacheFileSizes = FileSizeScanner.readFilesizes(cachedFiles.values());
      long totalSize = cacheFileSizes.values().stream().mapToLong(x -> x).sum();
      long totalSizeKB = totalSize / 1024;
      String msg =
          String.format(
              "Total Render JAR Cache Size: %d kB (%d files)", totalSizeKB, cachedFiles.size());
      context.output(PrintOutput.log(msg));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      context.setCancelled();
    } catch (ExecutionException e) {
      // ignore any errors reading file sizes for logging purposes
    }
  }

  private ImmutableList<ListenableFuture<?>> deleteCacheFiles(Collection<File> removed) {
    return removed.stream()
        .map(
            f ->
                FetchExecutor.EXECUTOR.submit(
                    () -> {
                      try {
                        Files.deleteIfExists(f.toPath());
                      } catch (IOException e) {
                        logger.warn(e);
                      }
                    }))
        .collect(ImmutableList.toImmutableList());
  }

  private ImmutableList<ListenableFuture<?>> copyLocally(Map<String, BlazeArtifact> updated) {
    return updated.entrySet().stream()
        .map(
            entry ->
                FetchExecutor.EXECUTOR.submit(
                    () -> {
                      try {
                        copyLocally(entry.getValue(), cacheFileForKey(entry.getKey()));
                      } catch (IOException e) {
                        logger.warn(
                            String.format(
                                "Failed to copy artifact %s to %s", entry.getValue(), cacheDir),
                            e);
                      }
                    }))
        .collect(ImmutableList.toImmutableList());
  }

  private void copyLocally(BlazeArtifact output, File destination) throws IOException {
    if (output instanceof LocalFileArtifact) {
      File source = ((LocalFileArtifact) output).getFile();
      Files.copy(
          source.toPath(),
          destination.toPath(),
          StandardCopyOption.REPLACE_EXISTING,
          StandardCopyOption.COPY_ATTRIBUTES);
      return;
    }

    try (InputStream stream = output.getInputStream()) {
      Files.copy(stream, destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private File cacheFileForKey(String key) {
    return new File(cacheDir, key);
  }

  private ImmutableMap<String, BlazeArtifact> getArtifactsToCache(
      ProjectViewSet projectViewSet, BlazeProjectData projectData) {
    List<ArtifactLocation> artifactsList =
        BlazeImportUtil.getSourceTargetsStream(project, projectData, projectViewSet)
            .map(TargetIdeInfo::getAndroidIdeInfo)
            .filter(Objects::nonNull)
            .map(AndroidIdeInfo::getRenderResolveJar)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

    ArtifactLocationDecoder decoder = projectData.getArtifactLocationDecoder();
    return decoder.resolveOutputs(artifactsList).stream()
        .collect(ImmutableMap.toImmutableMap(RenderJarCache::cacheKeyForJar, a -> a));
  }

  /**
   * Generates the name of the file stored in local cache that corresponds to the provided {@code
   * jar}. Note: this does not guarantee that the {@code jar} exists in the cache.
   */
  @VisibleForTesting
  public static String cacheKeyForJar(BlazeArtifact jar) {
    String key = artifactKey(jar);
    String name = FileUtil.getNameWithoutExtension(PathUtil.getFileName(key));
    return name + "_" + Integer.toHexString(key.hashCode()) + ".jar";
  }

  private static String artifactKey(BlazeArtifact artifact) {
    if (artifact instanceof OutputArtifact) {
      return ((OutputArtifact) artifact).getKey();
    }
    if (artifact instanceof SourceArtifact) {
      return ((SourceArtifact) artifact).getFile().getPath();
    }
    throw new IllegalArgumentException("Unhandled BlazeArtifact type: " + artifact.getClass());
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
      logger.warn("Render JAR Cache synchronization didn't complete", e);
      IssueOutput.warn("Render JAR Cache synchronization didn't complete").submit(context);
    }
  }

  /**
   * Returns the RenderJAR corresponding to {@code target} or null if no RenderJAR corresponding to
   * {@code target} exists in cache.
   */
  @Nullable
  public File getCachedJarForBinaryTarget(
      ArtifactLocationDecoder artifactLocationDecoder, TargetIdeInfo target) {
    if (!isEnabled()) {
      return null;
    }
    AndroidIdeInfo androidIdeInfo = target.getAndroidIdeInfo();
    if (androidIdeInfo == null) {
      return null;
    }
    ArtifactLocation jarArtifactLocation = androidIdeInfo.getRenderResolveJar();
    if (jarArtifactLocation == null) {
      return null;
    }

    BlazeArtifact jarArtifact = artifactLocationDecoder.resolveOutput(jarArtifactLocation);
    String key = cacheKeyForJar(jarArtifact);
    return cacheState.get(key);
  }

  /** Adapter to map Extension Point Implementation to ProjectService */
  public static final class FileCacheAdapter implements FileCache {
    @Override
    public String getName() {
      return "Render JAR Cache";
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
      ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
      BlazeProjectData blazeProjectData =
          BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
      if (projectViewSet == null || blazeProjectData == null) {
        return;
      }
      getInstance(project)
          .refresh(
              context,
              projectViewSet,
              blazeProjectData,
              blazeProjectData.getRemoteOutputs(),
              false);
    }

    @Override
    public void initialize(Project project) {
      RenderJarCache cache = getInstance(project);
      cache.readFileState();
    }
  }
}
