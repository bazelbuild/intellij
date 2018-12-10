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

import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.SyncResult;
import com.intellij.openapi.project.Project;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Per-project listener for changes to BUILD files, and other changes requiring an incremental sync.
 */
public class BlazeSyncStatusImpl implements BlazeSyncStatus {

  public static BlazeSyncStatusImpl getImpl(Project project) {
    return (BlazeSyncStatusImpl) BlazeSyncStatus.getInstance(project);
  }

  private final AtomicBoolean syncInProgress = new AtomicBoolean(false);
  private final BlazeSyncStatusStateManager stateManager;

  public BlazeSyncStatusImpl(Project project) {
    this.stateManager = BlazeSyncStatusStateManager.getInstance(project);
  }

  @Override
  public SyncStatus getStatus() {
    if (stateManager.lastSyncFailed()) {
      return SyncStatus.FAILED;
    }
    return stateManager.isDirty() ? SyncStatus.DIRTY : SyncStatus.CLEAN;
  }

  @Override
  public boolean syncInProgress() {
    return syncInProgress.get();
  }

  void syncStarted() {
    syncInProgress.set(true);
  }

  void syncEnded(SyncMode syncMode, SyncResult syncResult) {
    syncInProgress.set(false);
    stateManager.setLastSyncFailed(syncResult == SyncResult.FAILURE);
    if (allTargetsBuild(syncMode) && syncResult == SyncResult.SUCCESS) {
      stateManager.setDirty(false);
    } else if (syncResult == SyncResult.PARTIAL_SUCCESS || syncResult == SyncResult.CANCELLED) {
      stateManager.setDirty(true);
    }
  }

  private static boolean allTargetsBuild(SyncMode mode) {
    return mode == SyncMode.FULL || mode == SyncMode.INCREMENTAL;
  }

  @Override
  public void setDirty() {
    stateManager.setDirty(true);
  }

  @Override
  public boolean isDirty() {
    return stateManager.isDirty();
  }
}
