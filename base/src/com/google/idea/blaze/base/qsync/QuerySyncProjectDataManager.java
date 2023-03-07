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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.projectview.LanguageSupport;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.intellij.openapi.project.Project;
import javax.annotation.Nullable;

/**
 * Implementation of {@link BlazeProjectDataManager} specific to querysync.
 *
 * <p>TODO: it's not yet clear how useful this class is for querysync. This is currently a pragmatic
 * approach to get more IDE functionality working with querysync. The ideal long term design is not
 * yet determined.
 */
public class QuerySyncProjectDataManager implements BlazeProjectDataManager {

  private final Project project;
  private volatile QuerySyncProjectData projectData;

  public QuerySyncProjectDataManager(Project project) {
    this.project = project;
  }

  @Nullable
  @Override
  public BlazeProjectData getBlazeProjectData() {
    return projectData;
  }

  @Nullable
  @Override
  public BlazeProjectData loadProject(BlazeImportSettings importSettings) {
    WorkspaceLanguageSettings workspaceLanguageSettings =
        LanguageSupport.createWorkspaceLanguageSettings(
            checkNotNull(ProjectViewManager.getInstance(project).getProjectViewSet()));
    // TODO(b/260231317): implement loading if necessary
    projectData = new QuerySyncProjectData(project, importSettings, workspaceLanguageSettings);
    return projectData;
  }

  @Override
  public void saveProject(BlazeImportSettings importSettings, BlazeProjectData projectData) {
    // TODO(b/260231317): implement if necessary
  }
}
