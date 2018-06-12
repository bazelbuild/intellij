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

import com.google.common.base.Preconditions;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.idea.blaze.base.filecache.FileCache;
import com.google.idea.blaze.base.filecache.FileCacheSynchronizer;
import com.google.idea.blaze.base.filecache.FileCacheSynchronizerTraits;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.io.FileSizeScanner;
import com.google.idea.blaze.base.model.BlazeLibrary;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.PrintOutput;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.sync.BlazeSyncParams;
import com.google.idea.blaze.base.sync.BlazeSyncParams.SyncMode;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.google.idea.blaze.base.sync.libraries.BlazeLibraryCollector;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.java.settings.BlazeJavaUserSettings;
import com.google.idea.blaze.java.sync.model.BlazeJarLibrary;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** Local cache of the jars referenced by the project. */
public class JarCache {
  private static final Logger logger = Logger.getInstance(JarCache.class);

  private final File cacheDir;

  private boolean enabled;
  @Nullable private JarCacheSynchronizerTraits traits;

  public static JarCache getInstance(Project project) {
    return ServiceManager.getService(project, JarCache.class);
  }

  public JarCache(Project project) {
    BlazeImportSettings importSettings =
        BlazeImportSettingsManager.getInstance(project).getImportSettings();
    this.cacheDir = getCacheDir(importSettings);
  }

