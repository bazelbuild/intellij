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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo;
import com.google.devtools.intellij.model.ProjectData;
import com.google.idea.blaze.base.ideinfo.ProtoWrapper;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.model.SyncData;
import java.io.File;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

class JdepsState implements SyncData<ProjectData.JdepsState> {
  final ImmutableMap<File, Long> fileState;
  final ImmutableMap<File, TargetKey> fileToTargetMap;
  final ImmutableMap<TargetKey, List<String>> targetToJdeps;

  private JdepsState(
      Map<File, Long> fileState,
      Map<File, TargetKey> fileToTargetMap,
      Map<TargetKey, List<String>> targetToJdeps) {
    this.fileState = ImmutableMap.copyOf(fileState);
    this.fileToTargetMap = ImmutableMap.copyOf(fileToTargetMap);
    this.targetToJdeps = ImmutableMap.copyOf(targetToJdeps);
  }

  private static JdepsState fromProto(ProjectData.JdepsState proto) {
    ImmutableMap.Builder<TargetKey, List<String>> targetToJdepsBuilder = ImmutableMap.builder();
    for (ProjectData.TargetToJdepsMap.Entry entry : proto.getTargetToJdeps().getEntriesList()) {
      TargetKey key = TargetKey.fromProto(entry.getKey());
      List<String> value = entry.getValueList();
      targetToJdepsBuilder.put(key, value);
    }
    return new JdepsState(
        ProtoWrapper.map(proto.getFileStateMap(), File::new, Functions.identity()),
        ProtoWrapper.map(proto.getFileToTargetMap(), File::new, TargetKey::fromProto),
        targetToJdepsBuilder.build());
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
    return ProjectData.JdepsState.newBuilder()
        .putAllFileState(ProtoWrapper.map(fileState, File::getPath, Functions.identity()))
        .putAllFileToTarget(ProtoWrapper.map(fileToTargetMap, File::getPath, TargetKey::toProto))
        .setTargetToJdeps(targetToJdepsBuilder.build())
        .build();
  }

  static Builder builder() {
    return new Builder();
  }

  static class Builder {
    Map<File, Long> fileState = null;
    Map<File, TargetKey> fileToTargetMap = Maps.newHashMap();
    Map<TargetKey, List<String>> targetToJdeps = Maps.newHashMap();

    JdepsState build() {
      return new JdepsState(fileState, fileToTargetMap, targetToJdeps);
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
