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
import com.google.idea.blaze.base.ideinfo.RuleKey;
import com.google.idea.blaze.base.ideinfo.RuleMap;
import com.google.idea.blaze.base.model.primitives.Label;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/** Peforms a transitive reduction on the rule */
public abstract class TransitiveAggregator<T> {
  private Map<RuleKey, T> ruleKeyToResult;

  protected TransitiveAggregator(RuleMap ruleMap) {
    this.ruleKeyToResult = Maps.newHashMap();
    for (RuleIdeInfo rule : ruleMap.rules()) {
      aggregate(rule.key, ruleMap);
    }
  }

  protected T getOrDefault(RuleKey ruleKey, T defaultValue) {
    T result = ruleKeyToResult.get(ruleKey);
    return result != null ? result : defaultValue;
  }

  @Nullable
  private T aggregate(RuleKey ruleKey, RuleMap ruleMap) {
    T result = ruleKeyToResult.get(ruleKey);
    if (result != null) {
      return result;
    }

    RuleIdeInfo rule = ruleMap.get(ruleKey);
    if (rule == null) {
      return null;
    }

    result = createForRule(rule);

    for (Label depLabel : getDependencies(rule)) {
      T depResult = aggregate(RuleKey.forDependency(rule, depLabel), ruleMap);
      if (depResult != null) {
        result = reduce(result, depResult);
      }
    }

    ruleKeyToResult.put(ruleKey, result);
    return result;
  }

  protected abstract Iterable<Label> getDependencies(RuleIdeInfo rule);

  /** Creates the initial value for a given rule. */
  protected abstract T createForRule(RuleIdeInfo rule);

  /** Reduces two values, sum + new value. May mutate value in place. */
  protected abstract T reduce(T value, T dependencyValue);
}
