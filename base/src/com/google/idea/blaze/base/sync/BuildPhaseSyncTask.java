/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.sync;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.idea.blaze.base.command.BlazeInvocationContext.ContextType;
import com.google.idea.blaze.base.dependencies.BlazeQuerySourceToTargetProvider;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.logging.utils.BuildPhaseSyncStats;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.output.PrintOutput;
import com.google.idea.blaze.base.scope.output.StatusOutput;
import com.google.idea.blaze.base.scope.scopes.TimingScope;
import com.google.idea.blaze.base.scope.scopes.TimingScope.EventType;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.settings.BuildSystem;
import com.google.idea.blaze.base.sync.SyncProjectTargetsHelper.ProjectTargets;
import com.google.idea.blaze.base.sync.SyncScope.SyncCanceledException;
import com.google.idea.blaze.base.sync.SyncScope.SyncFailedException;
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs;
import com.google.idea.blaze.base.sync.aspects.BlazeIdeInterface;
import com.google.idea.blaze.base.sync.aspects.BuildResult;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.base.sync.sharding.BlazeBuildTargetSharder;
import com.google.idea.blaze.base.sync.sharding.BlazeBuildTargetSharder.ShardedTargetsResult;
import com.google.idea.blaze.base.sync.sharding.ShardedTargetList;
import com.google.idea.blaze.base.sync.sharding.SuggestBuildShardingNotification;
import com.google.idea.blaze.base.sync.workspace.WorkingSet;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/** Runs the 'blaze build' phase of sync. */
final class BuildPhaseSyncTask {

  /**
   * Runs the build phase of sync, and returns a possibly partially filled in {@link
   * BlazeSyncBuildResult}.
   */
  static BlazeSyncBuildResult runBuildPhase(
      Project project,
      BlazeSyncParams syncParams,
      SyncProjectState projectState,
      int buildId,
      BlazeContext context) {
    BuildPhaseSyncTask task = new BuildPhaseSyncTask(project, syncParams, projectState, buildId);
    return task.run(context);
  }

  private final Project project;
  private final BlazeImportSettings importSettings;
  private final WorkspaceRoot workspaceRoot;
  private final BlazeSyncParams syncParams;
  private final SyncProjectState projectState;
  private final int buildId;
  private final BlazeSyncBuildResult.Builder resultBuilder;
  private final BuildPhaseSyncStats.Builder buildStats;

  private BuildPhaseSyncTask(
      Project project, BlazeSyncParams syncParams, SyncProjectState projectState, int buildId) {
    this.project = project;
    this.importSettings = BlazeImportSettingsManager.getInstance(project).getImportSettings();
    this.workspaceRoot = WorkspaceRoot.fromImportSettings(importSettings);
    this.syncParams = syncParams;
    this.projectState = projectState;
    this.buildId = buildId;
    this.resultBuilder = BlazeSyncBuildResult.builder();
    this.buildStats = BuildPhaseSyncStats.builder();
  }

  private BlazeSyncBuildResult run(BlazeContext parentContext) {
    // run under a child context to capture all timing information before finalizing the stats
    SyncScope.push(
        parentContext,
        context -> {
          TimingScope timingScope = new TimingScope("Build phase", EventType.Other);
          timingScope.addScopeListener(
              (events, totalTime) -> buildStats.setTimedEvents(events).setTotalTime(totalTime));
          context.push(timingScope);
          doRun(context);
        });
    return resultBuilder.setBuildPhaseStats(ImmutableList.of(buildStats.build())).build();
  }

  private void notifyBuildStarted(
      BlazeContext context, boolean fullProjectSync, ImmutableList<TargetExpression> targets) {
    SyncListener.EP_NAME
        .extensions()
        .forEach(l -> l.buildStarted(project, context, fullProjectSync, buildId, targets));
  }

