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

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
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
import com.google.idea.blaze.qsync.BlazeProjectSnapshot;
import com.intellij.openapi.project.Project;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/** Implementation of {@link BlazeProjectData} specific to querysync. */
@AutoValue
public abstract class QuerySyncProjectData implements BlazeProjectData {

  public static QuerySyncProjectData create(Project project, BlazeImportSettings importSettings) {
    return builder()
        .setBlazeProject(QuerySyncManager.getInstance(project).getBlazeProject())
        .setWorkspacePathResolver(
            new WorkspacePathResolverImpl(WorkspaceRoot.fromImportSettings(importSettings)))
        .setWorkspaceLanguageSettings(null)
        .build();
  }

  public static Builder builder() {
    return new AutoValue_QuerySyncProjectData.Builder().setWorkspaceLanguageSettings(null);
  }

  public abstract Builder toBuilder();

  @Nullable
  @Override
  public TargetInfo getTargetInfo(Label label) {
    String kind =
        getBlazeProject()
            .getCurrent()
            .getTargetKind(com.google.idea.blaze.common.Label.of(label.toString()));
    return kind != null ? TargetInfo.builder(label, kind).build() : null;
  }

  @Override
  public ImmutableSet<Label> getTargetsOfKind(Predicate<String> kindPredicate) {
    BlazeProjectSnapshot projectSnapshot = getBlazeProject().getCurrent();
    return projectSnapshot.getAllTargets().stream()
        .filter(l -> kindPredicate.test(projectSnapshot.getTargetKind(l)))
        .map(Label::create)
        .collect(toImmutableSet());
  }

  abstract BlazeProject getBlazeProject();

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
  public abstract WorkspacePathResolver getWorkspacePathResolver();

  @Override
  public ArtifactLocationDecoder getArtifactLocationDecoder() {
    throw new NotSupportedWithQuerySyncException("getTargetMap");
  }

  @Override
  @Nullable
  public abstract WorkspaceLanguageSettings getWorkspaceLanguageSettings();

  @Override
  public RemoteOutputArtifacts getRemoteOutputs() {
    throw new NotSupportedWithQuerySyncException("getRemoteOutputs");
  }

  @Override
  public SyncState getSyncState() {
    throw new NotSupportedWithQuerySyncException("getSyncState");
  }

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setBlazeProject(BlazeProject value);

    public abstract Builder setWorkspacePathResolver(WorkspacePathResolver value);

    public abstract Builder setWorkspaceLanguageSettings(WorkspaceLanguageSettings value);

    public abstract QuerySyncProjectData build();
  }
}
