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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.devtools.intellij.qsync.ArtifactTrackerData.AppInspectorArtifacts;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.qsync.java.JavaTargetInfo.JavaTargetArtifacts;
import java.nio.file.Path;

/** Information about an app inspector artifacts. */
@AutoValue
public abstract class AppInspectorArtifactInfo {

  /** Build label for the app inspector. */
  public abstract Label label();

  /**
   * The jar artifacts relative path (blaze-out/xxx) that can be used to retrieve local copy in the
   * cache.
   */
  public abstract ImmutableList<Path> aars();

  public static AppInspectorArtifactInfo create(AppInspectorArtifacts proto) {
    // Note, the proto contains a list of sources, we take the parent as we want directories instead
    return new AutoValue_AppInspectorArtifactInfo(
        Label.of(proto.getTarget()),
        proto.getJarsList().stream().map(Path::of).collect(toImmutableList()));
  }

  public JavaTargetArtifacts toProto() {
    return JavaTargetArtifacts.newBuilder()
        .setTarget(label().toString())
        .addAllJars(aars().stream().map(Path::toString).collect(toImmutableList()))
        .build();
  }

  public static AppInspectorArtifactInfo empty(Label target) {
    return new AutoValue_AppInspectorArtifactInfo(target, ImmutableList.of());
  }
}
