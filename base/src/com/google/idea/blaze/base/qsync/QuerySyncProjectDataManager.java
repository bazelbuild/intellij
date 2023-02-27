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
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.projectview.LanguageSupport;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyKey;
import javax.annotation.Nullable;

/**
 * Implementation of {@link BlazeProjectDataManager} specific to querysync.
 *
 * <p>TODO: it's not yet clear how useful this class is for querysync. This is currently a pragmatic
 * approach to get more IDE functionality working with querysync. The ideal long term design is not
 * yet determined.
 */
public class QuerySyncProjectDataManager implements BlazeProjectDataManager {

  private static final NotNullLazyKey<QuerySyncProjectDataManager, Project>
      PROJECT_DATA_MANAGER_KEY =
          NotNullLazyKey.create(
              "QuerySyncProjectDataManager", QuerySyncProjectDataManager::createForProject);

  private static QuerySyncProjectDataManager createForProject(Project project) {
    return new QuerySyncProjectDataManager(project);
  }

  public static QuerySyncProjectDataManager forProject(Project project) {
    return PROJECT_DATA_MANAGER_KEY.getValue(project);
  }

  private final Project project;
  private volatile QuerySyncProjectData projectData;

  private QuerySyncProjectDataManager(Project project) {
    this.project = project;
  }

  public void onProjectLoaded(ProjectViewSet projectViewSet) {
    this.projectData =
        projectData.toBuilder()
            .setWorkspaceLanguageSettings(
                LanguageSupport.createWorkspaceLanguageSettings(projectViewSet))
            .build();
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
    projectData = QuerySyncProjectData.create(project, importSettings);

    return projectData;
  }

  @Override
  public void saveProject(BlazeImportSettings importSettings, BlazeProjectData projectData) {
    // TODO(b/260231317): implement saving if necessary
    this.projectData = (QuerySyncProjectData) projectData;
  }
}
