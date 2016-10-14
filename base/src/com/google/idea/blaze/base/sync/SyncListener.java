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

import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;

/** Extension interface for listening to syncs. */
public interface SyncListener {
  ExtensionPointName<SyncListener> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.SyncListener");

  /** Result of the sync operation */
  enum SyncResult {
    /** Full success */
    SUCCESS,
    /** The user has errors in their BUILD files or compilation errors */
    PARTIAL_SUCCESS,
    /** The user cancelled */
    CANCELLED,
    /** Failure -- sync could not complete */
    FAILURE,
  }

  /** Called after open documents have been saved, prior to starting the blaze sync. */
  void onSyncStart(Project project, BlazeContext context);

  /** Called on successful (or partially successful) completion of a sync */
  void onSyncComplete(
      Project project,
      BlazeContext context,
      BlazeImportSettings importSettings,
      ProjectViewSet projectViewSet,
      BlazeProjectData blazeProjectData,
      SyncResult syncResult);

  /** Guaranteed to be called once per sync, regardless of whether it successfully completed */
  void afterSync(Project project, BlazeContext context, SyncResult syncResult);

  /** Convenience adapter class. */
  abstract class Adapter implements SyncListener {

    @Override
    public void onSyncStart(Project project, BlazeContext context) {}

    @Override
    public void onSyncComplete(
        Project project,
        BlazeContext context,
        BlazeImportSettings importSettings,
        ProjectViewSet projectViewSet,
        BlazeProjectData blazeProjectData,
        SyncResult syncResult) {}

    @Override
    public void afterSync(Project project, BlazeContext context, SyncResult syncResult) {}
  }
}
