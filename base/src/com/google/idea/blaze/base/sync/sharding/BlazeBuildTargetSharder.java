/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.sync.sharding;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.model.primitives.Label;
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
import com.google.idea.blaze.base.sync.BlazeBuildParams;
import com.google.idea.blaze.base.sync.aspects.BuildResult;
import com.google.idea.blaze.base.sync.projectview.TargetExpressionList;
import com.google.idea.blaze.base.sync.sharding.WildcardTargetExpander.ExpandedTargetsResult;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.common.experiments.BoolExperiment;
import com.google.idea.common.experiments.IntExperiment;
import com.intellij.openapi.project.Project;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Utility methods for sharding blaze build invocations. */
public class BlazeBuildTargetSharder {

  /** Default number of individual targets per blaze build shard. Can be overridden by the user. */
  private static final IntExperiment targetShardSize =
      new IntExperiment("blaze.target.shard.size", 1000);

  /** Default # targets to keep the arg length below ARG_MAX. */
  private static final IntExperiment argLengthShardSize =
      new IntExperiment("arg.length.shard.size", 1000);

  /** If enabled, we'll automatically shard when we think it's appropriate. */
  private static final BoolExperiment shardAutomatically =
      new BoolExperiment("blaze.shard.automatically.2", true);

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

  /** Returns true if sharding is requested via the project view file. */
  static boolean shardingRequested(Project project) {
    ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
    return projectViewSet != null && shardingRequested(projectViewSet);
  }

  private static boolean shardingRequested(ProjectViewSet projectViewSet) {
    return projectViewSet.getScalarValue(ShardBlazeBuildsSection.KEY).orElse(false);
  }

  /** Number of individual targets per blaze build shard. */
  private static int getTargetShardSize(ProjectViewSet projectViewSet) {
    return projectViewSet
        .getScalarValue(TargetShardSizeSection.KEY)
        .orElse(targetShardSize.getValue());
  }

  private enum ShardingApproach {
    NONE,
    EXPAND_AND_SHARD, // first expand wildcard targets, then split into batches
    SHARD_WITHOUT_EXPANDING, // split unexpanded wildcard targets into batches
  }

  private static ShardingApproach getShardingApproach(
      BlazeBuildParams buildParams, ProjectViewSet viewSet) {
    if (shardingRequested(viewSet)) {
      return ShardingApproach.EXPAND_AND_SHARD;
    }
    if (!shardAutomatically.getValue()) {
      return ShardingApproach.NONE;
    }
    // otherwise, only expand targets before sharding (a 'complete' batching of the build) if we're
    // syncing remotely
    return buildParams.blazeBinaryType().isRemote
        ? ShardingApproach.EXPAND_AND_SHARD
        : ShardingApproach.SHARD_WITHOUT_EXPANDING;
  }

  /** Expand wildcard target patterns and partition the resulting target list. */
  public static ShardedTargetsResult expandAndShardTargets(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      BlazeBuildParams buildParams,
      ProjectViewSet viewSet,
      WorkspacePathResolver pathResolver,
      List<TargetExpression> targets) {
    ShardingApproach approach = getShardingApproach(buildParams, viewSet);
    switch (approach) {
      case NONE:
        return new ShardedTargetsResult(
            new ShardedTargetList(ImmutableList.of(ImmutableList.copyOf(targets))),
            BuildResult.SUCCESS);
      case SHARD_WITHOUT_EXPANDING:
        // shard only to keep the arg length below ARG_MAX
        return new ShardedTargetsResult(
            new ShardedTargetList(
                shardTargetsRetainingOrdering(targets, argLengthShardSize.getValue())),
            BuildResult.SUCCESS);
      case EXPAND_AND_SHARD:
        ExpandedTargetsResult expandedTargets =
            expandWildcardTargets(
                project, context, workspaceRoot, buildParams, viewSet, pathResolver, targets);
        if (expandedTargets.buildResult.status == BuildResult.Status.FATAL_ERROR) {
          return new ShardedTargetsResult(
              new ShardedTargetList(ImmutableList.of()), expandedTargets.buildResult);
        }

        return new ShardedTargetsResult(
            shardTargets(project, expandedTargets.singleTargets, getTargetShardSize(viewSet)),
            expandedTargets.buildResult);
    }
    throw new IllegalStateException("Unhandled sharding approach: " + approach);
  }

