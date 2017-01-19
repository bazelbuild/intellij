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

import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.google.idea.blaze.base.sync.BlazeSyncParams.SyncMode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

/** Syncs the project upon startup. */
public class BlazeSyncStartupActivity implements StartupActivity {

  @Override
  public void runActivity(@NotNull final Project project) {
    BlazeImportSettings importSettings =
        BlazeImportSettingsManager.getInstance(project).getImportSettings();

    if (importSettings != null) {
      BlazeSyncManager.getInstance(project).requestProjectSync(getSyncParams());
    }
  }

  private static BlazeSyncParams getSyncParams() {
    if (BlazeUserSettings.getInstance().getResyncAutomatically()) {
      return new BlazeSyncParams.Builder("Sync", SyncMode.INCREMENTAL)
          .addProjectViewTargets(true)
          .addWorkingSet(BlazeUserSettings.getInstance().getExpandSyncToWorkingSet())
          .build();
    }
    return new BlazeSyncParams.Builder("Sync Project", BlazeSyncParams.SyncMode.STARTUP)
        .addProjectViewTargets(true)
        .addWorkingSet(BlazeUserSettings.getInstance().getExpandSyncToWorkingSet())
        .build();
  }
}
