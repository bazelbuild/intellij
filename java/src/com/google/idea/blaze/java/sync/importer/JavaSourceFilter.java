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
package com.google.idea.blaze.java.sync.importer;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.RuleIdeInfo;
import com.google.idea.blaze.base.ideinfo.RuleKey;
import com.google.idea.blaze.base.ideinfo.RuleMap;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.sync.projectview.ProjectViewRuleImportFilter;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.project.Project;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Segments java rules into source/libraries */
public class JavaSourceFilter {
  private static final BoolExperiment NO_EMPTY_SOURCE_RULES =
      new BoolExperiment("no.empty.source.rules", true);

  final List<RuleIdeInfo> sourceRules;
  final List<RuleIdeInfo> libraryRules;
  final List<RuleIdeInfo> protoLibraries;
  final Map<RuleKey, Collection<ArtifactLocation>> ruleToJavaSources;

  public JavaSourceFilter(
      Project project,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      RuleMap ruleMap) {
    ProjectViewRuleImportFilter importFilter =
        new ProjectViewRuleImportFilter(project, workspaceRoot, projectViewSet);
    List<RuleIdeInfo> includedRules =
        ruleMap
            .rules()
            .stream()
            .filter(rule -> !importFilter.excludeTarget(rule))
            .collect(Collectors.toList());

    List<RuleIdeInfo> javaRules =
        includedRules
            .stream()
            .filter(rule -> rule.javaRuleIdeInfo != null)
            .collect(Collectors.toList());

    ruleToJavaSources = Maps.newHashMap();
    for (RuleIdeInfo rule : javaRules) {
      List<ArtifactLocation> javaSources =
          rule.sources
              .stream()
              .filter(source -> source.getRelativePath().endsWith(".java"))
              .collect(Collectors.toList());
      ruleToJavaSources.put(rule.key, javaSources);
    }

    boolean noEmptySourceRules = NO_EMPTY_SOURCE_RULES.getValue();
    sourceRules = Lists.newArrayList();
    libraryRules = Lists.newArrayList();
    for (RuleIdeInfo rule : javaRules) {
      boolean importAsSource =
          importFilter.isSourceRule(rule)
              && canImportAsSource(rule)
              && (noEmptySourceRules
                  ? anyNonGeneratedSources(ruleToJavaSources.get(rule.key))
                  : !allSourcesGenerated(ruleToJavaSources.get(rule.key)));

      if (importAsSource) {
        sourceRules.add(rule);
      } else {
        libraryRules.add(rule);
      }
    }

    protoLibraries =
        includedRules
            .stream()
            .filter(rule -> rule.kind == Kind.PROTO_LIBRARY)
            .collect(Collectors.toList());
  }

  public Iterable<RuleIdeInfo> getSourceRules() {
    return sourceRules;
  }

  private boolean canImportAsSource(RuleIdeInfo rule) {
    return !rule.kindIsOneOf(Kind.JAVA_WRAP_CC, Kind.JAVA_IMPORT);
  }

  private boolean allSourcesGenerated(Collection<ArtifactLocation> sources) {
    return !sources.isEmpty() && sources.stream().allMatch(ArtifactLocation::isGenerated);
  }

  private boolean anyNonGeneratedSources(Collection<ArtifactLocation> sources) {
    return sources.stream().anyMatch(ArtifactLocation::isSource);
  }
}
