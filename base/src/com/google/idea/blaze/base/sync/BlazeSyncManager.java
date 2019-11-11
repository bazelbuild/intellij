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

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
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
import java.util.concurrent.Future;
import java.util.function.Predicate;
import org.jetbrains.ide.PooledThreadExecutor;

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
              if (!runInitialDirectoryOnlySync(syncParams)) {
                @SuppressWarnings("FutureReturnValueIgnored")
                Future<Void> future = submitTask(project, syncParams);
                return;
              }
              BlazeSyncParams params =
                  BlazeSyncParams.builder()
                      .setTitle("Initial directory update")
                      .setSyncMode(SyncMode.NO_BUILD)
                      .setSyncOrigin(syncParams.syncOrigin())
                      .setBlazeBuildParams(BlazeBuildParams.fromProject(project))
                      .setBackgroundSync(true)
                      .build();
              ListenableFuture<Void> initialSync = submitTask(project, params);
              Futures.addCallback(
                  initialSync,
                  runOnSuccess(
                      () -> {
                        @SuppressWarnings("FutureReturnValueIgnored")
                        Future<Void> future = submitTask(project, syncParams);
                      }),
                  PooledThreadExecutor.INSTANCE);
            });
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  private static ListenableFuture<Void> submitTask(Project project, BlazeSyncParams params) {
    return SyncPhaseCoordinator.getInstance(project).syncProject(params);
  }

  private static FutureCallback<Void> runOnSuccess(Runnable runnable) {
    return new FutureCallback<Void>() {
      @Override
      public void onSuccess(Void aVoid) {
        runnable.run();
      }

      @Override
      public void onFailure(Throwable throwable) {}
    };
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

  /**
   * Runs a non-incremental full project sync, clearing the previous project data.
   *
   * @param reason a description of what triggered this sync
   */
  public void fullProjectSync(String reason) {
    BlazeSyncParams syncParams =
        BlazeSyncParams.builder()
            .setTitle("Full Sync")
            .setSyncMode(SyncMode.FULL)
            .setSyncOrigin(reason)
            .setBlazeBuildParams(BlazeBuildParams.fromProject(project))
            .setAddProjectViewTargets(true)
            .setAddWorkingSet(BlazeUserSettings.getInstance().getExpandSyncToWorkingSet())
            .build();
    requestProjectSync(syncParams);
  }

  /**
   * Syncs the entire project.
   *
   * @param reason a description of what triggered this sync
   */
  public void incrementalProjectSync(String reason) {
    BlazeSyncParams syncParams =
        BlazeSyncParams.builder()
            .setTitle("Sync")
            .setSyncMode(SyncMode.INCREMENTAL)
            .setSyncOrigin(reason)
            .setBlazeBuildParams(BlazeBuildParams.fromProject(project))
            .setAddProjectViewTargets(true)
            .setAddWorkingSet(BlazeUserSettings.getInstance().getExpandSyncToWorkingSet())
            .build();
    requestProjectSync(syncParams);
  }

  /**
   * Runs a partial sync of the given targets.
   *
   * @param reason a description of what triggered this sync
   */
  public void partialSync(Collection<? extends TargetExpression> targetExpressions, String reason) {
    BlazeSyncParams syncParams =
        BlazeSyncParams.builder()
            .setTitle("Partial Sync")
            .setSyncMode(SyncMode.PARTIAL)
            .setSyncOrigin(reason)
            .setBlazeBuildParams(BlazeBuildParams.fromProject(project))
            .addTargetExpressions(targetExpressions)
            .build();
    requestProjectSync(syncParams);
  }

  /**
   * Filters the project targets as part of a coherent sync process, updating derived project data
   * and sending notifications accordingly.
   *
   * @param reason a description of what triggered this action
   */
  public void filterProjectTargets(Predicate<TargetKey> filter, String reason) {
    StartupManager.getInstance(project)
        .runWhenProjectIsInitialized(
            () -> SyncPhaseCoordinator.getInstance(project).filterProjectTargets(filter, reason));
  }

  /**
   * Runs a directory-only sync, without any 'blaze build' operations.
   *
   * @param inBackground run in the background, suppressing the normal 'no targets will be build'
   *     warning.
   * @param reason a description of what triggered this sync
   */
  public void directoryUpdate(boolean inBackground, String reason) {
    BlazeSyncParams syncParams =
        BlazeSyncParams.builder()
            .setTitle("Update Directories")
            .setSyncMode(SyncMode.NO_BUILD)
            .setSyncOrigin(reason)
            .setBlazeBuildParams(BlazeBuildParams.fromProject(project))
            .setBackgroundSync(inBackground)
            .build();
    requestProjectSync(syncParams);
  }

  /**
   * Runs a sync of the 'working set' (the locally modified files).
   *
   * @param reason a description of what triggered this sync
   */
  public void workingSetSync(String reason) {
    BlazeSyncParams syncParams =
        BlazeSyncParams.builder()
            .setTitle("Sync Working Set")
            .setSyncMode(SyncMode.PARTIAL)
            .setSyncOrigin(reason)
            .setBlazeBuildParams(BlazeBuildParams.fromProject(project))
            .setAddWorkingSet(true)
            .build();
    requestProjectSync(syncParams);
  }
}
