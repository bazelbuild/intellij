/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.qsync.cache;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.intellij.qsync.ArtifactTrackerData.TargetArtifacts;
import com.google.idea.blaze.common.Label;
import java.nio.file.Path;

/** Information about a project dependency that is calculated when the dependency is built. */
@AutoValue
public abstract class ArtifactInfo {

  /** Build label for the dependency. */
  public abstract Label label();

  /**
   * The artifacts relative path (blaze-out/xxx) that can be used to retrieve local copy in the
   * cache.
   */
  public abstract ImmutableList<Path> artifactPath();

  /** Workspace relative sources for this dependency, extracted at dependency build time. */
  public abstract ImmutableSet<Path> source();

  public static ArtifactInfo create(TargetArtifacts proto) {
    // Note, the proto contains a list of sources, we take the parent as we want directories instead
    return new AutoValue_ArtifactInfo(
        Label.of(proto.getTarget()),
        proto.getArtifactPathsList().stream().map(Path::of).collect(toImmutableList()),
        proto.getSrcsList().stream().map(Path::of).collect(toImmutableSet()));
  }

  public TargetArtifacts toProto() {
    return TargetArtifacts.newBuilder()
        .setTarget(label().toString())
        .addAllArtifactPaths(artifactPath().stream().map(Path::toString).collect(toImmutableList()))
        .addAllSrcs(source().stream().map(Path::toString).collect(toImmutableList()))
        .build();
  }

  public static ArtifactInfo empty(Label target) {
    return new AutoValue_ArtifactInfo(target, ImmutableList.of(), ImmutableSet.of());
  }
}
