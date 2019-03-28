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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.command.buildresult.LocalFileOutputArtifact;
import com.google.idea.blaze.base.command.buildresult.OutputArtifact;
import com.google.idea.blaze.base.command.buildresult.RemoteOutputArtifact;
import com.google.idea.blaze.base.filecache.FileCache;
import com.google.idea.blaze.base.filecache.FileCacheDiffer;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.RemoteOutputArtifacts;
import com.google.idea.blaze.base.prefetch.FetchExecutor;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.output.PrintOutput;
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
import java.util.concurrent.Future;
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

  /** In-memory state representing the currently-cached files their corresponding artifacts. */
  private static class InMemoryState {
    private final ImmutableMap<String, OutputArtifact> projectOutputs;

    InMemoryState(ImmutableMap<String, OutputArtifact> projectOutputs) {
      this.projectOutputs = projectOutputs;
    }
  }

  private final File cacheDir;

  @Nullable private volatile InMemoryState inMemoryState = null;

  private boolean enabled;

  public JarCache(Project project) {
    BlazeImportSettings importSettings =
        BlazeImportSettingsManager.getInstance(project).getImportSettings();
    this.cacheDir = getCacheDir(importSettings);
  }

  public boolean isEnabled() {
    return enabled;
  }

  private boolean updateEnabled() {
    this.enabled =
        BlazeJavaUserSettings.getInstance().getUseJarCache()
            && !ApplicationManager.getApplication().isUnitTestMode();
    return enabled;
  }

  private ImmutableMap<String, File> readCachedFiles() {
    FileOperationProvider ops = FileOperationProvider.getInstance();
    File[] files =
        cacheDir.listFiles((dir, name) -> ops.isFile(new File(dir, name)) && name.endsWith(".jar"));
    return files == null
        ? ImmutableMap.of()
        : Arrays.stream(files).collect(toImmutableMap(File::getName, f -> f));
  }

  private void onSync(
      BlazeContext context,
      ProjectViewSet projectViewSet,
      BlazeProjectData projectData,
      @Nullable BlazeProjectData oldProjectData,
      SyncMode syncMode) {
    boolean fullRefresh = syncMode == SyncMode.FULL;
    boolean enabled = updateEnabled();
    if (!enabled || fullRefresh) {
      clearCache();
    }
    if (!enabled) {
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
            .anyMatch(a -> a instanceof RemoteOutputArtifact)) {
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

    ImmutableMap<String, File> cachedFiles = readCachedFiles();
    try {
      Map<String, OutputArtifact> updated =
          FileCacheDiffer.findUpdatedOutputs(
              inMemoryState.projectOutputs, cachedFiles, previousOutputs);

      List<File> removed = new ArrayList<>();
      if (removeMissingFiles) {
        removed =
            cachedFiles.entrySet().stream()
                .filter(e -> !inMemoryState.projectOutputs.containsKey(e.getKey()))
                .map(Map.Entry::getValue)
                .collect(toImmutableList());
      }

      // Update cache files, and remove files if required.
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
    }
  }

  private InMemoryState readState(ProjectViewSet projectViewSet, BlazeProjectData projectData) {
    List<LibraryArtifact> jarLibraries =
        BlazeLibraryCollector.getLibraries(projectViewSet, projectData).stream()
            .filter(library -> library instanceof BlazeJarLibrary)
            .map(library -> ((BlazeJarLibrary) library).libraryArtifact)
            .collect(Collectors.toList());

    ArtifactLocationDecoder decoder = projectData.getArtifactLocationDecoder();
    Map<String, OutputArtifact> newOutputs = new HashMap<>();
    for (LibraryArtifact lib : jarLibraries) {
      OutputArtifact jar = decoder.resolveOutput(lib.jarForIntellijLibrary());
      newOutputs.put(cacheKeyForJar(jar), jar);

      for (ArtifactLocation sourceJar : lib.getSourceJars()) {
        OutputArtifact srcJar = decoder.resolveOutput(sourceJar);
        newOutputs.put(cacheKeyForSourceJar(srcJar), srcJar);
      }
    }
    return new InMemoryState(ImmutableMap.copyOf(newOutputs));
  }

  private Collection<ListenableFuture<?>> copyLocally(Map<String, OutputArtifact> updated) {
    List<ListenableFuture<?>> futures = new ArrayList<>();
    updated.forEach(
        (key, artifact) ->
            futures.add(
                FetchExecutor.EXECUTOR.submit(
                    () -> {
                      try {
                        copyLocally(artifact, cacheFileForKey(key));
                      } catch (IOException e) {
                        logger.warn(e);
                      }
                    })));
    return futures;
  }

  private static void copyLocally(OutputArtifact output, File destination) throws IOException {
    if (output instanceof LocalFileOutputArtifact) {
      File source = ((LocalFileOutputArtifact) output).getFile();
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

  private void clearCache() {
    if (cacheDir.exists()) {
      File[] cacheFiles = cacheDir.listFiles();
      if (cacheFiles != null) {
        @SuppressWarnings("unused") // go/futurereturn-lsc
        Future<?> possiblyIgnoredError = FileUtil.asyncDelete(Lists.newArrayList(cacheFiles));
      }
    }
    inMemoryState = null;
  }

  /** Gets the cached file for a jar. If it doesn't exist, we return the file from the library. */
  public File getCachedJar(ArtifactLocationDecoder decoder, BlazeJarLibrary library) {
    boolean enabled = isEnabled();
    OutputArtifact artifact =
        decoder.resolveOutput(library.libraryArtifact.jarForIntellijLibrary());
    if (!enabled) {
      return getFallbackFile(artifact);
    }
    String cacheKey = cacheKeyForJar(artifact);
    return getCacheFile(cacheKey).orElse(getFallbackFile(artifact));
  }

  /** Gets the cached file for a source jar. */
  public File getCachedSourceJar(ArtifactLocationDecoder decoder, ArtifactLocation sourceJar) {
    boolean enabled = isEnabled();
    OutputArtifact artifact = decoder.resolveOutput(sourceJar);
    if (!enabled) {
      return getFallbackFile(artifact);
    }
    String cacheKey = cacheKeyForSourceJar(artifact);
    return getCacheFile(cacheKey).orElse(getFallbackFile(artifact));
  }

  private Optional<File> getCacheFile(String cacheKey) {
    InMemoryState state = inMemoryState;
    if (state == null || !state.projectOutputs.containsKey(cacheKey)) {
      return Optional.empty();
    }
    return Optional.of(cacheFileForKey(cacheKey));
  }

  /** The file to return if there's no locally cached version. */
  private static File getFallbackFile(OutputArtifact output) {
    if (output instanceof RemoteOutputArtifact) {
      // TODO(brendandouglas): copy locally on the fly?
      throw new RuntimeException("The jar cache must be enabled when syncing remotely");
    }
    return ((LocalFileOutputArtifact) output).getFile();
  }

  private static String cacheKeyInternal(OutputArtifact output) {
    String key = output.getKey();
    String name = FileUtil.getNameWithoutExtension(PathUtil.getFileName(key));
    return name + "_" + Integer.toHexString(key.hashCode());
  }

  private static String cacheKeyForJar(OutputArtifact jar) {
    return cacheKeyInternal(jar) + ".jar";
  }

  private static String cacheKeyForSourceJar(OutputArtifact srcjar) {
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
      BlazeProjectData projectData =
          BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
      getInstance(project).refresh(context, projectData);
    }
  }
}