  /** Expand wildcard target patterns into individual blaze targets. */
  private static ExpandedTargetsResult expandWildcardTargets(
      Project project,
      BlazeContext parentContext,
      WorkspaceRoot workspaceRoot,
      BlazeBuildParams buildParams,
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
              project, context, workspaceRoot, buildParams, projectViewSet, pathResolver, targets);
        });
  }

  private static ExpandedTargetsResult doExpandWildcardTargets(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      BlazeBuildParams buildParams,
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
    ExpandedTargetsResult result =
        WildcardTargetExpander.expandToSingleTargets(
            project, context, workspaceRoot, buildParams, projectViewSet, fullList);

    // finally add back any explicitly-specified, unexcluded single targets which may have been
    // removed by the query (for example, because they have the 'manual' tag)
    TargetExpressionList helper = TargetExpressionList.create(targets);
    List<TargetExpression> singleTargets =
        targets.stream()
            .filter(t -> !t.isExcluded())
            .filter(t -> !isWildcardPattern(t))
            .filter(t -> t instanceof Label)
            .filter(t -> helper.includesTarget((Label) t))
            .collect(toImmutableList());
    return ExpandedTargetsResult.merge(
        result, new ExpandedTargetsResult(singleTargets, result.buildResult));
  }

  @SuppressWarnings("unchecked")
  @VisibleForTesting
  static ShardedTargetList shardTargets(
      Project project, List<TargetExpression> targets, int shardSize) {
    ImmutableList<ImmutableList<Label>> batches =
        BuildBatchingService.batchTargets(project, canonicalizeTargets(targets), shardSize);
    return new ShardedTargetList((ImmutableList) batches);
  }

  /**
   * Given an ordered list of individual blaze targets (with no wildcard expressions), removes
   * duplicates and excluded targets, returning an unordered set.
   */
  private static ImmutableSet<Label> canonicalizeTargets(List<TargetExpression> targets) {
    Set<String> set = new HashSet<>();
    for (TargetExpression target : targets) {
      if (target.isExcluded()) {
        set.remove(target.toString().substring(1));
      } else {
        set.add(target.toString());
      }
    }
    return set.stream().map(Label::create).collect(toImmutableSet());
  }

  /**
   * Partition targets list. Because order is important with respect to excluded targets, original
   * relative ordering is retained, and each shard has all subsequent excluded targets appended to
   * it.
   */
  static ImmutableList<ImmutableList<TargetExpression>> shardTargetsRetainingOrdering(
      List<TargetExpression> targets, int shardSize) {
    if (targets.size() <= shardSize) {
      return ImmutableList.of(ImmutableList.copyOf(targets));
    }
    List<ImmutableList<TargetExpression>> output = new ArrayList<>();
    for (int index = 0; index < targets.size(); index += shardSize) {
      int endIndex = Math.min(targets.size(), index + shardSize);
      List<TargetExpression> shard = new ArrayList<>(targets.subList(index, endIndex));
      if (shard.stream().filter(TargetExpression::isExcluded).count() == shard.size()) {
        continue;
      }
      List<TargetExpression> remainingExcludes =
          targets.subList(endIndex, targets.size()).stream()
              .filter(TargetExpression::isExcluded)
              .collect(Collectors.toList());
      shard.addAll(remainingExcludes);
      output.add(ImmutableList.copyOf(shard));
    }
    return ImmutableList.copyOf(output);
  }

  /** Returns the wildcard target patterns, ignoring exclude patterns (those starting with '-') */
  private static List<WildcardTargetPattern> getWildcardPatterns(List<TargetExpression> targets) {
    return targets.stream()
        .filter(t -> !t.isExcluded())
        .map(WildcardTargetPattern::fromExpression)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  private static boolean isWildcardPattern(TargetExpression expr) {
    return WildcardTargetPattern.fromExpression(expr) != null;
  }

  private BlazeBuildTargetSharder() {}
}
