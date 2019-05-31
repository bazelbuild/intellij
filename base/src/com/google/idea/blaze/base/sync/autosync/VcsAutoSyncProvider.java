/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.sync.autosync;

import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.google.idea.blaze.base.sync.BlazeSyncParams;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.vcs.VcsSyncListener;
import com.intellij.openapi.project.Project;

/**
 * Optionally kicks off an automatic project-wide sync when changes to the base VCS commit are
 * detected.
 */
class VcsAutoSyncProvider implements VcsSyncListener {

  @Override
  public void onVcsSync(Project project) {
    if (Blaze.isBlazeProject(project) && AutoSyncSettings.getInstance().autoSyncOnVcsSync) {
      AutoSyncHandler.getInstance(project).queueIncrementalSync(syncParams());
    }
  }

  private static BlazeSyncParams syncParams() {
    return new BlazeSyncParams.Builder(AutoSyncProvider.AUTO_SYNC_TITLE, SyncMode.INCREMENTAL)
        .addProjectViewTargets(true)
        .addWorkingSet(BlazeUserSettings.getInstance().getExpandSyncToWorkingSet())
        .setBackgroundSync(true)
        .build();
  }
}