  private void doRun(BlazeContext context) throws SyncFailedException, SyncCanceledException {
    List<TargetExpression> targets = Lists.newArrayList();
    ProjectViewSet viewSet = projectState.getProjectViewSet();
    if (syncParams.addWorkingSet() && projectState.getWorkingSet() != null) {
      Collection<TargetExpression> workingSetTargets = getWorkingSetTargets(context);
      if (!workingSetTargets.isEmpty()) {
        targets.addAll(workingSetTargets);
        printTargets(context, "working set", workingSetTargets);
      }
    }
    if (syncParams.addProjectViewTargets()) {
      ProjectTargets projectTargets =
          SyncProjectTargetsHelper.getProjectTargets(
              project,
              context,
              viewSet,
              projectState.getWorkspacePathResolver(),
              projectState.getLanguageSettings());
      if (!projectTargets.derivedTargets.isEmpty()) {
        buildStats.setTargetsDerivedFromDirectories(true);
        printTargets(context, "project view directories", projectTargets.derivedTargets);
      }
      if (!projectTargets.explicitTargets.isEmpty()) {
        printTargets(context, "project view targets", projectTargets.explicitTargets);
      }
      targets.addAll(projectTargets.getTargetsToSync());
    }
    if (!syncParams.sourceFilesToSync().isEmpty()) {
      Collection<TargetExpression> targetsFromSources =
          findTargetsBuildingSourceFiles(syncParams.sourceFilesToSync(), context);
      if (!targetsFromSources.isEmpty()) {
        targets.addAll(targetsFromSources);
        printTargets(
            context, syncParams.title() + " (targets derived from query)", targetsFromSources);
      }
    }
    if (!syncParams.targetExpressions().isEmpty()) {
      targets.addAll(syncParams.targetExpressions());
      printTargets(context, syncParams.title(), syncParams.targetExpressions());
    }
    buildStats.setTargets(targets);
    notifyBuildStarted(context, syncParams.addProjectViewTargets(), ImmutableList.copyOf(targets));

    ShardedTargetsResult shardedTargetsResult =
        BlazeBuildTargetSharder.expandAndShardTargets(
            project,
            context,
            workspaceRoot,
            syncParams.blazeBuildParams(),
            viewSet,
            projectState.getWorkspacePathResolver(),
            targets);
    if (shardedTargetsResult.buildResult.status == BuildResult.Status.FATAL_ERROR) {
      throw new SyncFailedException();
    }
    ShardedTargetList shardedTargets = shardedTargetsResult.shardedTargets;

    buildStats
        .setSyncSharded(shardedTargets.shardCount() > 1)
        .setShardCount(shardedTargets.shardCount())
        .setShardStats(shardedTargets.shardStats())
        .setParallelBuilds(syncParams.blazeBuildParams().parallelizeBuilds());

    BlazeBuildOutputs blazeBuildResult = getBlazeBuildResult(context, viewSet, shardedTargets);
    resultBuilder.setBuildResult(blazeBuildResult);
    buildStats.setBuildResult(blazeBuildResult.buildResult).setBuildIds(blazeBuildResult.buildIds);
    if (context.isCancelled()) {
      throw new SyncCanceledException();
    }
    context.output(
        PrintOutput.log("build invocation result: " + blazeBuildResult.buildResult.status));
    if (blazeBuildResult.buildResult.status == BuildResult.Status.FATAL_ERROR) {
      context.setHasError();
      if (blazeBuildResult.buildResult.outOfMemory()) {
        SuggestBuildShardingNotification.syncOutOfMemoryError(project, context);
      }
      throw new SyncFailedException();
    }
  }

  private void printTargets(
      BlazeContext context, String owner, Collection<? extends TargetExpression> targets) {
    StringBuilder sb = new StringBuilder("Sync targets from ");
    sb.append(owner).append(':').append('\n');

    targets.stream().limit(50).forEach(target -> sb.append("  ").append(target).append('\n'));
    if (targets.size() > 50) {
      sb.append(String.format("\nPlus %d more targets", targets.size() - 50));
    }
    context.output(PrintOutput.log(sb.toString()));
  }

  private ImportRoots getImportRoots() {
    return ImportRoots.builder(workspaceRoot, importSettings.getBuildSystem())
        .add(projectState.getProjectViewSet())
        .build();
  }

  private static final BoolExperiment queryWorkingSetTargets =
      new BoolExperiment("query.working.set.targets", true);

