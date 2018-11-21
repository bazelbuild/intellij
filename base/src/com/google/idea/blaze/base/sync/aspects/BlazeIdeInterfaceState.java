/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.sync.aspects;

import com.google.common.base.Functions;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.intellij.model.ProjectData;
import com.google.idea.blaze.base.ideinfo.ProtoWrapper;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.model.SyncData;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import java.io.File;
import java.util.Objects;
import javax.annotation.Nullable;

final class BlazeIdeInterfaceState implements SyncData<ProjectData.BlazeIdeInterfaceState> {
  final ImmutableMap<File, Long> fileState;
  final ImmutableBiMap<File, TargetKey> fileToTargetMapKey;
  final WorkspaceLanguageSettings workspaceLanguageSettings;
  final String aspectStrategyName;

  private BlazeIdeInterfaceState(
      ImmutableMap<File, Long> fileState,
      BiMap<File, TargetKey> fileToTargetMapKey,
      WorkspaceLanguageSettings workspaceLanguageSettings,
      String aspectStrategyName) {
    this.fileState = fileState;
    this.fileToTargetMapKey = ImmutableBiMap.copyOf(fileToTargetMapKey);
    this.workspaceLanguageSettings = workspaceLanguageSettings;
    this.aspectStrategyName = aspectStrategyName;
  }

  private static BlazeIdeInterfaceState fromProto(ProjectData.BlazeIdeInterfaceState proto) {
    return new BlazeIdeInterfaceState(
        ProtoWrapper.map(proto.getFileStateMap(), File::new, Functions.identity()),
        ImmutableBiMap.copyOf(
            ProtoWrapper.map(proto.getFileToTargetMap(), File::new, TargetKey::fromProto)),
        WorkspaceLanguageSettings.fromProto(proto.getWorkspaceLanguageSettings()),
        proto.getAspectStrategyName());
  }

  @Override
  public ProjectData.BlazeIdeInterfaceState toProto() {
    return ProjectData.BlazeIdeInterfaceState.newBuilder()
        .putAllFileState(ProtoWrapper.map(fileState, File::getPath, Functions.identity()))
        .putAllFileToTarget(ProtoWrapper.map(fileToTargetMapKey, File::getPath, TargetKey::toProto))
        .setWorkspaceLanguageSettings(workspaceLanguageSettings.toProto())
        .setAspectStrategyName(aspectStrategyName)
        .build();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    BlazeIdeInterfaceState that = (BlazeIdeInterfaceState) o;
    return Objects.equals(fileState, that.fileState)
        && Objects.equals(fileToTargetMapKey, that.fileToTargetMapKey)
        && Objects.equals(workspaceLanguageSettings, that.workspaceLanguageSettings)
        && Objects.equals(aspectStrategyName, that.aspectStrategyName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        fileState, fileToTargetMapKey, workspaceLanguageSettings, aspectStrategyName);
  }

  static Builder builder() {
    return new Builder();
  }

  static class Builder {
    ImmutableMap<File, Long> fileState = null;
    BiMap<File, TargetKey> fileToTargetMapKey = HashBiMap.create();
    WorkspaceLanguageSettings workspaceLanguageSettings;
    String aspectStrategyName;

    BlazeIdeInterfaceState build() {
      return new BlazeIdeInterfaceState(
          fileState, fileToTargetMapKey, workspaceLanguageSettings, aspectStrategyName);
    }
  }

  @Override
  public void insert(ProjectData.SyncState.Builder builder) {
    builder.setBlazeIdeInterfaceState(toProto());
  }

  static class Extractor implements SyncData.Extractor<BlazeIdeInterfaceState> {
    @Nullable
    @Override
    public BlazeIdeInterfaceState extract(ProjectData.SyncState syncState) {
      return syncState.hasBlazeIdeInterfaceState()
          ? BlazeIdeInterfaceState.fromProto(syncState.getBlazeIdeInterfaceState())
          : null;
    }
  }
}
