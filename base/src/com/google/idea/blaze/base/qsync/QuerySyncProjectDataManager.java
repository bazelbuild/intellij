/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.qsync;

import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.projectview.LanguageSupport;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverImpl;
import com.google.idea.blaze.qsync.BlazeProjectSnapshot;
import javax.annotation.Nullable;

/**
 * Implementation of {@link BlazeProjectDataManager} specific to querysync.
 */
public class QuerySyncProjectDataManager implements BlazeProjectDataManager {

  private volatile QuerySyncProjectData projectData;

  QuerySyncProjectDataManager() {
    projectData = QuerySyncProjectData.EMPTY;
  }

  public void setProjectSnapshot(BlazeProjectSnapshot snapshot) {
    projectData = projectData.toBuilder().setBlazeProject(snapshot).build();
  }

  public void onProjectLoaded(ProjectViewSet projectViewSet) {
    projectData =
        projectData.toBuilder()
            .setProjectViewSet(projectViewSet)
            .setWorkspaceLanguageSettings(
                LanguageSupport.createWorkspaceLanguageSettings(projectViewSet))
            .build();
  }

  public BlazeProjectSnapshot getCurrentProject() {
    return projectData.getBlazeProject();
  }

  @Nullable
  @Override
  public QuerySyncProjectData getBlazeProjectData() {
    return projectData;
  }

  @Nullable
  @Override
  public BlazeProjectData loadProject(BlazeImportSettings importSettings) {
    // TODO(b/260231317): implement loading if necessary
    WorkspaceRoot workspaceRoot = WorkspaceRoot.fromImportSettings(importSettings);
    projectData =
        projectData.toBuilder()
            .setBlazeImportSettings(importSettings)
            .setWorkspaceRoot(workspaceRoot)
            .setWorkspacePathResolver(new WorkspacePathResolverImpl(workspaceRoot))
            .build();
    return projectData;
  }

  @Override
  public void saveProject(BlazeImportSettings importSettings, BlazeProjectData projectData) {
    // TODO(b/260231317): implement saving if necessary
    this.projectData = (QuerySyncProjectData) projectData;
  }
}