  private Collection<TargetExpression> getWorkingSetTargets(BlazeContext context)
      throws SyncCanceledException, SyncFailedException {
    WorkingSet workingSet = projectState.getWorkingSet();
    if (workingSet == null) {
      return ImmutableList.of();
    }
    ImmutableList<WorkspacePath> sources =
        ImmutableList.<WorkspacePath>builder()
            .addAll(workingSet.addedFiles)
            .addAll(workingSet.modifiedFiles)
            .build();

    if (queryWorkingSetTargets.getValue()) {
      return findTargetsBuildingSourceFiles(sources, context);
    }

    BuildTargetFinder buildTargetFinder =
        new BuildTargetFinder(project, workspaceRoot, getImportRoots());

    Set<TargetExpression> result = Sets.newHashSet();
    for (WorkspacePath workspacePath : sources) {
      File file = workspaceRoot.fileForPath(workspacePath);
      TargetExpression targetExpression = buildTargetFinder.findTargetForFile(file);
      if (targetExpression != null) {
        result.add(targetExpression);
      }
    }
    return result;
  }

  /**
   * Finds the list of targets to sync for the given source files. Ignores directories, and sources
   * not covered by the .bazelproject directories.
   */
  private ImmutableList<TargetExpression> findTargetsBuildingSourceFiles(
      Collection<WorkspacePath> sources, BlazeContext context)
      throws SyncCanceledException, SyncFailedException {
    ImportRoots importRoots = getImportRoots();
    ImmutableList.Builder<TargetExpression> targets = ImmutableList.builder();
    ImmutableList.Builder<WorkspacePath> pathsToQuery = ImmutableList.builder();
    for (WorkspacePath source : sources) {
      File file = projectState.getWorkspacePathResolver().resolveToFile(source);
      if (FileOperationProvider.getInstance().isDirectory(file)) {
        continue;
      }
      if (!importRoots.containsWorkspacePath(source)) {
        continue;
      }
      if (Blaze.getBuildSystemProvider(project).isBuildFile(file.getName())) {
        targets.add(TargetExpression.allFromPackageNonRecursive(source.getParent()));
        continue;
      }
      pathsToQuery.add(source);
    }
    List<TargetInfo> result =
        Scope.push(
            context,
            childContext -> {
              childContext.push(new TimingScope("QuerySourceTargets", EventType.BlazeInvocation));
              childContext.output(new StatusOutput("Querying targets building source files..."));
              // We don't want blaze build errors to fail the whole sync
              childContext.setPropagatesErrors(false);
              return BlazeQuerySourceToTargetProvider.getTargetsBuildingSourceFiles(
                  project, pathsToQuery.build(), childContext, ContextType.Sync);
            });
    if (context.isCancelled()) {
      throw new SyncCanceledException();
    }
    if (result == null) {
      String fileBugSuggestion =
          Blaze.getBuildSystem(project) == BuildSystem.Bazel
              ? ""
              : " Please run 'Blaze > File a Bug'";
      IssueOutput.error(
              "Querying blaze targets building project source files failed." + fileBugSuggestion)
          .submit(context);
      throw new SyncFailedException();
    }
    targets.addAll(result.stream().map(t -> t.label).collect(toImmutableList()));
    return targets.build();
  }

  private BlazeBuildOutputs getBlazeBuildResult(
      BlazeContext parentContext, ProjectViewSet projectViewSet, ShardedTargetList shardedTargets) {

    return Scope.push(
        parentContext,
        context -> {
          context.push(new TimingScope("BlazeBuild", EventType.BlazeInvocation));
          context.output(
              new StatusOutput(
                  "Building " + Blaze.getBuildSystem(project).getName() + " targets..."));
          // We don't want blaze build errors to fail the whole sync
          context.setPropagatesErrors(false);

          BlazeIdeInterface blazeIdeInterface = BlazeIdeInterface.getInstance();
          return blazeIdeInterface.buildIdeArtifacts(
              project,
              context,
              workspaceRoot,
              projectState.getBlazeVersionData(),
              syncParams.blazeBuildParams(),
              projectViewSet,
              projectState.getBlazeInfo(),
              shardedTargets,
              projectState.getLanguageSettings());
        });
  }
}
