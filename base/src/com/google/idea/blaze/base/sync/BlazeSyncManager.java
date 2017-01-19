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

import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import java.util.List;

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
  public void requestProjectSync(final BlazeSyncParams syncParams) {
    StartupManager.getInstance(project)
        .runWhenProjectIsInitialized(
            new Runnable() {
              @Override
              public void run() {
                final BlazeImportSettings importSettings =
                    BlazeImportSettingsManager.getInstance(project).getImportSettings();
                if (importSettings == null) {
                  throw new IllegalStateException(
                      String.format(
                          "Attempt to sync non-%s project.", Blaze.buildSystemName(project)));
                }

                final BlazeSyncTask syncTask =
                    new BlazeSyncTask(project, importSettings, syncParams);

                BlazeExecutor.submitTask(project, syncTask);
              }
            });
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

  public void partialSync(List<TargetExpression> targetExpressions) {
    BlazeSyncParams syncParams =
        new BlazeSyncParams.Builder("Partial Sync", BlazeSyncParams.SyncMode.PARTIAL)
            .addTargetExpressions(targetExpressions)
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
