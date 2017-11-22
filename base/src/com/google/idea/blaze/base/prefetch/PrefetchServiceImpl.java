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
package com.google.idea.blaze.base.prefetch;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** Implementation for prefetcher. */
public class PrefetchServiceImpl implements PrefetchService {

  private static final Logger logger = Logger.getInstance(PrefetchServiceImpl.class);

  private static final long REFETCH_PERIOD_MILLIS = 24 * 60 * 60 * 1000;
  private final Map<Integer, Long> fileToLastFetchTimeMillis = Maps.newConcurrentMap();

  @Override
  public ListenableFuture<?> prefetchFiles(
      Project project, Collection<File> files, boolean refetchCachedFiles) {
    return prefetchFiles(project, ImmutableSet.of(), files, refetchCachedFiles, false);
  }

  private ListenableFuture<?> prefetchFiles(
      Project project,
      Set<File> excludeDirectories,
      Collection<File> files,
      boolean refetchCachedFiles,
      boolean fetchFileTypes) {
    if (files.isEmpty() || !enabled(project)) {
      return Futures.immediateFuture(null);
    }
    if (!refetchCachedFiles) {
      long startTime = System.currentTimeMillis();
      // ignore recently fetched files
      files =
          files
              .stream()
              .filter(file -> shouldPrefetch(file, startTime))
              .collect(Collectors.toList());
    }
    FileOperationProvider provider = FileOperationProvider.getInstance();
    List<ListenableFuture<File>> canonicalFiles =
        files
            .stream()
            .map(file -> FetchExecutor.EXECUTOR.submit(() -> toCanonicalFile(provider, file)))
            .collect(Collectors.toList());
    List<ListenableFuture<?>> futures = Lists.newArrayList();
    for (Prefetcher prefetcher : Prefetcher.EP_NAME.getExtensions()) {
      futures.add(
          prefetcher.prefetchFiles(
              project, excludeDirectories, canonicalFiles, FetchExecutor.EXECUTOR, fetchFileTypes));
    }
    return Futures.allAsList(futures);
  }

  private static boolean enabled(Project project) {
    for (Prefetcher prefetcher : Prefetcher.EP_NAME.getExtensions()) {
      if (prefetcher.enabled(project)) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  private static File toCanonicalFile(FileOperationProvider provider, File file) {
    try {
      File canonicalFile = file.getCanonicalFile();
      if (provider.exists(canonicalFile)) {
        return canonicalFile;
      }
    } catch (IOException e) {
      logger.warn(e);
    }
    return null;
  }

  /** Returns false if this file has been recently prefetched. */
  private boolean shouldPrefetch(File file, long startTime) {
    // Filter files that have been recently fetched
    Long lastFetchTime = fileToLastFetchTimeMillis.get(file.hashCode());
    if (lastFetchTime != null && (startTime - lastFetchTime < REFETCH_PERIOD_MILLIS)) {
      return false;
    }
    fileToLastFetchTimeMillis.put(file.hashCode(), startTime);
    return true;
  }

  @Override
  public ListenableFuture<?> prefetchProjectFiles(
      Project project, ProjectViewSet projectViewSet, @Nullable BlazeProjectData blazeProjectData) {
    BlazeImportSettings importSettings =
        BlazeImportSettingsManager.getInstance(project).getImportSettings();
    if (importSettings == null) {
      return Futures.immediateFuture(null);
    }
    WorkspaceRoot workspaceRoot = WorkspaceRoot.fromImportSettings(importSettings);
    if (!FileOperationProvider.getInstance().exists(workspaceRoot.directory())) {
      // quick sanity check before trying to prefetch each individual file
      return Futures.immediateFuture(null);
    }
    ImportRoots importRoots =
        ImportRoots.builder(workspaceRoot, importSettings.getBuildSystem())
            .add(projectViewSet)
            .build();
    Set<File> sourceDirectories = new HashSet<>();
    for (WorkspacePath workspacePath : importRoots.rootDirectories()) {
      sourceDirectories.add(workspaceRoot.fileForPath(workspacePath));
    }
    Set<File> excludeDirectories = new HashSet<>();
    for (WorkspacePath workspacePath : importRoots.excludeDirectories()) {
      excludeDirectories.add(workspaceRoot.fileForPath(workspacePath));
    }
    ListenableFuture<?> sourceFilesFuture =
        prefetchFiles(
            project,
            excludeDirectories,
            sourceDirectories,
            /* refetchCachedFiles */ false,
            // PushedFilePropertiesUpdaterImpl will eventually want the file types of module roots.
            /* fetchFileTypes */ true);
    Set<File> externalFiles = new HashSet<>();
    if (blazeProjectData != null) {
      for (PrefetchFileSource fileSource : PrefetchFileSource.EP_NAME.getExtensions()) {
        fileSource.addFilesToPrefetch(
            project, projectViewSet, importRoots, blazeProjectData, externalFiles);
      }
    }
    ListenableFuture<?> externalFilesFuture = prefetchFiles(project, externalFiles, false);
    return Futures.allAsList(sourceFilesFuture, externalFilesFuture);
  }
}
