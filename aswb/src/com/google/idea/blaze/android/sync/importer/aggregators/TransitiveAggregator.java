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
import com.google.idea.blaze.base.ideinfo.RuleIdeInfo;
import com.google.idea.blaze.base.model.RuleMap;
import com.google.idea.blaze.base.model.primitives.Label;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/** Peforms a transitive reduction on the rule */
public abstract class TransitiveAggregator<T> {
  private Map<Label, T> labelToResult;

  protected TransitiveAggregator(RuleMap ruleMap) {
    this.labelToResult = Maps.newHashMap();
    for (RuleIdeInfo rule : ruleMap.rules()) {
      Label label = rule.label;
      aggregate(label, ruleMap);
    }
  }

  protected T getOrDefault(Label key, T defaultValue) {
    T result = labelToResult.get(key);
    return result != null ? result : defaultValue;
  }

  @Nullable
  private T aggregate(Label label, RuleMap ruleMap) {
    T result = labelToResult.get(label);
    if (result != null) {
      return result;
    }

    RuleIdeInfo rule = ruleMap.get(label);
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

  protected abstract Iterable<Label> getDependencies(RuleIdeInfo rule);

  /** Creates the initial value for a given rule. */
  protected abstract T createForRule(RuleIdeInfo rule);

  /** Reduces two values, sum + new value. May mutate value in place. */
  protected abstract T reduce(T value, T dependencyValue);
}
