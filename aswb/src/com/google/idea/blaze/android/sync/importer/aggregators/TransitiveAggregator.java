/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.sync.importer.aggregators;

import com.google.common.collect.Maps;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/** Peforms a transitive reduction on the targets */
public abstract class TransitiveAggregator<T> {
  private Map<TargetKey, T> targetKeyToResult;

  protected TransitiveAggregator(TargetMap targetMap) {
    this.targetKeyToResult = Maps.newHashMap();
    for (TargetIdeInfo rule : targetMap.targets()) {
      aggregate(rule.key, targetMap);
    }
  }

  protected T getOrDefault(TargetKey targetKey, T defaultValue) {
    T result = targetKeyToResult.get(targetKey);
    return result != null ? result : defaultValue;
  }

  @Nullable
  private T aggregate(TargetKey targetKey, TargetMap targetMap) {
    T result = targetKeyToResult.get(targetKey);
    if (result != null) {
      return result;
    }

    TargetIdeInfo target = targetMap.get(targetKey);
    if (target == null) {
      return null;
    }

    result = createForTarget(target);

    for (TargetKey dep : getDependencies(target)) {
      T depResult = aggregate(dep, targetMap);
      if (depResult != null) {
        result = reduce(result, depResult);
      }
    }

    targetKeyToResult.put(targetKey, result);
    return result;
  }

  protected abstract Iterable<TargetKey> getDependencies(TargetIdeInfo target);

  /** Creates the initial value for a given target. */
  protected abstract T createForTarget(TargetIdeInfo target);

  /** Reduces two values, sum + new value. May mutate value in place. */
  protected abstract T reduce(T value, T dependencyValue);
}
