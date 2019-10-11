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


import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.logging.utils.BuildPhaseSyncStats;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.output.PrintOutput;
import com.google.idea.blaze.base.scope.output.StatusOutput;
import com.google.idea.blaze.base.scope.scopes.TimingScope;
import com.google.idea.blaze.base.scope.scopes.TimingScope.EventType;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.sync.SyncProjectTargetsHelper.ProjectTargets;
import com.google.idea.blaze.base.sync.SyncScope.SyncCanceledException;
import com.google.idea.blaze.base.sync.SyncScope.SyncFailedException;
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs;
import com.google.idea.blaze.base.sync.aspects.BlazeIdeInterface;
import com.google.idea.blaze.base.sync.aspects.BuildResult;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.sharding.BlazeBuildTargetSharder;
import com.google.idea.blaze.base.sync.sharding.BlazeBuildTargetSharder.ShardedTargetsResult;
import com.google.idea.blaze.base.sync.sharding.ShardedTargetList;
import com.google.idea.blaze.base.sync.sharding.SuggestBuildShardingNotification;
import com.google.idea.blaze.base.sync.workspace.WorkingSet;
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
      BlazeContext context) {
    BuildPhaseSyncTask task = new BuildPhaseSyncTask(project, syncParams, projectState);
    return task.run(context);
  }

  private final Project project;
  private final BlazeImportSettings importSettings;
  private final WorkspaceRoot workspaceRoot;
  private final BlazeSyncParams syncParams;
  private final SyncProjectState projectState;
  private final BlazeSyncBuildResult.Builder resultBuilder;
  private final BuildPhaseSyncStats.Builder buildStats;

  private BuildPhaseSyncTask(
      Project project, BlazeSyncParams syncParams, SyncProjectState projectState) {
    this.project = project;
    this.importSettings = BlazeImportSettingsManager.getInstance(project).getImportSettings();
    this.workspaceRoot = WorkspaceRoot.fromImportSettings(importSettings);
    this.syncParams = syncParams;
    this.projectState = projectState;
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

  private void doRun(BlazeContext context) throws SyncFailedException, SyncCanceledException {
    List<TargetExpression> targets = Lists.newArrayList();
    ProjectViewSet viewSet = projectState.getProjectViewSet();
    if (syncParams.addWorkingSet() && projectState.getWorkingSet() != null) {
      Collection<? extends TargetExpression> workingSetTargets =
          getWorkingSetTargets(viewSet, projectState.getWorkingSet());
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
    if (!syncParams.targetExpressions().isEmpty()) {
      targets.addAll(syncParams.targetExpressions());
      printTargets(context, syncParams.title(), syncParams.targetExpressions());
    }
    buildStats.setTargets(targets);

    ShardedTargetsResult shardedTargetsResult =
        BlazeBuildTargetSharder.expandAndShardTargets(
            project,
            context,
            workspaceRoot,
            viewSet,
            projectState.getWorkspacePathResolver(),
            targets);
    if (shardedTargetsResult.buildResult.status == BuildResult.Status.FATAL_ERROR) {
      throw new SyncFailedException();
    }
    ShardedTargetList shardedTargets = shardedTargetsResult.shardedTargets;

    buildStats.setSyncSharded(shardedTargets.shardCount() > 1);

    BlazeBuildOutputs blazeBuildResult =
        getBlazeBuildResult(
            project,
            context,
            viewSet,
            projectState.getBlazeInfo(),
            shardedTargets,
            projectState.getLanguageSettings());
    resultBuilder.setBuildResult(blazeBuildResult);
    buildStats.setBuildResult(blazeBuildResult.buildResult);
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

  private Collection<? extends TargetExpression> getWorkingSetTargets(
      ProjectViewSet projectViewSet, WorkingSet workingSet) {
    ImportRoots importRoots =
        ImportRoots.builder(workspaceRoot, importSettings.getBuildSystem())
            .add(projectViewSet)
            .build();
    BuildTargetFinder buildTargetFinder =
        new BuildTargetFinder(project, workspaceRoot, importRoots);

    Set<TargetExpression> result = Sets.newHashSet();
    for (WorkspacePath workspacePath :
        Iterables.concat(workingSet.addedFiles, workingSet.modifiedFiles)) {
      File file = workspaceRoot.fileForPath(workspacePath);
      TargetExpression targetExpression = buildTargetFinder.findTargetForFile(file);
      if (targetExpression != null) {
        result.add(targetExpression);
      }
    }
    return result;
  }

  private BlazeBuildOutputs getBlazeBuildResult(
      Project project,
      BlazeContext parentContext,
      ProjectViewSet projectViewSet,
      BlazeInfo blazeInfo,
      ShardedTargetList shardedTargets,
      WorkspaceLanguageSettings workspaceLanguageSettings) {

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
              projectViewSet,
              blazeInfo,
              shardedTargets,
              workspaceLanguageSettings);
        });
  }
}
