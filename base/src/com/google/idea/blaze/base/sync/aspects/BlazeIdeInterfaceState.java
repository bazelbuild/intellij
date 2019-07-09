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
import com.google.devtools.intellij.model.ProjectData.LocalFileOrOutputArtifact;
import com.google.idea.blaze.base.filecache.ArtifactState;
import com.google.idea.blaze.base.ideinfo.ProtoWrapper;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.model.SyncData;
import java.util.Objects;
import javax.annotation.Nullable;

/** Sync state for aspect output files, and their mapping to targets. */
public final class BlazeIdeInterfaceState implements SyncData<ProjectData.BlazeIdeInterfaceState> {

  /**
   * File strings here are any string uniquely identifying output artifacts. It's not used to
   * re-derive an artifact location, though each artifact must map to a unique string.
   */
  final ImmutableMap<String, ArtifactState> ideInfoFileState;

  final ImmutableBiMap<String, TargetKey> ideInfoFileToTargetKey;

  private BlazeIdeInterfaceState(
      ImmutableMap<String, ArtifactState> ideInfoFileState,
      BiMap<String, TargetKey> ideInfoFileToTargetKey) {
    this.ideInfoFileState = ideInfoFileState;
    this.ideInfoFileToTargetKey = ImmutableBiMap.copyOf(ideInfoFileToTargetKey);
  }

  private static BlazeIdeInterfaceState fromProto(ProjectData.BlazeIdeInterfaceState proto) {
    ImmutableBiMap<String, TargetKey> targets =
        ImmutableBiMap.copyOf(
            ProtoWrapper.map(
                proto.getFileToTargetMap(), Functions.identity(), TargetKey::fromProto));
    ImmutableMap.Builder<String, ArtifactState> artifacts = ImmutableMap.builder();
    for (LocalFileOrOutputArtifact output : proto.getIdeInfoFilesList()) {
      ArtifactState state = ArtifactState.fromProto(output);
      if (state == null) {
        continue;
      }
      artifacts.put(state.getKey(), state);
    }
    return new BlazeIdeInterfaceState(artifacts.build(), targets);
  }

  @Override
  public ProjectData.BlazeIdeInterfaceState toProto() {
    ProjectData.BlazeIdeInterfaceState.Builder proto =
        ProjectData.BlazeIdeInterfaceState.newBuilder()
            .putAllFileToTarget(
                ProtoWrapper.map(ideInfoFileToTargetKey, Functions.identity(), TargetKey::toProto));
    for (String key : ideInfoFileState.keySet()) {
      proto.addIdeInfoFiles(ideInfoFileState.get(key).serializeToProto());
    }
    return proto.build();
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
    return Objects.equals(ideInfoFileState, that.ideInfoFileState)
        && Objects.equals(ideInfoFileToTargetKey, that.ideInfoFileToTargetKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(ideInfoFileState, ideInfoFileToTargetKey);
  }

  static Builder builder() {
    return new Builder();
  }

  static class Builder {
    ImmutableMap<String, ArtifactState> ideInfoFileState = null;
    BiMap<String, TargetKey> ideInfoToTargetKey = HashBiMap.create();

    BlazeIdeInterfaceState build() {
      return new BlazeIdeInterfaceState(ideInfoFileState, ideInfoToTargetKey);
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
