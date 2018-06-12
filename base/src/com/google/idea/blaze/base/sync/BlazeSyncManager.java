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
package com.google.idea.blaze.base.sync;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.idea.blaze.base.async.executor.ProgressiveTaskWithProgressIndicator;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.google.idea.blaze.base.sync.BlazeSyncParams.SyncMode;
import com.google.idea.blaze.base.sync.projectview.SyncDirectoriesWarning;
import com.google.idea.common.concurrency.ConcurrencyUtil;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import java.util.Collection;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Manages syncing and its listeners. */
public class BlazeSyncManager {

  // a per-project single-threaded executor to run syncs. Ensures only one sync can be run at a time
  private final ListeningExecutorService syncExecutor;
  private final Project project;

  public BlazeSyncManager(Project project) {
    this.project = project;
    syncExecutor =
        MoreExecutors.listeningDecorator(
            Executors.newSingleThreadExecutor(
                ConcurrencyUtil.namedDaemonThreadPoolFactory(BlazeSyncManager.class)));
  }

  public static BlazeSyncManager getInstance(Project project) {
    return ServiceManager.getService(project, BlazeSyncManager.class);
  }

  /** Requests a project sync with Blaze. */
  public void requestProjectSync(BlazeSyncParams syncParams) {
    if (syncParams.syncMode == SyncMode.NO_BUILD
        && !syncParams.backgroundSync
        && !SyncDirectoriesWarning.warn(project)) {
      return;
    }
    StartupManager.getInstance(project)
        .runWhenProjectIsInitialized(
            () -> {
              BlazeImportSettings importSettings =
                  BlazeImportSettingsManager.getInstance(project).getImportSettings();
              if (importSettings == null) {
                throw new IllegalStateException(
                    String.format(
                        "Attempt to sync non-%s project.", Blaze.buildSystemName(project)));
              }
              if (runInitialDirectoryOnlySync(syncParams)) {
                BlazeSyncParams params =
                    new BlazeSyncParams.Builder(
                            "Initial directory update", BlazeSyncParams.SyncMode.NO_BUILD)
                        .build();
                submitTask(new BlazeSyncTask(project, importSettings, params));
              }
              submitTask(new BlazeSyncTask(project, importSettings, syncParams));
            });
  }

  private void submitTask(BlazeSyncTask task) {
    @SuppressWarnings("unused") // go/futurereturn-lsc
    Future<?> possiblyIgnoredError =
        ProgressiveTaskWithProgressIndicator.builder(project)
            .setExecutor(syncExecutor)
            .submitTask(task);
  }

  private static boolean runInitialDirectoryOnlySync(BlazeSyncParams syncParams) {
    switch (syncParams.syncMode) {
      case NO_BUILD:
      case STARTUP:
        return false;
      case FULL:
      case INCREMENTAL:
      case PARTIAL:
        return true;
    }
    throw new AssertionError("Unhandled syncMode: " + syncParams.syncMode);
  }

  public void fullProjectSync() {
    BlazeSyncParams syncParams =
        new BlazeSyncParams.Builder("Full Sync", BlazeSyncParams.SyncMode.FULL)
            .addProjectViewTargets(true)
            .addWorkingSet(BlazeUserSettings.getInstance().getExpandSyncToWorkingSet())
            .build();
    requestProjectSync(syncParams);
  }

  public void incrementalProjectSync() {
    BlazeSyncParams syncParams =
        new BlazeSyncParams.Builder("Sync", BlazeSyncParams.SyncMode.INCREMENTAL)
            .addProjectViewTargets(true)
            .addWorkingSet(BlazeUserSettings.getInstance().getExpandSyncToWorkingSet())
            .build();
    requestProjectSync(syncParams);
  }

  public void partialSync(Collection<? extends TargetExpression> targetExpressions) {
    BlazeSyncParams syncParams =
        new BlazeSyncParams.Builder("Partial Sync", BlazeSyncParams.SyncMode.PARTIAL)
            .addTargetExpressions(targetExpressions)
            .build();
    requestProjectSync(syncParams);
  }

  /**
   * Runs a directory-only sync, without any 'blaze build' operations.
   *
   * @param inBackground run in the background, suppressing the normal 'no targets will be build'
   *     warning.
   */
  public void directoryUpdate(boolean inBackground) {
    BlazeSyncParams syncParams =
        new BlazeSyncParams.Builder("Update Directories", BlazeSyncParams.SyncMode.NO_BUILD)
            .setBackgroundSync(inBackground)
            .build();
    requestProjectSync(syncParams);
  }

  public void workingSetSync() {
    BlazeSyncParams syncParams =
        new BlazeSyncParams.Builder("Sync Working Set", BlazeSyncParams.SyncMode.PARTIAL)
            .addWorkingSet(true)
            .build();
    requestProjectSync(syncParams);
  }
}
