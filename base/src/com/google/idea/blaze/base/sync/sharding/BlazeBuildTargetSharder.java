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
import com.google.common.collect.Lists;
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
import com.google.idea.blaze.base.sync.aspects.BuildResult;
import com.google.idea.blaze.base.sync.projectview.ProjectTargetsHelper;
import com.google.idea.blaze.base.sync.sharding.WildcardTargetExpander.ExpandedTargetsResult;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.common.experiments.BoolExperiment;
import com.google.idea.common.experiments.IntExperiment;
import com.intellij.openapi.project.Project;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
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

  /** If enabled, we'll automatically shard when we think it's appropriate. */
  private static final BoolExperiment shardAutomatically =
      new BoolExperiment("blaze.shard.automatically", true);

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

  /** Expand wildcard target patterns and partition the resulting target list. */
  @SuppressWarnings("unchecked")
  public static ShardedTargetsResult expandAndShardTargets(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      WorkspacePathResolver pathResolver,
      List<TargetExpression> targets) {
    if (!shardingRequested(projectViewSet)) {
      if (!shardAutomatically.getValue()) {
        return new ShardedTargetsResult(
            new ShardedTargetList(ImmutableList.of(ImmutableList.copyOf(targets))),
            BuildResult.SUCCESS);
      }
      // for now, automatically shard only to keep the arg length below ARG_MAX
      return new ShardedTargetsResult(
          new ShardedTargetList(
              (ImmutableList)
                  LexicographicTargetSharder.shardTargets(targets, targetShardSize.getValue())),
          BuildResult.SUCCESS);
    }

    ExpandedTargetsResult expandedTargets =
        expandWildcardTargets(
            project, context, workspaceRoot, projectViewSet, pathResolver, targets);
    if (expandedTargets.buildResult.status == BuildResult.Status.FATAL_ERROR) {
      return new ShardedTargetsResult(
          new ShardedTargetList(ImmutableList.of()), expandedTargets.buildResult);
    }

    return new ShardedTargetsResult(
        shardTargets(project, expandedTargets.singleTargets, getTargetShardSize(projectViewSet)),
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
    ExpandedTargetsResult result =
        WildcardTargetExpander.expandToSingleTargets(
            project, context, workspaceRoot, projectViewSet, fullList);

    // finally add back any explicitly-specified, unexcluded single targets which may have been
    // removed by the query (for example, because they have the 'manual' tag)
    ProjectTargetsHelper helper = ProjectTargetsHelper.create(targets);
    List<TargetExpression> singleTargets =
        targets.stream()
            .filter(t -> !t.isExcluded())
            .filter(t -> !isWildcardPattern(t))
            .filter(t -> t instanceof Label)
            .filter(t -> helper.targetInProject((Label) t))
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
   * A simple target batcher splitting based on the target strings. This will tend to split by
   * package, so is better than random batching.
   */
  static class LexicographicTargetSharder implements BuildBatchingService {
    @Override
    @SuppressWarnings("unchecked")
    public ImmutableList<ImmutableList<Label>> calculateTargetBatches(
        Project project, Set<Label> targets, int suggestedShardSize) {
      return (ImmutableList) shardTargets(targets, suggestedShardSize);
    }

    private static ImmutableList<ImmutableList<? extends TargetExpression>> shardTargets(
        Collection<? extends TargetExpression> targets, int shardSize) {
      List<? extends TargetExpression> sorted =
          ImmutableList.sortedCopyOf(Comparator.comparing(TargetExpression::toString), targets);
      return Lists.partition(sorted, shardSize).stream()
          .map(ImmutableList::copyOf)
          .collect(toImmutableList());
    }
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
