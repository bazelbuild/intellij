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
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimaps;
import com.google.idea.blaze.base.command.buildresult.BepArtifactData;
import com.google.idea.blaze.base.command.buildresult.OutputArtifact;
import com.google.idea.blaze.base.command.buildresult.ParsedBepOutput;
import com.google.idea.blaze.base.sync.aspects.strategy.AspectStrategy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/** The result of the blaze build sync step. */
public class BlazeBuildOutputs {

  public static BlazeBuildOutputs noOutputs(BuildResult buildResult) {
    return new BlazeBuildOutputs(buildResult, ImmutableMap.of(), ImmutableList.of());
  }

  public static BlazeBuildOutputs fromParsedBepOutput(
      BuildResult result, ParsedBepOutput parsedOutput) {
    ImmutableList<String> id =
        parsedOutput.buildId != null ? ImmutableList.of(parsedOutput.buildId) : ImmutableList.of();
    return new BlazeBuildOutputs(result, parsedOutput.getFullArtifactData(), id);
  }

  public final BuildResult buildResult;
  public final ImmutableList<String> buildIds;
  private final ImmutableMap<String, BepArtifactData> artifacts;

  /** The artifacts transitively associated with each top-level target. */
  private final ImmutableSetMultimap<String, OutputArtifact> perTargetArtifacts;

  private BlazeBuildOutputs(
      BuildResult buildResult,
      Map<String, BepArtifactData> artifacts,
      ImmutableList<String> buildIds) {
    this.buildResult = buildResult;
    this.artifacts = ImmutableMap.copyOf(artifacts);
    this.buildIds = buildIds;

    ImmutableSetMultimap.Builder<String, OutputArtifact> perTarget = ImmutableSetMultimap.builder();
    artifacts.values().forEach(a -> a.topLevelTargets.forEach(t -> perTarget.put(t, a.artifact)));
    this.perTargetArtifacts = perTarget.build();
  }

  @VisibleForTesting
  public ImmutableList<OutputArtifact> getOutputGroupArtifacts(
      Predicate<String> outputGroupFilter) {
    return artifacts.values().stream()
        .filter(a -> a.outputGroups.stream().anyMatch(outputGroupFilter))
        .map(a -> a.artifact)
        .collect(toImmutableList());
  }

  /** Merges this {@link BlazeBuildOutputs} with a newer set of outputs. */
  public BlazeBuildOutputs updateOutputs(BlazeBuildOutputs nextOutputs) {

    // first combine common artifacts
    Map<String, BepArtifactData> combined = new LinkedHashMap<>(artifacts);
    for (Map.Entry<String, BepArtifactData> e : nextOutputs.artifacts.entrySet()) {
      BepArtifactData a = e.getValue();
      combined.compute(e.getKey(), (k, v) -> v == null ? a : v.update(a));
    }

    // then iterate over targets, throwing away old data for rebuilt targets and updating output
    // data accordingly
    for (String target : perTargetArtifacts.keySet()) {
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
        BepArtifactData data = combined.get(old.getKey());
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
        BuildResult.combine(buildResult, nextOutputs.buildResult),
        combined,
        ImmutableList.<String>builder().addAll(buildIds).addAll(nextOutputs.buildIds).build());
  }

  ImmutableSetMultimap<ImmutableSet<String>, OutputArtifact> getAllTrackedOutputs() {
    // don't track intellij-info.txt outputs -- they're already tracked in
    // BlazeIdeInterfaceState
    Predicate<String> pathFilter = AspectStrategy.ASPECT_OUTPUT_FILE_PREDICATE.negate();
    return ImmutableSetMultimap.copyOf(
        Multimaps.invertFrom(
            Multimaps.transformValues(
                Multimaps.forMap(
                    Multimaps.invertFrom(
                            Multimaps.filterValues(
                                perTargetArtifacts, a -> pathFilter.test(a.getRelativePath())),
                            HashMultimap.create())
                        .asMap()),
                ImmutableSet::copyOf),
            HashMultimap.create()));
  }

  /** Returns the {@link OutputArtifact}s we want to track between syncs. */
  ImmutableSet<OutputArtifact> getTrackedRemoteOutputs() {
    // don't track intellij-info.txt outputs -- they're already tracked in
    // BlazeIdeInterfaceState
    Predicate<String> pathFilter = AspectStrategy.ASPECT_OUTPUT_FILE_PREDICATE.negate();
    return getOutputGroupArtifacts(group -> true).stream()
        .filter(a -> pathFilter.test(a.getRelativePath()))
        .collect(toImmutableSet());
  }
}
