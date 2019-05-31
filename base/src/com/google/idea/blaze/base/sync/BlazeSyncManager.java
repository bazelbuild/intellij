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

import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.google.idea.blaze.base.sync.projectview.SyncDirectoriesWarning;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import java.util.Collection;

/** Manages syncing and its listeners. */
public class BlazeSyncManager {

  private final Project project;

  public BlazeSyncManager(Project project) {
    this.project = project;
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
              if (BlazeImportSettingsManager.getInstance(project).getImportSettings() == null) {
                throw new IllegalStateException(
                    String.format(
                        "Attempt to sync non-%s project.", Blaze.buildSystemName(project)));
              }
              if (runInitialDirectoryOnlySync(syncParams)) {
                BlazeSyncParams params =
                    new BlazeSyncParams.Builder("Initial directory update", SyncMode.NO_BUILD)
                        .setBackgroundSync(true)
                        .build();
                submitTask(project, params);
              }
              submitTask(project, syncParams);
            });
  }

  private void submitTask(Project project, BlazeSyncParams params) {
    SyncPhaseCoordinator.getInstance(project).syncProject(params);
  }

  private static boolean runInitialDirectoryOnlySync(BlazeSyncParams syncParams) {
    switch (syncParams.syncMode) {
      case NO_BUILD:
      case STARTUP:
        return false;
      case FULL:
        return true;
      case INCREMENTAL:
      case PARTIAL:
        return !syncParams.backgroundSync;
    }
    throw new AssertionError("Unhandled syncMode: " + syncParams.syncMode);
  }

  public void fullProjectSync() {
    BlazeSyncParams syncParams =
        new BlazeSyncParams.Builder("Full Sync", SyncMode.FULL)
            .addProjectViewTargets(true)
            .addWorkingSet(BlazeUserSettings.getInstance().getExpandSyncToWorkingSet())
            .build();
    requestProjectSync(syncParams);
  }

  public void incrementalProjectSync() {
    BlazeSyncParams syncParams =
        new BlazeSyncParams.Builder("Sync", SyncMode.INCREMENTAL)
            .addProjectViewTargets(true)
            .addWorkingSet(BlazeUserSettings.getInstance().getExpandSyncToWorkingSet())
            .build();
    requestProjectSync(syncParams);
  }

  public void partialSync(Collection<? extends TargetExpression> targetExpressions) {
    BlazeSyncParams syncParams =
        new BlazeSyncParams.Builder("Partial Sync", SyncMode.PARTIAL)
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
        new BlazeSyncParams.Builder("Update Directories", SyncMode.NO_BUILD)
            .setBackgroundSync(inBackground)
            .build();
    requestProjectSync(syncParams);
  }

  public void workingSetSync() {
    BlazeSyncParams syncParams =
        new BlazeSyncParams.Builder("Sync Working Set", SyncMode.PARTIAL)
            .addWorkingSet(true)
            .build();
    requestProjectSync(syncParams);
  }
}
