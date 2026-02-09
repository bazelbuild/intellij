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

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.devtools.intellij.model.ProjectData;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.sync.aspects.BlazeIdeInterfaceState;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoderImpl;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.blaze.common.BuildTarget;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.jetbrains.annotations.Nullable;

/**
 * The top-level object serialized to cache.
 */
@AutoValue
public abstract class BlazeProjectData {

  public abstract ProjectTargetData targetData();

  public abstract BlazeInfo blazeInfo();

  public abstract BlazeVersionData blazeVersionData();

  public abstract WorkspacePathResolver workspacePathResolver();

  public abstract ArtifactLocationDecoder artifactLocationDecoder();

  public abstract WorkspaceLanguageSettings workspaceLanguageSettings();

  public abstract ExternalWorkspaceData externalWorkspaceData();

  public abstract SyncState syncState();

  public static Builder builder() {
    return new AutoValue_BlazeProjectData.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder targetData(ProjectTargetData value);

    public abstract Builder blazeInfo(BlazeInfo value);

    public abstract Builder blazeVersionData(BlazeVersionData value);

    public abstract Builder workspacePathResolver(WorkspacePathResolver value);

    public abstract Builder artifactLocationDecoder(ArtifactLocationDecoder value);

    public abstract Builder workspaceLanguageSettings(WorkspaceLanguageSettings value);

    public abstract Builder externalWorkspaceData(ExternalWorkspaceData value);

    public abstract Builder syncState(SyncState value);

    public abstract BlazeProjectData build();
  }

  @VisibleForTesting
  public static BlazeProjectData fromProto(
      BuildSystemName buildSystemName, ProjectData.BlazeProjectData proto) {
    final var blazeInfo = BlazeInfo.fromProto(buildSystemName, proto.getBlazeInfo());
    final var workspacePathResolver = WorkspacePathResolver.fromProto(proto.getWorkspacePathResolver());
    final var targetData = parseTargetData(buildSystemName, proto);

    return builder()
        .targetData(targetData)
        .blazeInfo(blazeInfo)
        .blazeVersionData(BlazeVersionData.fromProto(proto.getBlazeVersionData()))
        .workspacePathResolver(workspacePathResolver)
        .artifactLocationDecoder(
            new ArtifactLocationDecoderImpl(blazeInfo, workspacePathResolver, targetData.remoteOutputs))
        .workspaceLanguageSettings(WorkspaceLanguageSettings.fromProto(proto.getWorkspaceLanguageSettings()))
        .externalWorkspaceData(ExternalWorkspaceData.fromProto(proto.getExternalWorkspaceData()))
        .syncState(SyncState.fromProto(proto.getSyncState()))
        .build();
  }

  private static ProjectTargetData parseTargetData(BuildSystemName buildSystemName,
      ProjectData.BlazeProjectData proto) {
    if (proto.hasTargetData()) {
      return ProjectTargetData.fromProto(buildSystemName, proto.getTargetData());
    }
    // handle older version of project data
    TargetMap map = TargetMap.fromProto(proto.getTargetMap());
    BlazeIdeInterfaceState ideInterfaceState =
        BlazeIdeInterfaceState.fromProto(proto.getSyncState().getBlazeIdeInterfaceState());
    RemoteOutputArtifacts remoteOutputs =
        proto.getSyncState().hasRemoteOutputArtifacts()
            ? RemoteOutputArtifacts.fromProto(buildSystemName, proto.getSyncState().getRemoteOutputArtifacts())
            : RemoteOutputArtifacts.EMPTY;
    return new ProjectTargetData(map, ideInterfaceState, remoteOutputs);
  }

  @VisibleForTesting
  public ProjectData.BlazeProjectData toProto() {
    return ProjectData.BlazeProjectData.newBuilder()
        .setTargetData(targetData().toProto())
        .setBlazeVersionData(blazeVersionData().toProto())
        .setBlazeInfo(blazeInfo().toProto())
        .setWorkspacePathResolver(workspacePathResolver().toProto())
        .setWorkspaceLanguageSettings(workspaceLanguageSettings().toProto())
        .setSyncState(syncState().toProto())
        .setExternalWorkspaceData(externalWorkspaceData().toProto())
        .build();
  }

  private TargetInfo getTargetInfo(Label label) {
    final var map = targetMap();

    // look for a plain target first
    final var target = map.get(TargetKey.forPlainTarget(label));
    if (target != null) {
      return target.toTargetInfo();
    }

    // otherwise just return any matching target
    return map.targets().stream()
        .filter(t -> Objects.equals(label, t.getKey().getLabel()))
        .findFirst()
        .map(TargetIdeInfo::toTargetInfo)
        .orElse(null);
  }

  @Nullable
  public BuildTarget buildTarget(Label label) {
    final var targetInfo = getTargetInfo(label);
    if (targetInfo == null) {
      return null;
    }

    return BuildTarget.create(com.google.idea.blaze.common.Label.of(targetInfo.label.toString()), targetInfo.kindString);
  }

  public ImmutableList<Label> targets() {
    return targetMap().targets().stream()
        .map(it -> it.getKey().getLabel())
        .collect(ImmutableList.toImmutableList());
  }

  public TargetMap targetMap() {
    return targetData().targetMap();
  }

  public RemoteOutputArtifacts remoteOutputs() {
    return targetData().remoteOutputs;
  }

  public static BlazeProjectData loadFromDisk(BuildSystemName buildSystemName, File file) throws IOException {
    try (final var stream = new GZIPInputStream(new FileInputStream(file))) {
      return fromProto(buildSystemName, ProjectData.BlazeProjectData.parseFrom(stream));
    }
  }

  public void saveToDisk(File file) throws IOException {
    final var proto = toProto();
    try (final var stream = new GZIPOutputStream(new FileOutputStream(file))) {
      proto.writeTo(stream);
    }
  }
}
