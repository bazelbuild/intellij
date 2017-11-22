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
import com.google.idea.blaze.base.model.primitives.WildcardTargetPattern;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.sections.ShardBlazeBuildsSection;
import com.google.idea.blaze.base.projectview.section.sections.TargetShardSizeSection;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.output.StatusOutput;
import com.google.idea.blaze.base.scope.scopes.TimingScope;
import com.google.idea.blaze.base.scope.scopes.TimingScope.EventType;
import com.google.idea.blaze.base.sync.aspects.BuildResult;
import com.google.idea.blaze.base.sync.sharding.WildcardTargetExpander.ExpandedTargetsResult;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.common.experiments.IntExperiment;
import com.intellij.openapi.project.Project;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Utility methods for sharding blaze build invocations. */
public class BlazeBuildTargetSharder {

  /** Default number of individual targets per blaze build shard. Can be overridden by the user. */
  private static final IntExperiment targetShardSize =
      new IntExperiment("blaze.target.shard.size", 1000);

  // number of packages per blaze query shard
  static final int PACKAGE_SHARD_SIZE = 500;

  /** Result of expanding then sharding wildcard target patterns */
  public static class ShardedTargetsResult {
    public final ShardedTargetList shardedTargets;
    public final BuildResult buildResult;

    private ShardedTargetsResult(ShardedTargetList shardedTargets, BuildResult buildResult) {
      this.shardedTargets = shardedTargets;
      this.buildResult = buildResult;
    }
  }

  /** Returns true if sharding is already enabled for this project. */
  static boolean shardingEnabled(Project project) {
    ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
    return projectViewSet != null && shardingEnabled(projectViewSet);
  }

  private static boolean shardingEnabled(ProjectViewSet projectViewSet) {
    return projectViewSet.getScalarValue(ShardBlazeBuildsSection.KEY).orElse(false);
  }

  /** Number of individual targets per blaze build shard */
  static int getTargetShardSize(ProjectViewSet projectViewSet) {
    return projectViewSet
        .getScalarValue(TargetShardSizeSection.KEY)
        .orElse(targetShardSize.getValue());
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
        shardTargets(expandedTargets.singleTargets, getTargetShardSize(projectViewSet)),
        expandedTargets.buildResult);
  }

  /** Expand wildcard target patterns into individual blaze targets. */
  private static ExpandedTargetsResult expandWildcardTargets(
      Project project,
      BlazeContext parentContext,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      WorkspacePathResolver pathResolver,
      List<TargetExpression> targets) {
    return Scope.push(
        parentContext,
        context -> {
          context.push(new TimingScope("ShardSyncTargets", EventType.Other));
          context.output(new StatusOutput("Sharding: expanding wildcard target patterns..."));
          context.setPropagatesErrors(false);
          return doExpandWildcardTargets(
              project, context, workspaceRoot, projectViewSet, pathResolver, targets);
        });
  }

  private static ExpandedTargetsResult doExpandWildcardTargets(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      WorkspacePathResolver pathResolver,
      List<TargetExpression> targets) {
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
      if (shard.stream().filter(TargetExpression::isExcluded).count() == shard.size()) {
        continue;
      }
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
