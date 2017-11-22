/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.dependencies;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Some minimal data about a blaze target. This is intended to contain the data common to our aspect
 * output, and the per-target data provided by a global dependency index.
 */
public class TargetInfo {
  public final Label label;
  public final String kindString;
  @Nullable public final TestSize testSize;
  @Nullable private final ImmutableList<ArtifactLocation> sources;

  private TargetInfo(
      Label label,
      String kindString,
      @Nullable TestSize testSize,
      @Nullable ImmutableList<ArtifactLocation> sources) {
    this.label = label;
    this.kindString = kindString;
    this.testSize = testSize;
    this.sources = sources;
  }

  @Nullable
  public Kind getKind() {
    return Kind.fromString(kindString);
  }

  /** Returns this targets sources, or Optional#empty if they're not known. */
  public Optional<ImmutableList<ArtifactLocation>> getSources() {
    return Optional.ofNullable(sources);
  }

  @Override
  public String toString() {
    return String.format("%s (%s)", label, kindString);
  }

  public static Builder builder(Label label, String kindString) {
    return new Builder(label, kindString);
  }

  /** Builder class for {@link TargetInfo}. */
  public static class Builder {
    private final Label label;
    private final String kindString;
    @Nullable private TestSize testSize;
    @Nullable private ImmutableList<ArtifactLocation> sources;

    private Builder(Label label, String kindString) {
      this.label = label;
      this.kindString = kindString;
    }

    public Builder setTestSize(TestSize testSize) {
      this.testSize = testSize;
      return this;
    }

    public Builder setSources(ImmutableList<ArtifactLocation> sources) {
      this.sources = sources;
      return this;
    }

    public TargetInfo build() {
      return new TargetInfo(label, kindString, testSize, sources);
    }
  }
}
