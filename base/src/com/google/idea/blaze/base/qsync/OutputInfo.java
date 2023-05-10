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
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.intellij.qsync.ArtifactTrackerData.BuildArtifacts;
import com.google.idea.blaze.base.command.buildresult.OutputArtifact;
import com.google.idea.blaze.common.Label;

/** A data class that collecting and converting output group artifacts. */
@AutoValue
public abstract class OutputInfo {

  @VisibleForTesting
  public static final OutputInfo EMPTY =
      create(
          ImmutableSet.of(),
          ImmutableList.of(),
          ImmutableList.of(),
          ImmutableList.of(),
          ImmutableSet.of(),
          0);

  public abstract ImmutableSet<BuildArtifacts> getArtifacts();

  public abstract ImmutableList<OutputArtifact> getJars();

  public abstract ImmutableList<OutputArtifact> getAars();

  public abstract ImmutableList<OutputArtifact> getGeneratedSources();

  public abstract ImmutableSet<Label> getTargetsWithErrors();

  public abstract int getExitCode();

  public boolean isEmpty() {
    return getArtifacts().isEmpty()
        && getJars().isEmpty()
        && getAars().isEmpty()
        && getGeneratedSources().isEmpty();
  }

  @VisibleForTesting
  public abstract Builder toBuilder();

  public static OutputInfo create(
      ImmutableSet<BuildArtifacts> artifacts,
      ImmutableList<OutputArtifact> jars,
      ImmutableList<OutputArtifact> aars,
      ImmutableList<OutputArtifact> generatedSources,
      ImmutableSet<Label> targetsWithErrors,
      int exitCode) {
    return new AutoValue_OutputInfo.Builder()
        .setArtifacts(artifacts)
        .setJars(jars)
        .setAars(aars)
        .setGeneratedSources(generatedSources)
        .setTargetsWithErrors(targetsWithErrors)
        .setExitCode(exitCode)
        .build();
  }

  /** Builder for {@link OutputInfo}. */
  @VisibleForTesting
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setArtifacts(ImmutableSet<BuildArtifacts> value);

    public abstract Builder setJars(ImmutableList<OutputArtifact> value);

    public abstract Builder setAars(ImmutableList<OutputArtifact> value);

    public abstract Builder setGeneratedSources(ImmutableList<OutputArtifact> value);

    public abstract Builder setTargetsWithErrors(ImmutableSet<Label> value);

    public abstract Builder setExitCode(int value);

    public abstract OutputInfo build();
  }
}
