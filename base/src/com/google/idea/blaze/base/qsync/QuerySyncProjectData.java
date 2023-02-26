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

import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.BlazeVersionData;
import com.google.idea.blaze.base.model.ProjectTargetData;
import com.google.idea.blaze.base.model.RemoteOutputArtifacts;
import com.google.idea.blaze.base.model.SyncState;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverImpl;
import com.google.idea.blaze.qsync.BlazeProject;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

/** Implementation of {@link BlazeProjectData} specific to querysync. */
public class QuerySyncProjectData implements BlazeProjectData {

  private final WorkspacePathResolver workspacePathResolver;
  private final BlazeProject blazeProject;

  QuerySyncProjectData(Project project, BlazeImportSettings importSettings) {
    blazeProject = QuerySyncManager.getInstance(project).getBlazeProject();
    workspacePathResolver =
        new WorkspacePathResolverImpl(WorkspaceRoot.fromImportSettings(importSettings));
  }

  @Nullable
  @Override
  public TargetInfo getTargetInfo(Label label) {
    String kind =
        blazeProject
            .getCurrent()
            .getTargetKind(com.google.idea.blaze.common.Label.of(label.toString()));
    return kind != null ? TargetInfo.builder(label, kind).build() : null;
  }

  @Override
  public ProjectTargetData getTargetData() {
    throw new NotSupportedWithQuerySyncException("getTargetData");
  }

  @Override
  public TargetMap getTargetMap() {
    throw new NotSupportedWithQuerySyncException("getTargetMap");
  }

  @Override
  public BlazeInfo getBlazeInfo() {
    throw new NotSupportedWithQuerySyncException("getBlazeInfo");
  }

  @Override
  public BlazeVersionData getBlazeVersionData() {
    return null;
  }

  @Override
  public WorkspacePathResolver getWorkspacePathResolver() {
    return workspacePathResolver;
  }

  @Override
  public ArtifactLocationDecoder getArtifactLocationDecoder() {
    throw new NotSupportedWithQuerySyncException("getTargetMap");
  }

  @Override
  public WorkspaceLanguageSettings getWorkspaceLanguageSettings() {
    return null;
  }

  @Override
  public RemoteOutputArtifacts getRemoteOutputs() {
    throw new NotSupportedWithQuerySyncException("getRemoteOutputs");
  }

  @Override
  public SyncState getSyncState() {
    throw new NotSupportedWithQuerySyncException("getSyncState");
  }
}
