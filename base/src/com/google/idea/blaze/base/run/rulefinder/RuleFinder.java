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
package com.google.idea.blaze.base.run.rulefinder;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.idea.blaze.base.ideinfo.RuleIdeInfo;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nullable;

/** Searches BlazeProjectData for matching rules. */
public abstract class RuleFinder {
  public static RuleFinder getInstance() {
    return ServiceManager.getService(RuleFinder.class);
  }

  @Nullable
  public RuleIdeInfo ruleForTarget(Project project, final Label target) {
    return findRule(project, rule -> rule.label.equals(target));
  }

  public ImmutableList<RuleIdeInfo> rulesOfKinds(Project project, final Kind... kinds) {
    return rulesOfKinds(project, Arrays.asList(kinds));
  }

  public ImmutableList<RuleIdeInfo> rulesOfKinds(Project project, final List<Kind> kinds) {
    return ImmutableList.copyOf(findRules(project, rule -> rule.kindIsOneOf(kinds)));
  }

  @Nullable
  public RuleIdeInfo firstRuleOfKinds(Project project, Kind... kinds) {
    return Iterables.getFirst(rulesOfKinds(project, kinds), null);
  }

  @Nullable
  public RuleIdeInfo firstRuleOfKinds(Project project, List<Kind> kinds) {
    return Iterables.getFirst(rulesOfKinds(project, kinds), null);
  }

  @Nullable
  private RuleIdeInfo findRule(Project project, Predicate<RuleIdeInfo> predicate) {
    List<RuleIdeInfo> results = findRules(project, predicate);
    assert results.size() <= 1;
    return Iterables.getFirst(results, null);
  }

  @Nullable
  public RuleIdeInfo findFirstRule(Project project, Predicate<RuleIdeInfo> predicate) {
    return Iterables.getFirst(findRules(project, predicate), null);
  }

  public abstract List<RuleIdeInfo> findRules(Project project, Predicate<RuleIdeInfo> predicate);
}
