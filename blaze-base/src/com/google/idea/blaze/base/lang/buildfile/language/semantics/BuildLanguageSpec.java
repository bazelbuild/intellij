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
package com.google.idea.blaze.base.lang.buildfile.language.semantics;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.repackaged.devtools.build.lib.query2.proto.proto2api.Build;

import javax.annotation.Nullable;
import java.io.Serializable;

/**
 * Specification of the BUILD language, as provided by "blaze info build-language".<p>
 *
 * This constitutes a set of rules, along with their supported attributes, and other
 * useful information. We query this once per blaze workspace (it won't change unless
 * the blaze binary is also changed).<p>
 *
 * This rule list is not exhaustive; it's intended to give information about known
 * rules, not enumerate all possibilities.
 */
public class BuildLanguageSpec implements Serializable {

  public static BuildLanguageSpec fromProto(Build.BuildLanguage proto) {
    ImmutableMap.Builder<String, RuleDefinition> builder = ImmutableMap.builder();
    for (Build.RuleDefinition rule : proto.getRuleList()) {
      builder.put(rule.getName(), RuleDefinition.fromProto(rule));
    }
    return new BuildLanguageSpec(builder.build());
  }

  public final ImmutableMap<String, RuleDefinition> rules;

  @VisibleForTesting
  public BuildLanguageSpec(ImmutableMap<String, RuleDefinition> rules) {
    this.rules = rules;
  }

  public ImmutableSet<String> getKnownRuleNames() {
    return rules.keySet();
  }

  public boolean hasRule(@Nullable String ruleName) {
    return getRule(ruleName) != null;
  }

  @Nullable
  public RuleDefinition getRule(@Nullable String ruleName) {
    return ruleName != null ? rules.get(ruleName) : null;
  }
}
