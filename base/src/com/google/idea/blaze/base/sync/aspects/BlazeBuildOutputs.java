/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.sync.aspects;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.idea.blaze.base.command.buildresult.OutputArtifact;
import com.google.idea.blaze.base.command.buildresult.ParsedBepOutput;
import com.google.idea.blaze.base.model.primitives.Label;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/** The result of the blaze build sync step. */
public class BlazeBuildOutputs {

  public static BlazeBuildOutputs noOutputs(BuildResult buildResult) {
    return new BlazeBuildOutputs(buildResult, ImmutableMap.of());
  }

  public static BlazeBuildOutputs fromParsedBepOutput(
      BuildResult result, ParsedBepOutput parsedOutput) {
    ImmutableListMultimap<String, OutputArtifact> outputGroupOutputs =
        parsedOutput.getPerOutputGroupArtifacts(path -> true);
    ImmutableListMultimap<Label, OutputArtifact> targetOutputArtifacts =
        parsedOutput.getPerTargetOutputArtifacts(path -> true);

    Map<String, ArtifactData.Builder> builders = new LinkedHashMap<>();
    targetOutputArtifacts.forEach(
        (label, output) ->
            builders.compute(
                output.getKey(),
                (key, builder) -> {
                  if (builder == null) {
                    builder = new ArtifactData.Builder(output);
                  }
                  builder.targets.add(label);
                  return builder;
                }));
    outputGroupOutputs.forEach(
        (group, output) ->
            builders.compute(
                output.getKey(),
                (key, builder) -> {
                  if (builder == null) {
                    builder = new ArtifactData.Builder(output);
                  }
                  builder.outputGroups.add(group);
                  return builder;
                }));
    Map<String, ArtifactData> allOutputs =
        builders.values().stream()
            .map(ArtifactData.Builder::build)
            .filter(Objects::nonNull)
            .collect(toImmutableMap(a -> a.artifact.getKey(), a -> a, (a, b) -> a));
    return new BlazeBuildOutputs(result, allOutputs);
  }

  public final BuildResult buildResult;

  private final ImmutableMap<String, ArtifactData> artifacts;

  /** The artifacts transitively associated with each top-level target. */
  private final ImmutableSetMultimap<Label, OutputArtifact> perTargetArtifacts;

  private BlazeBuildOutputs(BuildResult buildResult, Map<String, ArtifactData> artifacts) {
    this.buildResult = buildResult;
    this.artifacts = ImmutableMap.copyOf(artifacts);

    ImmutableSetMultimap.Builder<Label, OutputArtifact> perTarget = ImmutableSetMultimap.builder();
    artifacts.values().forEach(a -> a.topLevelTargets.forEach(t -> perTarget.put(t, a.artifact)));
    this.perTargetArtifacts = perTarget.build();
  }

  ImmutableList<OutputArtifact> getOutputGroupArtifacts(Predicate<String> outputGroupFilter) {
    return artifacts.values().stream()
        .filter(a -> a.outputGroups.stream().anyMatch(outputGroupFilter))
        .map(a -> a.artifact)
        .collect(toImmutableList());
  }

  /** Merges this {@link BlazeBuildOutputs} with a newer set of outputs. */
  public BlazeBuildOutputs updateOutputs(BlazeBuildOutputs nextOutputs) {

    // first combine common artifacts
    Map<String, ArtifactData> combined = new LinkedHashMap<>(artifacts);
    for (Map.Entry<String, ArtifactData> e : nextOutputs.artifacts.entrySet()) {
      ArtifactData a = e.getValue();
      combined.compute(e.getKey(), (k, v) -> v == null ? a : v.update(a));
    }

    // then iterate over targets, throwing away old data for rebuilt targets and updating output
    // data accordingly
    for (Label target : perTargetArtifacts.keySet()) {
      if (!nextOutputs.perTargetArtifacts.containsKey(target)) {
        continue;
      }
      Set<OutputArtifact> oldOutputs = perTargetArtifacts.get(target);
      Set<OutputArtifact> newOutputs = nextOutputs.perTargetArtifacts.get(target);

      // remove out of date target associations
      for (OutputArtifact old : oldOutputs) {
        if (newOutputs.contains(old)) {
          continue;
        }
        // no longer output by this target; need to update target associations
        ArtifactData data = combined.get(old.getKey());
        if (data != null) {
          data = data.removeTargetAssociation(target);
        }
        if (data == null) {
          combined.remove(old.getKey());
        } else {
          combined.put(old.getKey(), data);
        }
      }
    }
    return new BlazeBuildOutputs(
        BuildResult.combine(buildResult, nextOutputs.buildResult), combined);
  }

  /** All the relevant output data for a single {@link OutputArtifact}. */
  private static final class ArtifactData {
    private final OutputArtifact artifact;
    /** The output groups this artifact belongs to. */
    private final ImmutableSet<String> outputGroups;
    /** The top-level targets this artifact is transitively associated with. */
    private final ImmutableSet<Label> topLevelTargets;

    private ArtifactData(
        OutputArtifact artifact,
        Collection<String> outputGroups,
        Collection<Label> topLevelTargets) {
      this.artifact = artifact;
      this.outputGroups = ImmutableSet.copyOf(outputGroups);
      this.topLevelTargets = ImmutableSet.copyOf(topLevelTargets);
    }

    @Override
    public int hashCode() {
      return artifact.getKey().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof ArtifactData && artifact.equals(((ArtifactData) obj).artifact);
    }

    /** Returns null if this was the only top-level target the artifact was associated with. */
    @Nullable
    private ArtifactData removeTargetAssociation(Label target) {
      List<Label> newTargets = new ArrayList<>(topLevelTargets);
      newTargets.remove(target);
      return newTargets.isEmpty()
          ? null
          : new ArtifactData(artifact, outputGroups, ImmutableList.copyOf(newTargets));
    }

    /** Combines this data with a newer version. */
    private ArtifactData update(ArtifactData newer) {
      Preconditions.checkState(artifact.getKey().equals(newer.artifact.getKey()));
      return new ArtifactData(
          newer.artifact,
          ImmutableSet.<String>builder().addAll(outputGroups).addAll(newer.outputGroups).build(),
          ImmutableSet.<Label>builder()
              .addAll(topLevelTargets)
              .addAll(newer.topLevelTargets)
              .build());
    }

    private static class Builder {
      final OutputArtifact artifact;
      final List<String> outputGroups = new ArrayList<>();
      final List<Label> targets = new ArrayList<>();

      Builder(OutputArtifact artifact) {
        this.artifact = artifact;
      }

      @Nullable
      ArtifactData build() {
        if (outputGroups.isEmpty() || targets.isEmpty()) {
          return null;
        }
        return new ArtifactData(
            artifact, ImmutableList.copyOf(outputGroups), ImmutableList.copyOf(targets));
      }
    }
  }
}
