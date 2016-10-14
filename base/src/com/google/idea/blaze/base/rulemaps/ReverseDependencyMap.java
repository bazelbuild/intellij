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
package com.google.idea.blaze.base.rulemaps;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Iterables;
import com.google.idea.blaze.base.ideinfo.RuleIdeInfo;
import com.google.idea.blaze.base.ideinfo.RuleKey;
import com.google.idea.blaze.base.ideinfo.RuleMap;
import com.google.idea.blaze.base.model.primitives.Label;

/** Handy class to create an reverse dep map of all rules */
public class ReverseDependencyMap {
  public static ImmutableMultimap<RuleKey, RuleKey> createRdepsMap(RuleMap ruleMap) {
    ImmutableMultimap.Builder<RuleKey, RuleKey> builder = ImmutableMultimap.builder();
    for (RuleIdeInfo rule : ruleMap.rules()) {
      RuleKey key = rule.key;
      for (Label dep : Iterables.concat(rule.dependencies, rule.runtimeDeps)) {
        RuleKey depKey = RuleKey.forDependency(rule, dep);
        if (ruleMap.contains(depKey)) {
          builder.put(depKey, key);
        }
      }
    }
    return builder.build();
  }
}
