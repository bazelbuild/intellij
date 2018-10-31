/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.sync.importer.aggregators;

import com.google.common.collect.Maps;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/** Performs a transitive reduction on the targets */
public abstract class TransitiveAggregator<T> {
  private Map<TargetKey, T> targetKeyToResult;
  // A map from transitiveResources to targetIdeInfo that contains the most likely manifest for this
  // resource.
  protected Map<ArtifactLocation, TargetIdeInfo> transitiveResourcesToTargetIdeInfo;

  protected TransitiveAggregator(TargetMap targetMap) {
    this.transitiveResourcesToTargetIdeInfo = new HashMap<>();
    this.targetKeyToResult = Maps.newHashMap();
    Set<TargetIdeInfo> unaggregated = new HashSet<>(targetMap.targets());
    while (!unaggregated.isEmpty()) {
      List<TargetIdeInfo> toAggregate =
          unaggregated.stream()
              .filter(
                  t ->
                      // All dependencies are either
                      StreamSupport.stream(getDependencies(t).spliterator(), false)
                          // missing from targetMap, or
                          .filter(targetMap::contains)
                          // already aggregated.
                          .allMatch(targetKeyToResult::containsKey))
              .collect(Collectors.toList());
      if (toAggregate.isEmpty()) {
        // Shouldn't happen unless there is a cyclic dependency.
        break;
      }
      toAggregate.forEach(this::aggregate);
      unaggregated.removeAll(toAggregate);
    }
  }

  protected T getOrDefault(TargetKey targetKey, T defaultValue) {
    T result = targetKeyToResult.get(targetKey);
    return result != null ? result : defaultValue;
  }

  private void aggregate(TargetIdeInfo target) {
    T result = createForTarget(target);
    for (TargetKey dep : getDependencies(target)) {
      // Since we aggregate dependencies first, this should already be in the map.
      T depResult = targetKeyToResult.get(dep);
      if (depResult != null) {
        result = reduce(result, depResult);
      }
    }
    targetKeyToResult.put(target.getKey(), result);
  }

  protected abstract Iterable<TargetKey> getDependencies(TargetIdeInfo target);

  /** Creates the initial value for a given target. */
  protected abstract T createForTarget(TargetIdeInfo target);

  /** Reduces two values, sum + new value. May mutate value in place. */
  protected abstract T reduce(T value, T dependencyValue);
}
