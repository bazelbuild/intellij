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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Sets;
import com.google.idea.blaze.base.command.buildresult.OutputArtifact;
import com.google.idea.blaze.base.command.buildresult.RemoteOutputArtifact;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/** The result of the blaze build sync step. */
public class BlazeBuildOutputs {
  /** All output artifacts from the blaze build step. */
  public final ImmutableListMultimap<String, OutputArtifact> perOutputGroupArtifacts;

  public final BuildResult buildResult;

  public BlazeBuildOutputs(
      ImmutableListMultimap<String, OutputArtifact> perOutputGroupArtifacts,
      BuildResult buildResult) {
    this.perOutputGroupArtifacts = perOutputGroupArtifacts;
    this.buildResult = buildResult;
  }

  /** Merges this {@link BlazeBuildOutputs} with a newer set of outputs. */
  public BlazeBuildOutputs updateOutputs(BlazeBuildOutputs nextOutputs) {
    BuildResult result = BuildResult.combine(buildResult, nextOutputs.buildResult);
    ImmutableListMultimap.Builder<String, OutputArtifact> artifacts =
        ImmutableListMultimap.<String, OutputArtifact>builder().putAll(perOutputGroupArtifacts);
    Set<String> groups =
        Sets.union(perOutputGroupArtifacts.keySet(), nextOutputs.perOutputGroupArtifacts.keySet());
    for (String group : groups) {
      List<OutputArtifact> first = perOutputGroupArtifacts.get(group);
      List<OutputArtifact> second = nextOutputs.perOutputGroupArtifacts.get(group);
      artifacts.putAll(group, combineOutputs(first, second));
    }
    return new BlazeBuildOutputs(artifacts.build(), result);
  }

  private static Collection<OutputArtifact> combineOutputs(
      @Nullable List<OutputArtifact> first, @Nullable List<OutputArtifact> second) {
    if (first == null) {
      return second == null ? ImmutableList.of() : second;
    }
    if (second == null) {
      return first;
    }
    Map<String, OutputArtifact> combined = new LinkedHashMap<>();
    first.forEach(a -> combined.put(a.getKey(), a));
    second.forEach(
        a -> {
          OutputArtifact other = combined.get(a.getKey());
          if (replaceOutput(other, a)) {
            combined.put(a.getKey(), a);
          }
        });
    return combined.values();
  }

  private static boolean replaceOutput(@Nullable OutputArtifact first, OutputArtifact second) {
    if (!(first instanceof RemoteOutputArtifact) || !(second instanceof RemoteOutputArtifact)) {
      // local syncs are done serially, so just use sync finish time
      return true;
    }
    return ((RemoteOutputArtifact) first).getSyncTimeMillis()
        < ((RemoteOutputArtifact) second).getSyncTimeMillis();
  }
}
