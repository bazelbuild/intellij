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

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.idea.blaze.base.ideinfo.AndroidRuleIdeInfo;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.RuleIdeInfo;
import com.google.idea.blaze.base.ideinfo.RuleKey;
import com.google.idea.blaze.base.ideinfo.RuleMap;
import com.google.idea.blaze.base.model.primitives.Label;
import java.util.List;
import java.util.Set;

/** Computes transitive resources. */
public class TransitiveResourceMap
    extends RuleIdeInfoTransitiveAggregator<TransitiveResourceMap.TransitiveResourceInfo> {
  /** The transitive info computed per-rule */
  public static class TransitiveResourceInfo {
    public static final TransitiveResourceInfo NO_RESOURCES = new TransitiveResourceInfo();
    public final Set<ArtifactLocation> transitiveResources = Sets.newHashSet();
    public final Set<RuleKey> transitiveResourceRules = Sets.newHashSet();
  }

  public TransitiveResourceMap(RuleMap ruleMap) {
    super(ruleMap);
  }

  @Override
  protected Iterable<Label> getDependencies(RuleIdeInfo ruleIdeInfo) {
    AndroidRuleIdeInfo androidRuleIdeInfo = ruleIdeInfo.androidRuleIdeInfo;
    if (androidRuleIdeInfo != null && androidRuleIdeInfo.legacyResources != null) {
      List<Label> result = Lists.newArrayList(super.getDependencies(ruleIdeInfo));
      result.add(androidRuleIdeInfo.legacyResources);
      return result;
    }
    return super.getDependencies(ruleIdeInfo);
  }

  public TransitiveResourceInfo get(RuleKey ruleKey) {
    return getOrDefault(ruleKey, TransitiveResourceInfo.NO_RESOURCES);
  }

  @Override
  protected TransitiveResourceInfo createForRule(RuleIdeInfo ruleIdeInfo) {
    TransitiveResourceInfo result = new TransitiveResourceInfo();
    AndroidRuleIdeInfo androidRuleIdeInfo = ruleIdeInfo.androidRuleIdeInfo;
    if (androidRuleIdeInfo == null) {
      return result;
    }
    if (androidRuleIdeInfo.legacyResources != null) {
      return result;
    }
    result.transitiveResources.addAll(androidRuleIdeInfo.resources);
    result.transitiveResourceRules.add(ruleIdeInfo.key);
    return result;
  }

  @Override
  protected TransitiveResourceInfo reduce(
      TransitiveResourceInfo value, TransitiveResourceInfo dependencyValue) {
    value.transitiveResources.addAll(dependencyValue.transitiveResources);
    value.transitiveResourceRules.addAll(dependencyValue.transitiveResourceRules);
    return value;
  }
}
