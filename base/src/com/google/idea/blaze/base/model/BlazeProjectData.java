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
package com.google.idea.blaze.base.model;

import com.google.common.annotations.VisibleForTesting;
import com.google.devtools.intellij.model.ProjectData;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.ideinfo.ProtoWrapper;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.sync.aspects.BlazeIdeInterfaceState;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoderImpl;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import javax.annotation.concurrent.Immutable;

/** The top-level object serialized to cache. */
@Immutable
public final class BlazeProjectData implements ProtoWrapper<ProjectData.BlazeProjectData> {
  private final ProjectTargetData targetData;
  private final BlazeInfo blazeInfo;
  private final BlazeVersionData blazeVersionData;
  private final WorkspacePathResolver workspacePathResolver;
  private final ArtifactLocationDecoder artifactLocationDecoder;
  private final WorkspaceLanguageSettings workspaceLanguageSettings;
  private final SyncState syncState;

  public BlazeProjectData(
      ProjectTargetData targetData,
      BlazeInfo blazeInfo,
      BlazeVersionData blazeVersionData,
      WorkspacePathResolver workspacePathResolver,
      ArtifactLocationDecoder artifactLocationDecoder,
      WorkspaceLanguageSettings workspaceLanguageSettings,
      SyncState syncState) {
    this.targetData = targetData;
    this.blazeInfo = blazeInfo;
    this.blazeVersionData = blazeVersionData;
    this.workspacePathResolver = workspacePathResolver;
    this.artifactLocationDecoder = artifactLocationDecoder;
    this.workspaceLanguageSettings = workspaceLanguageSettings;
    this.syncState = syncState;
  }

  @VisibleForTesting
  public static BlazeProjectData fromProto(
      BuildSystemName buildSystemName, ProjectData.BlazeProjectData proto) {
    BlazeInfo blazeInfo = BlazeInfo.fromProto(buildSystemName, proto.getBlazeInfo());
    WorkspacePathResolver workspacePathResolver =
        WorkspacePathResolver.fromProto(proto.getWorkspacePathResolver());
    ProjectTargetData targetData = parseTargetData(proto);
    return new BlazeProjectData(
        targetData,
        blazeInfo,
        BlazeVersionData.fromProto(proto.getBlazeVersionData()),
        workspacePathResolver,
        new ArtifactLocationDecoderImpl(blazeInfo, workspacePathResolver, targetData.remoteOutputs),
        WorkspaceLanguageSettings.fromProto(proto.getWorkspaceLanguageSettings()),
        SyncState.fromProto(proto.getSyncState()));
  }

  private static ProjectTargetData parseTargetData(ProjectData.BlazeProjectData proto) {
    if (proto.hasTargetData()) {
      return ProjectTargetData.fromProto(proto.getTargetData());
    }
    // handle older version of project data
    TargetMap map = TargetMap.fromProto(proto.getTargetMap());
    BlazeIdeInterfaceState ideInterfaceState =
        BlazeIdeInterfaceState.fromProto(proto.getSyncState().getBlazeIdeInterfaceState());
    RemoteOutputArtifacts remoteOutputs =
        proto.getSyncState().hasRemoteOutputArtifacts()
            ? RemoteOutputArtifacts.fromProto(proto.getSyncState().getRemoteOutputArtifacts())
            : RemoteOutputArtifacts.EMPTY;
    return new ProjectTargetData(map, ideInterfaceState, remoteOutputs);
  }

  @Override
  public ProjectData.BlazeProjectData toProto() {
    return ProjectData.BlazeProjectData.newBuilder()
        .setTargetData(targetData.toProto())
        .setBlazeInfo(blazeInfo.toProto())
        .setBlazeVersionData(blazeVersionData.toProto())
        .setWorkspacePathResolver(workspacePathResolver.toProto())
        .setWorkspaceLanguageSettings(workspaceLanguageSettings.toProto())
        .setSyncState(syncState.toProto())
        .build();
  }

  public ProjectTargetData getTargetData() {
    return targetData;
  }

  public TargetMap getTargetMap() {
    return targetData.targetMap;
  }

  public BlazeInfo getBlazeInfo() {
    return blazeInfo;
  }

  public BlazeVersionData getBlazeVersionData() {
    return blazeVersionData;
  }

  public WorkspacePathResolver getWorkspacePathResolver() {
    return workspacePathResolver;
  }

  public ArtifactLocationDecoder getArtifactLocationDecoder() {
    return artifactLocationDecoder;
  }

  public WorkspaceLanguageSettings getWorkspaceLanguageSettings() {
    return workspaceLanguageSettings;
  }

  public RemoteOutputArtifacts getRemoteOutputs() {
    return targetData.remoteOutputs;
  }

  public SyncState getSyncState() {
    return syncState;
  }

  public static BlazeProjectData loadFromDisk(BuildSystemName buildSystemName, File file)
      throws IOException {
    try (InputStream stream = new GZIPInputStream(new FileInputStream(file))) {
      return fromProto(buildSystemName, ProjectData.BlazeProjectData.parseFrom(stream));
    }
  }

  public void saveToDisk(File file) throws IOException {
    ProjectData.BlazeProjectData proto = toProto();
    try (OutputStream stream = new GZIPOutputStream(new FileOutputStream(file))) {
      proto.writeTo(stream);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof BlazeProjectData)) {
      return false;
    }
    BlazeProjectData other = (BlazeProjectData) o;
    return Objects.equals(targetData, other.targetData)
        && Objects.equals(blazeInfo, other.blazeInfo)
        && Objects.equals(blazeVersionData, other.blazeVersionData)
        && Objects.equals(workspacePathResolver, other.workspacePathResolver)
        && Objects.equals(artifactLocationDecoder, other.artifactLocationDecoder)
        && Objects.equals(workspaceLanguageSettings, other.workspaceLanguageSettings)
        && Objects.equals(syncState, other.syncState);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        targetData,
        blazeInfo,
        blazeVersionData,
        workspaceLanguageSettings,
        artifactLocationDecoder,
        workspaceLanguageSettings,
        syncState);
  }
}
