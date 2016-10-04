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
package com.google.idea.blaze.base.sync.actions;

import com.google.idea.blaze.base.actions.BlazeAction;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.google.idea.blaze.base.sync.BlazeSyncManager;
import com.google.idea.blaze.base.sync.BlazeSyncParams;
import com.google.idea.blaze.base.sync.BlazeSyncParams.SyncMode;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;

/** Re-imports (syncs) an Android-Blaze project, without showing the "Import Project" wizard. */
public class FullSyncProjectAction extends BlazeAction {

  public FullSyncProjectAction() {
    super("Non-Incrementally Sync Project with BUILD Files");
  }

  @Override
  public void actionPerformed(final AnActionEvent e) {
    Project project = e.getProject();
    if (project != null) {
      Presentation presentation = e.getPresentation();
      presentation.setEnabled(false);
      try {
        BlazeSyncParams syncParams =
            new BlazeSyncParams.Builder("Full Sync", SyncMode.FULL)
                .addProjectViewTargets(true)
                .addWorkingSet(BlazeUserSettings.getInstance().getExpandSyncToWorkingSet())
                .build();
        BlazeSyncManager.getInstance(project).requestProjectSync(syncParams);
      } finally {
        presentation.setEnabled(true);
      }
    }
  }
}
