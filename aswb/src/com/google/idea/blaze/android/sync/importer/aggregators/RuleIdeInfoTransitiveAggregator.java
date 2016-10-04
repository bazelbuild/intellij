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

import com.google.idea.blaze.base.ideinfo.RuleIdeInfo;
import com.google.idea.blaze.base.model.RuleMap;
import com.google.idea.blaze.base.model.primitives.Label;

/** Transitive aggregator for RuleIdeInfo. */
public abstract class RuleIdeInfoTransitiveAggregator<T> extends TransitiveAggregator<T> {
  protected RuleIdeInfoTransitiveAggregator(RuleMap ruleMap) {
    super(ruleMap);
  }

  @Override
  protected Iterable<Label> getDependencies(RuleIdeInfo ruleIdeInfo) {
    return ruleIdeInfo.dependencies;
  }
}
