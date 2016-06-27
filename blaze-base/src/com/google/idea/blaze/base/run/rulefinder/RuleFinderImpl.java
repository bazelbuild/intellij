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
import com.google.idea.blaze.base.ideinfo.RuleIdeInfo;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Implementation of RuleFinder.
 */
class RuleFinderImpl extends RuleFinder {
  @Override
  public List<RuleIdeInfo> findRules(@NotNull Project project, @NotNull Predicate<RuleIdeInfo> predicate) {
    BlazeProjectData projectData = BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (projectData == null) {
      return ImmutableList.of();
    }

    ImmutableList.Builder<RuleIdeInfo> resultList = ImmutableList.builder();
    for (RuleIdeInfo rule : projectData.ruleMap.values()) {
      if (predicate.apply(rule)) {
        resultList.add(rule);
      }
    }
    return resultList.build();
  }
}
