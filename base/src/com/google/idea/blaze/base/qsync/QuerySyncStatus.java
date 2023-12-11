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

import com.google.idea.blaze.base.qsync.QuerySyncManager.Operation;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.SyncResult;
import com.google.idea.blaze.base.sync.status.BlazeSyncStatus;
import com.intellij.openapi.project.Project;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Class that keeps track of query sync operations. */
public class QuerySyncStatus {

  private final Project project;
  private final AtomicReference<Operation> operationInProgress = new AtomicReference<>(null);

  public QuerySyncStatus(Project project) {
    this.project = project;
  }

  public boolean syncInProgress() {
    return Objects.equals(operationInProgress.get(), Operation.SYNC);
  }

  public boolean buildInProgress() {
    return Objects.equals(operationInProgress.get(), Operation.BUILD);
  }

  public void operationStarted(Operation operationType) {
    operationInProgress.set(operationType);
  }

  public void operationCancelled() {
    operationEnded(SyncResult.CANCELLED);
  }

  public void operationFailed() {
    operationEnded(SyncResult.FAILURE);
  }

  public void operationEnded() {
    operationEnded(SyncResult.SUCCESS);
  }

  private void operationEnded(SyncResult result) {
    operationInProgress.set(null);
    BlazeSyncStatus.getInstance(project).syncEnded(SyncMode.FULL, result);
  }
}
