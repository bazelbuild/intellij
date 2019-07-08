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
package com.google.idea.blaze.java.sync.jdeps;

import com.google.common.base.Functions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo;
import com.google.devtools.intellij.model.ProjectData;
import com.google.devtools.intellij.model.ProjectData.LocalFileOrOutputArtifact;
import com.google.idea.blaze.base.filecache.ArtifactState;
import com.google.idea.blaze.base.ideinfo.ProtoWrapper;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.model.SyncData;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;

final class JdepsState implements SyncData<ProjectData.JdepsState> {

  final ImmutableMap<String, ArtifactState> artifactState;
  final ImmutableMap<String, TargetKey> artifactToTargetMap;

  final ImmutableMap<TargetKey, List<String>> targetToJdeps;

  private JdepsState(
      Map<String, ArtifactState> artifactState,
      Map<String, TargetKey> artifactToTargetMap,
      Map<TargetKey, List<String>> targetToJdeps) {
    this.artifactState = ImmutableMap.copyOf(artifactState);
    this.artifactToTargetMap = ImmutableMap.copyOf(artifactToTargetMap);
    this.targetToJdeps = ImmutableMap.copyOf(targetToJdeps);
  }

  private static JdepsState fromProto(ProjectData.JdepsState proto) {
    ImmutableMap<String, TargetKey> targets =
        ProtoWrapper.map(proto.getFileToTargetMap(), Functions.identity(), TargetKey::fromProto);
    ImmutableMap.Builder<String, ArtifactState> artifacts = ImmutableMap.builder();
    for (LocalFileOrOutputArtifact output : proto.getJdepsFilesList()) {
      ArtifactState state = ArtifactState.fromProto(output);
      if (state == null) {
        continue;
      }
      artifacts.put(state.getKey(), state);
    }
    return new JdepsState(artifacts.build(), targets, parseJdepsMap(proto));
  }

  private static ImmutableMap<TargetKey, List<String>> parseJdepsMap(ProjectData.JdepsState proto) {
    ImmutableMap.Builder<TargetKey, List<String>> map = ImmutableMap.builder();
    for (ProjectData.TargetToJdepsMap.Entry entry : proto.getTargetToJdeps().getEntriesList()) {
      TargetKey key = TargetKey.fromProto(entry.getKey());
      ImmutableList<String> value = ProtoWrapper.internStrings(entry.getValueList());
      map.put(key, value);
    }
    return map.build();
  }

  @Override
  public ProjectData.JdepsState toProto() {
    ProjectData.TargetToJdepsMap.Builder targetToJdepsBuilder =
        ProjectData.TargetToJdepsMap.newBuilder();
    for (Map.Entry<TargetKey, List<String>> entry : targetToJdeps.entrySet()) {
      IntellijIdeInfo.TargetKey key = entry.getKey().toProto();
      List<String> value = entry.getValue();
      targetToJdepsBuilder.addEntries(
          ProjectData.TargetToJdepsMap.Entry.newBuilder().setKey(key).addAllValue(value));
    }
    ProjectData.JdepsState.Builder proto =
        ProjectData.JdepsState.newBuilder()
            .putAllFileToTarget(
                ProtoWrapper.map(artifactToTargetMap, Functions.identity(), TargetKey::toProto))
            .setTargetToJdeps(targetToJdepsBuilder.build());
    for (String key : artifactState.keySet()) {
      proto.addJdepsFiles(artifactState.get(key).serializeToProto());
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
    JdepsState that = (JdepsState) o;
    return Objects.equals(artifactState, that.artifactState)
        && Objects.equals(artifactToTargetMap, that.artifactToTargetMap)
        && Objects.equals(targetToJdeps, that.targetToJdeps);
  }

  @Override
  public int hashCode() {
    return Objects.hash(artifactState, artifactToTargetMap, targetToJdeps);
  }

  static Builder builder() {
    return new Builder();
  }

  static class Builder {
    Map<String, ArtifactState> artifactState = null;
    Map<String, TargetKey> artifactToTargetMap = Maps.newHashMap();
    Map<TargetKey, List<String>> targetToJdeps = Maps.newHashMap();

    JdepsState build() {
      return new JdepsState(artifactState, artifactToTargetMap, targetToJdeps);
    }
  }

  @Override
  public void insert(ProjectData.SyncState.Builder builder) {
    builder.setJdepsState(toProto());
  }

  static class Extractor implements SyncData.Extractor<JdepsState> {
    @Nullable
    @Override
    public JdepsState extract(ProjectData.SyncState syncState) {
      return syncState.hasJdepsState() ? JdepsState.fromProto(syncState.getJdepsState()) : null;
    }
  }
}
