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

import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.google.idea.blaze.base.sync.projectview.SyncDirectoriesWarning;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import java.util.Collection;
import java.util.function.Predicate;

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
    if (syncParams.syncMode() == SyncMode.NO_BUILD
        && !syncParams.backgroundSync()
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
                    BlazeSyncParams.builder()
                        .setTitle("Initial directory update")
                        .setSyncMode(SyncMode.NO_BUILD)
                        .setBlazeBuildParams(BlazeBuildParams.fromProject(project))
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
    switch (syncParams.syncMode()) {
      case NO_BUILD:
      case STARTUP:
        return false;
      case FULL:
        return true;
      case INCREMENTAL:
      case PARTIAL:
        return !syncParams.backgroundSync();
    }
    throw new AssertionError("Unhandled syncMode: " + syncParams.syncMode());
  }

  public void fullProjectSync() {
    BlazeSyncParams syncParams =
        BlazeSyncParams.builder()
            .setTitle("Full Sync")
            .setSyncMode(SyncMode.FULL)
            .setBlazeBuildParams(BlazeBuildParams.fromProject(project))
            .setAddProjectViewTargets(true)
            .setAddWorkingSet(BlazeUserSettings.getInstance().getExpandSyncToWorkingSet())
            .build();
    requestProjectSync(syncParams);
  }

  public void incrementalProjectSync() {
    BlazeSyncParams syncParams =
        BlazeSyncParams.builder()
            .setTitle("Sync")
            .setSyncMode(SyncMode.INCREMENTAL)
            .setBlazeBuildParams(BlazeBuildParams.fromProject(project))
            .setAddProjectViewTargets(true)
            .setAddWorkingSet(BlazeUserSettings.getInstance().getExpandSyncToWorkingSet())
            .build();
    requestProjectSync(syncParams);
  }

  public void partialSync(Collection<? extends TargetExpression> targetExpressions) {
    BlazeSyncParams syncParams =
        BlazeSyncParams.builder()
            .setTitle("Partial Sync")
            .setSyncMode(SyncMode.PARTIAL)
            .setBlazeBuildParams(BlazeBuildParams.fromProject(project))
            .addTargetExpressions(targetExpressions)
            .build();
    requestProjectSync(syncParams);
  }

  public void filterProjectTargets(Predicate<TargetKey> filter) {
    StartupManager.getInstance(project)
        .runWhenProjectIsInitialized(
            () -> SyncPhaseCoordinator.getInstance(project).filterProjectTargets(filter));
  }

  /**
   * Runs a directory-only sync, without any 'blaze build' operations.
   *
   * @param inBackground run in the background, suppressing the normal 'no targets will be build'
   *     warning.
   */
  public void directoryUpdate(boolean inBackground) {
    BlazeSyncParams syncParams =
        BlazeSyncParams.builder()
            .setTitle("Update Directories")
            .setSyncMode(SyncMode.NO_BUILD)
            .setBlazeBuildParams(BlazeBuildParams.fromProject(project))
            .setBackgroundSync(inBackground)
            .build();
    requestProjectSync(syncParams);
  }

  public void workingSetSync() {
    BlazeSyncParams syncParams =
        BlazeSyncParams.builder()
            .setTitle("Sync Working Set")
            .setSyncMode(SyncMode.PARTIAL)
            .setBlazeBuildParams(BlazeBuildParams.fromProject(project))
            .setAddWorkingSet(true)
            .build();
    requestProjectSync(syncParams);
  }
}
