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
package com.google.idea.blaze.base.sync.status;

import com.google.idea.blaze.base.experiments.BoolExperiment;
import com.google.idea.blaze.base.projectview.ProjectViewStorageManager;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.google.idea.blaze.base.sync.BlazeSyncManager;
import com.google.idea.blaze.base.sync.actions.IncrementalSyncProjectAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Per-project listener for changes to BUILD files, and other changes requiring an incremental sync.
 */
public class BlazeSyncStatusImpl implements BlazeSyncStatus {

  public static final BoolExperiment AUTOMATIC_INCREMENTAL_SYNC =
    new BoolExperiment("automatic.incremental.sync", true);

  public static BlazeSyncStatusImpl getImpl(@NotNull Project project) {
    return (BlazeSyncStatusImpl) BlazeSyncStatus.getInstance(project);
  }

  private static Logger log = Logger.getInstance(BlazeSyncStatusImpl.class);

  private final Project project;

  public final AtomicBoolean syncInProgress = new AtomicBoolean(false);
  private final AtomicBoolean syncPending = new AtomicBoolean(false);

  /**
   * has a BUILD file changed since the last sync started
   */
  private volatile boolean dirty = false;

  private volatile boolean failedSync = false;

  public BlazeSyncStatusImpl(Project project) {
    this.project = project;
    // listen for changes to the VFS
    VirtualFileManager.getInstance().addVirtualFileListener(new FileListener(), project);

    // trigger VFS updates whenever navigating away from an unsaved BUILD file
    project.getMessageBus().connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER,
                                                new FileFocusListener());
  }

  private static boolean automaticSyncEnabled() {
    return AUTOMATIC_INCREMENTAL_SYNC.getValue()
        && BlazeUserSettings.getInstance().getResyncAutomatically();
  }

  @Override
  public SyncStatus getStatus() {
    if (failedSync) {
      return SyncStatus.FAILED;
    }
    return dirty ? SyncStatus.DIRTY : SyncStatus.CLEAN;
  }

  public void syncStarted() {
    syncPending.set(false);
    syncInProgress.set(true);
  }

  public void syncEnded(boolean successful) {
    syncInProgress.set(false);
    failedSync = !successful;
    if (successful && !syncPending.get()) {
      dirty = false;
    }
  }

  @Override
  public void setDirty() {
    dirty = true;
    queueIncrementalSync();
  }

  @Override
  public void queueAutomaticSyncIfDirty() {
    if (dirty) {
      queueIncrementalSync();
    }
  }

  private void queueIncrementalSync() {
    if (automaticSyncEnabled() && syncPending.compareAndSet(false, true)) {
      log.info("Automatic sync started");
      BlazeSyncManager.getInstance(project).requestProjectSync(IncrementalSyncProjectAction.autoSyncParams);
    }
  }

  /**
   * Listens for changes to files which impact the sync process
   * (BUILD files and project view files)
   */
  private class FileListener extends VirtualFileAdapter {
    @Override
    public void fileCreated(@NotNull VirtualFileEvent event) {
      processEvent(event);
    }

    @Override
    public void fileDeleted(@NotNull VirtualFileEvent event){
      processEvent(event);
      // we (sometimes) only get one event when a directory is deleted, so check the children too.
      checkChildren(event.getFile());
    }

    @Override
    public void fileMoved(@NotNull VirtualFileMoveEvent event){
      processEvent(event);
    }

    @Override
    public void contentsChanged(@NotNull VirtualFileEvent event){
      processEvent(event);
    }

    private void processEvent(@NotNull VirtualFileEvent event) {
      if (isSyncSensitiveFile(event.getFile())) {
        setDirty();
      }
    }

    private void checkChildren(VirtualFile file) {
      if (!(file instanceof NewVirtualFile)) {
        return;
      }
      Collection<VirtualFile> children = ((NewVirtualFile) file).getCachedChildren();
      for (VirtualFile child : children) {
        if (isSyncSensitiveFile(child)) {
          setDirty();
          return;
        }
      }
    }
  }

  /**
   * Listens for changes to files which impact the sync process
   * (BUILD files and project view files)
   */
  private static class FileFocusListener extends FileEditorManagerAdapter {
    @Override
    public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
      processEvent(file);
    }

    @Override
    public void selectionChanged(@NotNull FileEditorManagerEvent event) {
      processEvent(event.getOldFile());
    }

    private void processEvent(@Nullable VirtualFile file) {
      if (isSyncSensitiveFile(file)) {
        FileDocumentManager manager = FileDocumentManager.getInstance();
        Document doc = manager.getCachedDocument(file);
        if (doc != null) {
          manager.saveDocument(doc);
        }
      }
    }
  }

  private static boolean isSyncSensitiveFile(@Nullable VirtualFile file) {
    return file != null && (isBuildFile(file) || ProjectViewStorageManager.isProjectViewFile(file.getPath()));
  }


  private static boolean isBuildFile(VirtualFile file) {
    return file.getName().equals("BUILD");
  }

}
