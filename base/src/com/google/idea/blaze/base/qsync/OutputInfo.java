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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.intellij.qsync.ArtifactTrackerData;
import com.google.idea.blaze.base.command.buildresult.OutputArtifact;

/** A data class that collecting and converting output group artifacts. */
@AutoValue
public abstract class OutputInfo {

  public abstract ImmutableSet<ArtifactTrackerData.TargetToDeps> getArtifactInfos();

  public abstract ImmutableList<OutputArtifact> getJars();

  public abstract ImmutableList<OutputArtifact> getAars();

  public abstract ImmutableList<OutputArtifact> getGeneratedSources();

  public static OutputInfo create(
      ImmutableSet<ArtifactTrackerData.TargetToDeps> artifactInfos,
      ImmutableList<OutputArtifact> jars,
      ImmutableList<OutputArtifact> aars,
      ImmutableList<OutputArtifact> generatedSources) {
    return new AutoValue_OutputInfo(artifactInfos, jars, aars, generatedSources);
  }
}
