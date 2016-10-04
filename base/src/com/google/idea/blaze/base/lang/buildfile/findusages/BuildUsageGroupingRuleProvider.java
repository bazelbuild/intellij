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
package com.google.idea.blaze.base.lang.buildfile.findusages;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.usages.UsageView;
import com.intellij.usages.rules.UsageGroupingRule;
import com.intellij.usages.rules.UsageGroupingRuleProvider;

/**
 * This is a gross hack. We want to always include file paths for BUILD files in the 'find usages'
 * dialog.<br>
 * This achieves that by inserting an additional UsageGroupingRule for each file usage, regardless
 * of whether we're grouping by file structure
 */
public class BuildUsageGroupingRuleProvider implements UsageGroupingRuleProvider {
  @Override
  public UsageGroupingRule[] getActiveRules(Project project) {
    return new UsageGroupingRule[] {BuildFileGroupingRuleProvider.getGroupingRule(project)};
  }

  @Override
  public AnAction[] createGroupingActions(UsageView view) {
    return new AnAction[0];
  }
}
