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

import com.google.common.base.Preconditions;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.qsync.BlazeProjectListener;
import com.google.idea.blaze.qsync.project.BlazeProjectSnapshot;
import com.google.idea.blaze.qsync.project.ProjectDefinition;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Implementation of {@link BlazeProjectDataManager} specific to querysync.
 *
 * <p>TODO: it's not yet clear how useful this class is for querysync. This is currently a pragmatic
 * approach to get more IDE functionality working with querysync. The ideal long term design is not
 * yet determined.
 */
public class QuerySyncProjectDataManager implements BlazeProjectDataManager, BlazeProjectListener {

  private final ProjectDeps.Builder projectDepsBuilder;
  private volatile ProjectDeps projectDeps;
  private volatile QuerySyncProjectData projectData;

  public QuerySyncProjectDataManager(ProjectDeps.Builder projectDepsBuilder) {
    this.projectDepsBuilder = projectDepsBuilder;
  }

  private synchronized void ensureProjectDepsCreated(BlazeContext context) {
    if (projectDeps == null) {
      projectDeps = projectDepsBuilder.build(context);
      projectData =
          new QuerySyncProjectData(
              projectDeps.workspacePathResolver(), projectDeps.workspaceLanguageSettings());
    }
  }

  @Override
  public void graphCreated(Context context, BlazeProjectSnapshot instance) {
    Preconditions.checkNotNull(projectData);
    projectData = projectData.withSnapshot(instance);
  }

  synchronized ProjectDefinition getProjectDefinition(Optional<BlazeContext> optionalContext) {
    ensureProjectDepsCreated(optionalContext.orElseGet(BlazeContext::create));
    return projectDeps.projectDefinition();
  }

  @Nullable
  @Override
  public BlazeProjectData getBlazeProjectData() {
    return projectData;
  }

  @Nullable
  @Override
  public BlazeProjectData loadProject(BlazeImportSettings importSettings) {
    // TODO(b/260231317): implement loading if necessary
    return projectData;
  }

  @Override
  public void saveProject(BlazeImportSettings importSettings, BlazeProjectData projectData) {
    // TODO(b/260231317): implement if necessary
  }
}
