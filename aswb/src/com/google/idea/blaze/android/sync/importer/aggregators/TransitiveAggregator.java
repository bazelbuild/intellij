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
import com.google.idea.blaze.base.model.primitives.Label;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Peforms a transitive reduction on the rule
 */
public abstract class TransitiveAggregator<Rule, T> {
  private Map<Label, T> labelToResult;

  protected TransitiveAggregator(@NotNull Map<Label, Rule> ruleMap) {
    this.labelToResult = Maps.newHashMap();
    for (Label label : ruleMap.keySet()) {
      aggregate(label, ruleMap);
    }
  }

  @NotNull
  protected T getOrDefault(@NotNull Label key, @NotNull T defaultValue) {
    T result = labelToResult.get(key);
    return result != null ? result : defaultValue;
  }

  @Nullable
  private T aggregate(
    @NotNull Label label,
    @NotNull Map<Label, Rule> ruleMap) {
    T result = labelToResult.get(label);
    if (result != null) {
      return result;
    }

    Rule rule = ruleMap.get(label);
    if (rule == null) {
      return null;
    }

    result = createForRule(rule);

    for (Label depLabel : getDependencies(rule)) {
      T depResult = aggregate(depLabel, ruleMap);
      if (depResult != null) {
        result = reduce(result, depResult);
      }
    }

    labelToResult.put(label, result);
    return result;
  }

  protected abstract Iterable<Label> getDependencies(@NotNull Rule rule);

  /**
   * Creates the initial value for a given rule.
   */
  @NotNull
  protected abstract T createForRule(@NotNull Rule rule);

  /**
   * Reduces two values, sum + new value. May mutate value in place.
   */
  @NotNull
  protected abstract T reduce(@NotNull T value, @NotNull T dependencyValue);
}
