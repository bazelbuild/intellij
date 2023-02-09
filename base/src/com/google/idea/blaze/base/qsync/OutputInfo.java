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
package com.google.idea.blaze.base.qsync;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.intellij.qsync.ArtifactTrackerData;
import com.google.idea.blaze.base.command.buildresult.OutputArtifact;
import javax.annotation.Nullable;

/** A data class that collecting and converting output group artifacts. */
@AutoValue
public abstract class OutputInfo {
  public abstract ImmutableMap<String, OutputArtifact> getKeyToArtifact();

  public abstract ImmutableSet<ArtifactTrackerData.TargetToDeps> getArtifactInfos();

  @Nullable
  public OutputArtifact getArtifact(String key) {
    return getKeyToArtifact().get(key);
  }

  public static OutputInfo create(
      ImmutableMap<String, OutputArtifact> keyToArtifact,
      ImmutableSet<ArtifactTrackerData.TargetToDeps> artifactInfos) {
    return new AutoValue_OutputInfo(keyToArtifact, artifactInfos);
  }
}
