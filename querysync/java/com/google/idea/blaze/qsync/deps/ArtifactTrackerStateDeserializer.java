/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.qsync.deps;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.common.vcs.VcsState;
import com.google.idea.blaze.qsync.artifacts.BuildArtifact;
import com.google.idea.blaze.qsync.java.ArtifactTrackerProto;
import com.google.idea.blaze.qsync.java.JavaArtifactInfo;
import com.google.idea.blaze.qsync.project.SnapshotDeserializer;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Deserializes {@link NewArtifactTracker} state from a proto. */
public class ArtifactTrackerStateDeserializer {

  private final ImmutableMap.Builder<Label, TargetBuildInfo> map = ImmutableMap.builder();
  private final Map<String, DependencyBuildContext> buildContexts = Maps.newHashMap();

  public void visit(ArtifactTrackerProto.ArtifactTrackerState proto) {
    proto.getBuildContextsList().forEach(this::visitBuildContext);
    proto.getBuiltDepsMap().entrySet().forEach(this::visitTargetBuildInfo);
  }

  public ImmutableMap<Label, TargetBuildInfo> getBuiltDepsMap() {
    return map.buildOrThrow();
  }

  private void visitBuildContext(ArtifactTrackerProto.BuildContext buildContext) {
    Optional<VcsState> vcsState = Optional.empty();
    if (buildContext.hasVcsState()) {
      vcsState = Optional.of(SnapshotDeserializer.convertVcsState(buildContext.getVcsState()));
    }
    buildContexts.put(
        buildContext.getBuildId(),
        DependencyBuildContext.create(
            buildContext.getBuildId(),
            Instant.ofEpochMilli(buildContext.getStartTimeMillis()),
            vcsState));
  }

  private void visitTargetBuildInfo(Map.Entry<String, ArtifactTrackerProto.TargetBuildInfo> entry) {
    ArtifactTrackerProto.TargetBuildInfo proto = entry.getValue();
    DependencyBuildContext buildContext =
        Preconditions.checkNotNull(buildContexts.get(proto.getBuildId()));
    Label owner = Label.of(entry.getKey());
    if (proto.hasJavaArtifacts()) {
      map.put(
          owner,
          TargetBuildInfo.forJavaTarget(
              convertJavaArtifactInfo(owner, proto.getJavaArtifacts()), buildContext));
    }
  }

  private JavaArtifactInfo convertJavaArtifactInfo(
      Label owner, ArtifactTrackerProto.JavaArtifacts proto) {
    return JavaArtifactInfo.builder()
        .setLabel(owner)
        .setJars(toArtifactList(proto.getJarsList(), owner))
        .setIdeAars(toArtifactList(proto.getIdeAarsList(), owner))
        .setGenSrcs(toArtifactList(proto.getGenSrcsList(), owner))
        .setSources(proto.getSourcesList().stream().map(Path::of).collect(toImmutableSet()))
        .setSrcJars(proto.getSrcJarsList().stream().map(Path::of).collect(toImmutableSet()))
        .setAndroidResourcesPackage(proto.getAndroidResourcesPackage())
        .build();
  }

  private ImmutableList<BuildArtifact> toArtifactList(
      List<ArtifactTrackerProto.Artifact> protos, Label owner) {
    return protos.stream()
        .map(a -> BuildArtifact.create(a.getDigest(), Path.of(a.getPath()), owner))
        .collect(toImmutableList());
  }
}
