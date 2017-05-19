/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.cpp;

import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.sync.BlazeSyncParams.SyncMode;
import com.google.idea.blaze.base.sync.SyncListener;
import com.google.idea.sdkcompat.cidr.OCWorkspaceModificationTrackersCompatUtils;
import com.google.idea.sdkcompat.transactions.Transactions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.jetbrains.cidr.lang.workspace.OCWorkspace;
import com.jetbrains.cidr.lang.workspace.OCWorkspaceManager;

/** Runs after sync, triggering a rebuild of the symbol tables. */
public class BlazeCppSymbolRebuildSyncListener extends SyncListener.Adapter {

  @Override
  public void onSyncComplete(
      Project project,
      BlazeContext context,
      BlazeImportSettings importSettings,
      ProjectViewSet projectViewSet,
      BlazeProjectData blazeProjectData,
      SyncMode syncMode,
      SyncResult syncResult) {

    OCWorkspace workspace = OCWorkspaceManager.getWorkspace(project);
    if (!(workspace instanceof BlazeCWorkspace)) {
      return;
    }
    rebuildSymbolTables(project);
  }

  private static void rebuildSymbolTables(Project project) {
    Transactions.submitTransactionAndWait(
        () ->
            ApplicationManager.getApplication()
                .runWriteAction(
                    () ->
                        OCWorkspaceModificationTrackersCompatUtils.incrementModificationCounts(
                            project)));
  }
}
