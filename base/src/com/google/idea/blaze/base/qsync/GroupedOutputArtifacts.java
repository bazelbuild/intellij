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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.idea.blaze.base.command.buildresult.LocalFileOutputArtifact;
import com.google.idea.blaze.base.command.buildresult.RemoteOutputArtifact;
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs;
import com.google.idea.blaze.common.artifact.OutputArtifact;
import java.io.File;
import java.util.Set;

/** Output artifacts from the build per {@link OutputGroup}. */
public class GroupedOutputArtifacts {

  public static final GroupedOutputArtifacts EMPTY =
      new GroupedOutputArtifacts(ImmutableListMultimap.of());

  private final ImmutableListMultimap<OutputGroup, OutputArtifact> outputArtifacts;

  private GroupedOutputArtifacts(ImmutableListMultimap<OutputGroup, OutputArtifact> map) {
    this.outputArtifacts = map;
  }

  public GroupedOutputArtifacts(BlazeBuildOutputs buildOutputs, Set<OutputGroup> outputGroups) {
    ImmutableListMultimap.Builder<OutputGroup, OutputArtifact> builder =
        ImmutableListMultimap.builder();
    for (OutputGroup group : outputGroups) {
      ImmutableList<OutputArtifact> artifacts =
          translateOutputArtifacts(
              buildOutputs.getOutputGroupArtifacts(group.outputGroupName()::equals));
      builder.putAll(group, artifacts);
    }
    outputArtifacts = builder.build();
  }

  @VisibleForTesting
  public static Builder builder() {
    return new Builder();
  }

  public ImmutableList<OutputArtifact> get(OutputGroup group) {
    return outputArtifacts.get(group);
  }

  public boolean isEmpty() {
    return outputArtifacts.isEmpty();
  }

  private static ImmutableList<OutputArtifact> translateOutputArtifacts(
      ImmutableList<OutputArtifact> artifacts) {
    return artifacts.stream()
        .map(GroupedOutputArtifacts::translateOutputArtifact)
        .collect(ImmutableList.toImmutableList());
  }

  private static OutputArtifact translateOutputArtifact(OutputArtifact it) {
    if (!(it instanceof RemoteOutputArtifact)) {
      return it;
    }
    RemoteOutputArtifact remoteOutputArtifact = (RemoteOutputArtifact) it;
    String hashId = remoteOutputArtifact.getHashId();
    if (!(hashId.startsWith("/google_src") || hashId.startsWith("/google/src"))) {
      return it;
    }
    File srcfsArtifact = new File(hashId.replaceFirst("/google_src", "/google/src"));
    return new LocalFileOutputArtifact(
        srcfsArtifact, it.getRelativePath(), it.getConfigurationMnemonic(), it.getDigest());
  }

  /**
   * Builder class for {@link GroupedOutputArtifacts}. Just wraps a multimap builder to make test
   * code a bit less verbose.
   */
  public static class Builder {
    private final ImmutableListMultimap.Builder<OutputGroup, OutputArtifact> mapBuilder =
        new ImmutableListMultimap.Builder<>();

    Builder() {}

    @CanIgnoreReturnValue
    public Builder putAll(OutputGroup group, Iterable<OutputArtifact> artifacts) {
      mapBuilder.putAll(group, artifacts);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder putAll(OutputGroup group, OutputArtifact... artifacts) {
      mapBuilder.putAll(group, artifacts);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder put(OutputGroup group, OutputArtifact artifact) {
      mapBuilder.put(group, artifact);
      return this;
    }

    public GroupedOutputArtifacts build() {
      return new GroupedOutputArtifacts(mapBuilder.build());
    }
  }
}
