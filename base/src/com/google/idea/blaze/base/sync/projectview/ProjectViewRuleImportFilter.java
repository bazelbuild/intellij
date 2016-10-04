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
package com.google.idea.blaze.base.sync.projectview;

import com.google.common.collect.Sets;
import com.google.idea.blaze.base.ideinfo.RuleIdeInfo;
import com.google.idea.blaze.base.ideinfo.Tags;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.sections.ExcludeTargetSection;
import com.google.idea.blaze.base.projectview.section.sections.ImportTargetOutputSection;
import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.openapi.project.Project;
import java.util.Set;

/** Filters rules into source/library depending on the project view. */
public class ProjectViewRuleImportFilter {
  private final ImportRoots importRoots;
  private final Set<Label> importTargetOutputs;
  private final Set<Label> excludedTargets;

  public ProjectViewRuleImportFilter(
      Project project, WorkspaceRoot workspaceRoot, ProjectViewSet projectViewSet) {
    this.importRoots =
        ImportRoots.builder(workspaceRoot, Blaze.getBuildSystem(project))
            .add(projectViewSet)
            .build();
    this.importTargetOutputs =
        Sets.newHashSet(projectViewSet.listItems(ImportTargetOutputSection.KEY));
    this.excludedTargets = Sets.newHashSet(projectViewSet.listItems(ExcludeTargetSection.KEY));
  }

  public boolean isSourceRule(RuleIdeInfo rule) {
    return importRoots.importAsSource(rule.label) && !importTargetOutput(rule);
  }

  private boolean importTargetOutput(RuleIdeInfo rule) {
    return rule.tags.contains(Tags.RULE_TAG_IMPORT_TARGET_OUTPUT)
        || rule.tags.contains(Tags.RULE_TAG_IMPORT_AS_LIBRARY_LEGACY)
        || importTargetOutputs.contains(rule.label);
  }

  public boolean excludeTarget(RuleIdeInfo rule) {
    return excludedTargets.contains(rule.label)
        || rule.tags.contains(Tags.RULE_TAG_PROVIDED_BY_SDK)
        || rule.tags.contains(Tags.RULE_TAG_EXCLUDE_TARGET);
  }
}
