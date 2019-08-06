/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.golang.resolve;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.goide.psi.GoFile;
import com.google.idea.blaze.base.io.VfsUtils;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.sync.SyncListener;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.SyncResult;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.LowMemoryWatcher;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileListener;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileMoveEvent;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import java.io.File;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A cache of PSI files contained in go packages.
 *
 * <p>Will update contents in response to VFS events to prevent stale files.
 *
 * <p>Cache resets on sync to prevent accumulation of obsolete go packages.
 */
class GoFilesCache {
  private final PsiManager psiManager;
  private final ConcurrentMap<File, Optional<GoFile>> cache;

  static GoFilesCache getInstance(Project project) {
    return ServiceManager.getService(project, GoFilesCache.class);
  }

  GoFilesCache(Project project) {
    this.psiManager = PsiManager.getInstance(project);
    this.cache = new ConcurrentHashMap<>();
    VirtualFileManager.getInstance()
        .addVirtualFileListener(
            new VirtualFileListener() {
              @Override
              public void fileCreated(VirtualFileEvent event) {
                updateFile(event.getFile());
              }

              @Override
              public void fileDeleted(VirtualFileEvent event) {
                removeFile(event.getFile());
              }

              @Override
              public void fileMoved(VirtualFileMoveEvent event) {
                updateFile(event.getFile());
                removeFile(event.getOldParent(), event.getFileName());
              }
            },
            project);
    LowMemoryWatcher.register(cache::clear, project);
  }

  private void updateFile(VirtualFile virtualFile) {
    cache.computeIfPresent(
        VfsUtil.virtualToIoFile(virtualFile),
        (file, oldGoFile) ->
            Optional.of(virtualFile)
                .map(psiManager::findFile)
                .filter(GoFile.class::isInstance)
                .map(GoFile.class::cast));
  }

  private void removeFile(VirtualFile virtualFile) {
    cache.computeIfPresent(
        VfsUtil.virtualToIoFile(virtualFile), (file, oldGoFile) -> Optional.empty());
  }

  private void removeFile(VirtualFile directory, String name) {
    cache.computeIfPresent(
        new File(VfsUtil.virtualToIoFile(directory), name), (file, oldGoFile) -> Optional.empty());
  }

  /**
   * Resolve {@link File}s to {@link PsiFile}s if not already cached, returning only valid files.
   */
  Collection<PsiFile> getFiles(Collection<File> files) {
    return files.stream()
        .map(file -> cache.computeIfAbsent(file, this::compute))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .filter(GoFile::isValid)
        .collect(toImmutableList());
  }

  private Optional<GoFile> compute(File file) {
    return Optional.of(file)
        .map(VfsUtils::resolveVirtualFile)
        .map(psiManager::findFile)
        .filter(GoFile.class::isInstance)
        .map(GoFile.class::cast);
  }

  static class ClearCacheListener implements SyncListener {
    @Override
    public void onSyncComplete(
        Project project,
        BlazeContext context,
        BlazeImportSettings importSettings,
        ProjectViewSet projectViewSet,
        BlazeProjectData blazeProjectData,
        SyncMode syncMode,
        SyncResult syncResult) {
      if (SyncMode.involvesBlazeBuild(syncMode)) {
        GoFilesCache.getInstance(project).cache.clear();
      }
    }
  }
}
