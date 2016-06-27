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
import com.google.idea.blaze.base.model.primitives.Label;

import java.util.Map;

public class ReverseDependencyMap {
  public static ImmutableMultimap<Label, Label> createRdepsMap(Map<Label, RuleIdeInfo> ruleMap) {
    ImmutableMultimap.Builder<Label, Label> builder = ImmutableMultimap.builder();
    for (Map.Entry<Label, RuleIdeInfo> entry : ruleMap.entrySet()) {
      Label label = entry.getKey();
      RuleIdeInfo ruleIdeInfo = entry.getValue();
      for (Label dep : Iterables.concat(ruleIdeInfo.dependencies, ruleIdeInfo.runtimeDeps)) {
        if (ruleMap.containsKey(dep)) {
          builder.put(dep, label);
        }
      }
    }
    return builder.build();
  }
}
