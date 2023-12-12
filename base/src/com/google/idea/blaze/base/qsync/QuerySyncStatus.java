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

import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.SyncResult;
import com.google.idea.blaze.base.sync.status.BlazeSyncStatus;
import com.intellij.openapi.project.Project;
import java.util.concurrent.atomic.AtomicBoolean;

/** Class that keeps track of query sync operations. */
public class QuerySyncStatus {

  private final Project project;
  private final AtomicBoolean syncInProgress = new AtomicBoolean(false);

  public QuerySyncStatus(Project project) {
    this.project = project;
  }

  public boolean syncInProgress() {
    return syncInProgress.get();
  }

  public void syncStarted() {
    syncInProgress.set(true);
    BlazeSyncStatus.getInstance(project).syncStarted();
  }

  public void syncCancelled() {
    syncEnded(SyncResult.CANCELLED);
  }

  public void syncFailed() {
    syncEnded(SyncResult.FAILURE);
  }

  public void syncEnded() {
    syncEnded(SyncResult.SUCCESS);
  }

  private void syncEnded(SyncResult result) {
    syncInProgress.set(false);
    BlazeSyncStatus.getInstance(project).syncEnded(SyncMode.FULL, result);
  }
}
