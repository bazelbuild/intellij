/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.sync.sharding;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.sections.ShardBlazeBuildsSection;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.aspects.BuildResult;
import com.google.idea.blaze.base.sync.sharding.WildcardTargetExpander.ExpandedTargetsResult;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.project.Project;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Utility methods for sharding blaze build invocations. */
public class BlazeBuildTargetSharder {

  private static final BoolExperiment allowSharding =
      new BoolExperiment("blaze.build.sharding.allowed", true);

  // number of packages per blaze query shard
  static final int PACKAGE_SHARD_SIZE = 500;

  // number of individual targets per blaze build shard
  private static final int TARGET_SHARD_SIZE = 1000;

  /** Result of expanding then sharding wildcard target patterns */
  public static class ShardedTargetsResult {
    public final ShardedTargetList shardedTargets;
    public final BuildResult buildResult;

    private ShardedTargetsResult(ShardedTargetList shardedTargets, BuildResult buildResult) {
      this.shardedTargets = shardedTargets;
      this.buildResult = buildResult;
    }
  }

  /** Returns true if sharding can be enabled for this project, and is not already enabled */
  static boolean canEnableSharding(Project project) {
    if (!allowSharding.getValue()) {
      return false;
    }
    ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
    return projectViewSet != null && !shardingEnabled(projectViewSet);
  }

  private static boolean shardingEnabled(ProjectViewSet projectViewSet) {
    if (!allowSharding.getValue()) {
      return false;
    }
    return projectViewSet.getScalarValue(ShardBlazeBuildsSection.KEY, false);
  }

  /** Expand wildcard target patterns and partition the resulting target list. */
  public static ShardedTargetsResult expandAndShardTargets(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      WorkspacePathResolver pathResolver,
      List<TargetExpression> targets) {
    if (!shardingEnabled(projectViewSet)) {
      return new ShardedTargetsResult(
          new ShardedTargetList(ImmutableList.of(targets)), BuildResult.SUCCESS);
    }

    List<WildcardTargetPattern> wildcardIncludes = getWildcardPatterns(targets);
    if (wildcardIncludes.isEmpty()) {
      return new ShardedTargetsResult(
          new ShardedTargetList(ImmutableList.of(targets)), BuildResult.SUCCESS);
    }
    ExpandedTargetsResult expandedTargets =
        expandWildcardTargets(
            project, context, workspaceRoot, projectViewSet, pathResolver, targets);
    if (expandedTargets.buildResult.status == BuildResult.Status.FATAL_ERROR) {
      return new ShardedTargetsResult(
          new ShardedTargetList(ImmutableList.of()), expandedTargets.buildResult);
    }
    return new ShardedTargetsResult(
        shardTargets(expandedTargets.singleTargets, TARGET_SHARD_SIZE),
        expandedTargets.buildResult);
  }

  /** Expand wildcard target patterns into individual blaze targets. */
  private static ExpandedTargetsResult expandWildcardTargets(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      WorkspacePathResolver pathResolver,
      List<TargetExpression> targets) {
    if (!shardingEnabled(projectViewSet)) {
      return new ExpandedTargetsResult(targets, BuildResult.SUCCESS);
    }
    List<WildcardTargetPattern> includes = getWildcardPatterns(targets);
    if (includes.isEmpty()) {
      return new ExpandedTargetsResult(targets, BuildResult.SUCCESS);
    }
    Map<TargetExpression, List<TargetExpression>> expandedTargets =
        WildcardTargetExpander.expandToNonRecursiveWildcardTargets(
            project, context, pathResolver, includes);
    if (expandedTargets == null) {
      return new ExpandedTargetsResult(ImmutableList.of(), BuildResult.FATAL_ERROR);
    }

    // replace original recursive targets with expanded list, retaining relative ordering
    List<TargetExpression> fullList = new ArrayList<>();
    for (TargetExpression target : targets) {
      List<TargetExpression> expanded = expandedTargets.get(target);
      if (expanded == null) {
        fullList.add(target);
      } else {
        fullList.addAll(expanded);
      }
    }
    return WildcardTargetExpander.expandToSingleTargets(
        project, context, workspaceRoot, projectViewSet, fullList);
  }

  /**
   * Partition targets list. Because order is important with respect to excluded targets, each shard
   * has all subsequent excluded targets appended to it.
   */
  static ShardedTargetList shardTargets(List<TargetExpression> targets, int shardSize) {
    if (targets.size() <= shardSize) {
      return new ShardedTargetList(ImmutableList.of(targets));
    }
    List<List<TargetExpression>> output = new ArrayList<>();
    for (int index = 0; index < targets.size(); index += shardSize) {
      int endIndex = Math.min(targets.size(), index + shardSize);
      List<TargetExpression> shard = new ArrayList<>(targets.subList(index, endIndex));
      List<TargetExpression> remainingExcludes =
          targets
              .subList(endIndex, targets.size())
              .stream()
              .filter(TargetExpression::isExcluded)
              .collect(Collectors.toList());
      shard.addAll(remainingExcludes);
      output.add(shard);
    }
    return new ShardedTargetList(output);
  }

  /** Returns the wildcard target patterns, ignoring exclude patterns (those starting with '-') */
  private static List<WildcardTargetPattern> getWildcardPatterns(List<TargetExpression> targets) {
    return targets
        .stream()
        .filter(t -> !t.isExcluded())
        .map(WildcardTargetPattern::fromExpression)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  private BlazeBuildTargetSharder() {}
}