  void onSync(
      BlazeContext context,
      ProjectViewSet projectViewSet,
      BlazeProjectData projectData,
      BlazeSyncParams.SyncMode syncMode) {
    Collection<BlazeLibrary> libraries =
        BlazeLibraryCollector.getLibraries(projectViewSet, projectData);
    boolean fullRefresh = syncMode == SyncMode.FULL;
    boolean removeMissingFiles = syncMode == SyncMode.INCREMENTAL;
    boolean enabled = updateEnabled();

    if (!enabled || fullRefresh) {
      clearCache();
    }
    if (!enabled) {
      return;
    }

    List<BlazeJarLibrary> jarLibraries =
        libraries
            .stream()
            .filter(library -> library instanceof BlazeJarLibrary)
            .map(library -> (BlazeJarLibrary) library)
            .collect(Collectors.toList());

    ArtifactLocationDecoder artifactLocationDecoder = projectData.artifactLocationDecoder;
    BiMap<File, String> sourceFileToCacheKey = HashBiMap.create(jarLibraries.size());
    for (BlazeJarLibrary library : jarLibraries) {
      File jarFile =
          artifactLocationDecoder.decode(library.libraryArtifact.jarForIntellijLibrary());
      sourceFileToCacheKey.put(jarFile, cacheKeyForJar(jarFile));

      for (ArtifactLocation sourceJar : library.libraryArtifact.sourceJars) {
        File srcJarFile = artifactLocationDecoder.decode(sourceJar);
        sourceFileToCacheKey.put(srcJarFile, cacheKeyForSourceJar(srcJarFile));
      }
    }

    this.traits = new JarCacheSynchronizerTraits(cacheDir, sourceFileToCacheKey);
    refresh(context, removeMissingFiles);
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

  /** Refreshes any updated files in the cache. Does not add or removes any files */
  void refresh() {
    refresh(null, false);
  }

  private void refresh(@Nullable BlazeContext context, boolean removeMissingFiles) {
    if (!enabled || traits == null) {
      return;
    }

    // Ensure the cache dir exists
    if (!cacheDir.exists()) {
      if (!cacheDir.mkdirs()) {
        logger.error("Could not create jar cache directory");
        return;
      }
    }
    FileCacheSynchronizer synchronizer = new FileCacheSynchronizer(traits);
    if (!synchronizer.synchronize(context, removeMissingFiles)) {
      logger.warn("Jar Cache synchronization didn't complete");
    }
    if (context != null) {
      try {
        Collection<File> finalCacheFiles = traits.enumerateCacheFiles();
        ImmutableMap<File, Long> cacheFileSizes = FileSizeScanner.readFilesizes(finalCacheFiles);
        Long total = cacheFileSizes.values().stream().mapToLong(x -> x).sum();
        context.output(
            PrintOutput.log(
                String.format(
                    "Total Jar Cache size: %d kB (%d files)",
                    total / 1024, finalCacheFiles.size())));
      } catch (Exception e) {
        logger.warn("Could not determine cache size", e);
      }
    }
  }

  private void clearCache() {
    if (cacheDir.exists()) {
      File[] cacheFiles = cacheDir.listFiles();
      if (cacheFiles != null) {
        @SuppressWarnings("unused") // go/futurereturn-lsc
        Future<?> possiblyIgnoredError = FileUtil.asyncDelete(Lists.newArrayList(cacheFiles));
      }
    }
    traits = null;
  }

  /** Gets the cached file for a jar. If it doesn't exist, we return the file from the library. */
  public File getCachedJar(ArtifactLocationDecoder decoder, BlazeJarLibrary library) {
    File file = decoder.decode(library.libraryArtifact.jarForIntellijLibrary());
    if (!enabled || traits == null) {
      return file;
    }
    String cacheKey = traits.sourceFileToCacheKey(file);
    if (cacheKey == null) {
      return file;
    }
    return traits.cacheFileForKey(cacheKey);
  }

  /** Gets the cached file for a source jar. */
  public File getCachedSourceJar(ArtifactLocationDecoder decoder, ArtifactLocation sourceJar) {
    File file = decoder.decode(sourceJar);
    if (!enabled || traits == null) {
      return file;
    }
    String cacheKey = traits.sourceFileToCacheKey(file);
    if (cacheKey == null) {
      return file;
    }
    return traits.cacheFileForKey(cacheKey);
  }

  private static String cacheKeyInternal(File jar) {
    int parentHash = jar.getParent().hashCode();
    return FileUtil.getNameWithoutExtension(jar) + "_" + Integer.toHexString(parentHash);
  }

  private static String cacheKeyForJar(File jar) {
    return cacheKeyInternal(jar) + ".jar";
  }

  private static String cacheKeyForSourceJar(File srcjar) {
    return cacheKeyInternal(srcjar) + "-src.jar";
  }

  private static File getCacheDir(BlazeImportSettings importSettings) {
    return new File(BlazeDataStorage.getProjectDataDir(importSettings), "libraries");
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
        BlazeSyncParams.SyncMode syncMode) {
      getInstance(project).onSync(context, projectViewSet, projectData, syncMode);
    }

    @Override
    public void refreshFiles(Project project) {
      getInstance(project).refresh();
    }
  }

  /** Traits to synchronize local cache of the jars referenced by the project. */
  private static final class JarCacheSynchronizerTraits implements FileCacheSynchronizerTraits {
    private final File cacheDir;
    private final BiMap<File, String> sourceFileToCacheKey;

    JarCacheSynchronizerTraits(File cacheDir, BiMap<File, String> sourceFileToCacheKey) {
      this.cacheDir = cacheDir;
      this.sourceFileToCacheKey = sourceFileToCacheKey;
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
      File[] cacheFiles = cacheDir.listFiles();
      Preconditions.checkNotNull(cacheFiles);
      return ImmutableList.copyOf(cacheFiles);
    }

    @Override
    public String cacheFileToCacheKey(File cacheFile) {
      // Cache key == file name
      return cacheFile.getName();
    }

    @Override
    public File cacheFileForKey(String key) {
      return new File(cacheDir, key);
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
        context.output(PrintOutput.log(String.format("Copied %d jars", numUpdatedFiles)));
      }
      if (numRemovedFiles > 0 && removeMissingFiles) {
        context.output(PrintOutput.log(String.format("Removed %d jars", numRemovedFiles)));
      }
    }
  }
}
