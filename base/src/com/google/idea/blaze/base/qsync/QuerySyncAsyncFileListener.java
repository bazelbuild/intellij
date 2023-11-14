/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.qsync;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.lang.buildfile.language.BuildFileType;
import com.google.idea.blaze.base.logging.utils.querysync.QuerySyncActionStatsScope;
import com.google.idea.blaze.base.qsync.QuerySyncManager.TaskOrigin;
import com.google.idea.blaze.base.qsync.settings.QuerySyncSettings;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.SyncListener;
import com.google.idea.blaze.base.sync.status.BlazeSyncStatus;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.AsyncFileListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;

/** {@link AsyncFileListener} for monitoring project changes requiring a re-sync */
public class QuerySyncAsyncFileListener implements AsyncFileListener {
  private final Project project;

  private final SyncRequester syncRequester;

  public QuerySyncAsyncFileListener(Project project, Disposable parentDisposable) {
    this.project = project;
    this.syncRequester = new SyncRequester(project, parentDisposable);
  }

  @Override
  @Nullable
  public ChangeApplier prepareChange(List<? extends VFileEvent> events) {
    if (!QuerySyncSettings.getInstance().syncOnFileChanges()) {
      return null;
    }

    ImmutableList<? extends VFileEvent> projectEvents = filterForProject(events);

    if (projectEvents.stream().anyMatch(this::requiresSync)) {
      return new ChangeApplier() {
        @Override
        public void afterVfsChange() {
          syncRequester.requestSync();
        }
      };
    }
    return null;
  }

  private ImmutableList<? extends VFileEvent> filterForProject(
      List<? extends VFileEvent> rawEvents) {
    QuerySyncProject querySyncProject =
        QuerySyncManager.getInstance(project).getLoadedProject().orElse(null);
    if (querySyncProject == null) {
      return ImmutableList.of();
    }
    return rawEvents.stream()
        .filter(event -> querySyncProject.containsPath(Path.of(event.getPath())))
        .collect(toImmutableList());
  }

  private boolean requiresSync(VFileEvent event) {
    VirtualFile vf = event.getFile();
    if (vf == null) {
      return false;
    }

    if (vf.getFileType() instanceof BuildFileType) {
      return true;
    }

    if (event instanceof VFileCreateEvent || event instanceof VFileMoveEvent) {
      return true;
    }

    if (event instanceof VFileContentChangeEvent) {
      // TODO: Check if file is not already part of graph
      if (((VFileContentChangeEvent) event).getOldLength() == 0) {
        return true;
      }
    }

    return false;
  }

  /**
   * Utility for requesting partial syncs, with a listener to retry requests if a sync is already in
   * progress.
   */
  private static class SyncRequester {
    private final Project project;

    private final AtomicBoolean changePending = new AtomicBoolean(false);

    public SyncRequester(Project project, Disposable parentDisposable) {
      this.project = project;
      ApplicationManager.getApplication()
          .getExtensionArea()
          .getExtensionPoint(SyncListener.EP_NAME)
          .registerExtension(
              new SyncListener() {
                @Override
                public void afterQuerySync(Project project1, BlazeContext context) {
                  if (SyncRequester.this.project != project1) {
                    return;
                  }
                  if (changePending.get()) {
                    requestSyncInternal();
                  }
                }
              },
              parentDisposable);
    }

    public void requestSync() {
      if (changePending.compareAndSet(false, true)) {
        if (!BlazeSyncStatus.getInstance(project).syncInProgress()) {
          requestSyncInternal();
        }
      }
    }

    private void requestSyncInternal() {
      QuerySyncManager.getInstance(project)
          .deltaSync(
              QuerySyncActionStatsScope.create(QuerySyncAsyncFileListener.class, null),
              TaskOrigin.AUTOMATIC);
      changePending.set(false);
    }
  }
}
