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
package com.google.idea.blaze.base.sync;

import com.google.auto.value.AutoValue;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.model.BlazeVersionData;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.sync.aspects.BlazeIdeInterface.BlazeBuildOutputs;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.workspace.WorkingSet;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;

/**
 * All the information gathered during the build phase of sync, used as input to the project update
 * phase.
 */
@AutoValue
public abstract class BlazeSyncBuildResult {

  public abstract ProjectViewSet getProjectViewSet();

  public abstract WorkspaceLanguageSettings getLanguageSettings();

  public abstract BlazeInfo getBlazeInfo();

  public abstract BlazeVersionData getBlazeVersionData();

  public abstract WorkingSet getWorkingSet();

  public abstract WorkspacePathResolver getWorkspacePathResolver();

  public abstract BlazeBuildOutputs getBuildResult();

  public static Builder builder() {
    return new AutoValue_BlazeSyncBuildResult.Builder();
  }

  /** A builder for {@link BlazeSyncBuildResult} objects. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setProjectViewSet(ProjectViewSet projectViewSet);

    public abstract Builder setLanguageSettings(WorkspaceLanguageSettings languageSettings);

    public abstract Builder setBlazeInfo(BlazeInfo blazeInfo);

    public abstract Builder setBlazeVersionData(BlazeVersionData blazeVersionData);

    public abstract Builder setWorkingSet(WorkingSet workingSet);

    public abstract Builder setWorkspacePathResolver(WorkspacePathResolver pathResolver);

    public abstract Builder setBuildResult(BlazeBuildOutputs buildResult);

    public abstract BlazeSyncBuildResult build();
  }
}
