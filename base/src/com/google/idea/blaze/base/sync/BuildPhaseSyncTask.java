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
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.idea.blaze.base.async.FutureUtil;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.command.BlazeInvocationContext;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.command.info.BlazeInfoRunner;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.logging.utils.BuildPhaseSyncStats;
import com.google.idea.blaze.base.model.BlazeVersionData;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.plugin.BuildSystemVersionChecker;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.ProjectViewVerifier;
import com.google.idea.blaze.base.projectview.section.sections.TargetSection;
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
import com.google.idea.blaze.base.sync.SyncScope.SyncCanceledException;
import com.google.idea.blaze.base.sync.SyncScope.SyncFailedException;
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs;
import com.google.idea.blaze.base.sync.aspects.BlazeIdeInterface;
import com.google.idea.blaze.base.sync.aspects.BuildResult;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.base.sync.projectview.LanguageSupport;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.sharding.BlazeBuildTargetSharder;
import com.google.idea.blaze.base.sync.sharding.BlazeBuildTargetSharder.ShardedTargetsResult;
import com.google.idea.blaze.base.sync.sharding.ShardedTargetList;
import com.google.idea.blaze.base.sync.sharding.SuggestBuildShardingNotification;
import com.google.idea.blaze.base.sync.workspace.WorkingSet;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverImpl;
import com.google.idea.blaze.base.vcs.BlazeVcsHandler;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Runs the 'project update' phase of sync, after the blaze build phase has completed. */
final class BuildPhaseSyncTask {

  /**
   * Runs the build phase of sync, and returns a possibly partially filled in {@link
   * BlazeSyncBuildResult}.
   */
  static BlazeSyncBuildResult runBuildPhase(
      Project project, BlazeSyncParams syncParams, BlazeContext context) {
    BuildPhaseSyncTask task = new BuildPhaseSyncTask(project, syncParams);
    return task.run(context);
  }

  private final Project project;
  private final BlazeImportSettings importSettings;
  private final WorkspaceRoot workspaceRoot;
  private final BlazeSyncParams syncParams;
  private final BlazeSyncBuildResult.Builder resultBuilder;
  private final BuildPhaseSyncStats.Builder buildStats;

  private BuildPhaseSyncTask(Project project, BlazeSyncParams syncParams) {
    this.project = project;
    this.importSettings = BlazeImportSettingsManager.getInstance(project).getImportSettings();
    this.workspaceRoot = WorkspaceRoot.fromImportSettings(importSettings);
    this.syncParams = syncParams;
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

  private BlazeSyncBuildResult.Builder doRun(BlazeContext context)
      throws SyncFailedException, SyncCanceledException {
    SyncProjectState projectState = getProjectState(context);
    resultBuilder.setProjectState(projectState);

    List<TargetExpression> targets = Lists.newArrayList();
    if (syncParams.addProjectViewTargets) {
      List<TargetExpression> projectViewTargets =
          projectState.getProjectViewSet().listItems(TargetSection.KEY);
      if (!projectViewTargets.isEmpty()) {
        targets.addAll(projectViewTargets);
        printTargets(context, "project view", projectViewTargets);
      }
    }
    if (syncParams.addWorkingSet && projectState.getWorkingSet() != null) {
      Collection<? extends TargetExpression> workingSetTargets =
          getWorkingSetTargets(projectState.getProjectViewSet(), projectState.getWorkingSet());
      if (!workingSetTargets.isEmpty()) {
        targets.addAll(workingSetTargets);
        printTargets(context, "working set", workingSetTargets);
      }
    }
    if (!syncParams.targetExpressions.isEmpty()) {
      targets.addAll(syncParams.targetExpressions);
      printTargets(context, syncParams.title, syncParams.targetExpressions);
    }
    buildStats.setTargets(targets);

    ShardedTargetsResult shardedTargetsResult =
        BlazeBuildTargetSharder.expandAndShardTargets(
            project,
            context,
            workspaceRoot,
            projectState.getProjectViewSet(),
            projectState.getWorkspacePathResolver(),
            targets);
    if (shardedTargetsResult.buildResult.status == BuildResult.Status.FATAL_ERROR) {
      throw new SyncFailedException();
    }
    ShardedTargetList shardedTargets = shardedTargetsResult.shardedTargets;

    buildStats.setSyncSharded(shardedTargets.shardedTargets.size() > 1);

    BlazeBuildOutputs blazeBuildResult =
        getBlazeBuildResult(
            project,
            context,
            projectState.getProjectViewSet(),
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
    return BlazeSyncBuildResult.builder()
        .setProjectState(projectState)
        .setBuildResult(blazeBuildResult);
  }

  private SyncProjectState getProjectState(BlazeContext context)
      throws SyncFailedException, SyncCanceledException {
    if (!FileOperationProvider.getInstance().exists(workspaceRoot.directory())) {
      IssueOutput.error(String.format("Workspace '%s' doesn't exist.", workspaceRoot.directory()))
          .submit(context);
      throw new SyncFailedException();
    }

    BlazeVcsHandler vcsHandler = BlazeVcsHandler.vcsHandlerForProject(project);
    if (vcsHandler == null) {
      IssueOutput.error("Could not find a VCS handler").submit(context);
      throw new SyncFailedException();
    }

    ListeningExecutorService executor = BlazeExecutor.getInstance().getExecutor();
    WorkspacePathResolverAndProjectView workspacePathResolverAndProjectView =
        computeWorkspacePathResolverAndProjectView(context, vcsHandler, executor);
    if (workspacePathResolverAndProjectView == null) {
      throw new SyncFailedException();
    }
    ProjectViewSet projectViewSet = workspacePathResolverAndProjectView.projectViewSet;

    List<String> syncFlags =
        BlazeFlags.blazeFlags(
            project, projectViewSet, BlazeCommandName.INFO, BlazeInvocationContext.SYNC_CONTEXT);
    buildStats.setSyncFlags(syncFlags);
    ListenableFuture<BlazeInfo> blazeInfoFuture =
        BlazeInfoRunner.getInstance()
            .runBlazeInfo(
                context,
                importSettings.getBuildSystem(),
                Blaze.getBuildSystemProvider(project).getSyncBinaryPath(project),
                workspaceRoot,
                syncFlags);

    ListenableFuture<WorkingSet> workingSetFuture =
        vcsHandler.getWorkingSet(project, context, workspaceRoot, executor);

    BlazeInfo blazeInfo =
        FutureUtil.waitForFuture(context, blazeInfoFuture)
            .timed(Blaze.buildSystemName(project) + "Info", EventType.BlazeInvocation)
            .withProgressMessage(
                String.format("Running %s info...", Blaze.buildSystemName(project)))
            .onError(String.format("Could not run %s info", Blaze.buildSystemName(project)))
            .run()
            .result();
    if (blazeInfo == null) {
      throw new SyncFailedException();
    }
    BlazeVersionData blazeVersionData =
        BlazeVersionData.build(importSettings.getBuildSystem(), workspaceRoot, blazeInfo);

    if (!BuildSystemVersionChecker.verifyVersionSupported(context, blazeVersionData)) {
      throw new SyncFailedException();
    }

    WorkspacePathResolver workspacePathResolver =
        workspacePathResolverAndProjectView.workspacePathResolver;
    WorkspaceLanguageSettings workspaceLanguageSettings =
        LanguageSupport.createWorkspaceLanguageSettings(projectViewSet);

    if (!ProjectViewVerifier.verifyProjectView(
        project, context, workspacePathResolver, projectViewSet, workspaceLanguageSettings)) {
      throw new SyncFailedException();
    }

    WorkingSet workingSet =
        FutureUtil.waitForFuture(context, workingSetFuture)
            .timed("WorkingSet", EventType.Other)
            .withProgressMessage("Computing VCS working set...")
            .onError("Could not compute working set")
            .run()
            .result();
    if (context.isCancelled()) {
      throw new SyncCanceledException();
    }
    if (context.hasErrors()) {
      throw new SyncFailedException();
    }

    if (workingSet != null) {
      printWorkingSet(context, workingSet);
    }
    return SyncProjectState.builder()
        .setSyncParams(syncParams)
        .setProjectViewSet(projectViewSet)
        .setLanguageSettings(workspaceLanguageSettings)
        .setBlazeInfo(blazeInfo)
        .setBlazeVersionData(blazeVersionData)
        .setWorkingSet(workingSet)
        .setWorkspacePathResolver(workspacePathResolver)
        .build();
  }

  private static class WorkspacePathResolverAndProjectView {
    final WorkspacePathResolver workspacePathResolver;
    final ProjectViewSet projectViewSet;

    WorkspacePathResolverAndProjectView(
        WorkspacePathResolver workspacePathResolver, ProjectViewSet projectViewSet) {
      this.workspacePathResolver = workspacePathResolver;
      this.projectViewSet = projectViewSet;
    }
  }

  private WorkspacePathResolverAndProjectView computeWorkspacePathResolverAndProjectView(
      BlazeContext context, BlazeVcsHandler vcsHandler, ListeningExecutorService executor) {
    context.output(new StatusOutput("Updating VCS..."));

    for (int i = 0; i < 3; ++i) {
      WorkspacePathResolver vcsWorkspacePathResolver = null;
      BlazeVcsHandler.BlazeVcsSyncHandler vcsSyncHandler =
          vcsHandler.createSyncHandler(project, workspaceRoot);
      if (vcsSyncHandler != null) {
        boolean ok =
            Scope.push(
                context,
                (childContext) -> {
                  childContext.push(new TimingScope("UpdateVcs", EventType.Other));
                  return vcsSyncHandler.update(context, executor);
                });
        if (!ok) {
          return null;
        }
        vcsWorkspacePathResolver = vcsSyncHandler.getWorkspacePathResolver();
      }

      WorkspacePathResolver workspacePathResolver =
          vcsWorkspacePathResolver != null
              ? vcsWorkspacePathResolver
              : new WorkspacePathResolverImpl(workspaceRoot);

      ProjectViewSet projectViewSet =
          ProjectViewManager.getInstance(project).reloadProjectView(context, workspacePathResolver);
      if (projectViewSet == null) {
        return null;
      }

      if (vcsSyncHandler != null) {
        BlazeVcsHandler.BlazeVcsSyncHandler.ValidationResult validationResult =
            vcsSyncHandler.validateProjectView(context, projectViewSet);
        switch (validationResult) {
          case OK:
            // Fall-through and return
            break;
          case Error:
            return null;
          case RestartSync:
            continue;
          default:
            // Cannot happen
            return null;
        }
      }

      return new WorkspacePathResolverAndProjectView(workspacePathResolver, projectViewSet);
    }
    return null;
  }

  private void printWorkingSet(BlazeContext context, WorkingSet workingSet) {
    List<String> messages = Lists.newArrayList();
    messages.addAll(
        workingSet.addedFiles.stream()
            .map(file -> file.relativePath() + " (added)")
            .collect(Collectors.toList()));
    messages.addAll(
        workingSet.modifiedFiles.stream()
            .map(file -> file.relativePath() + " (modified)")
            .collect(Collectors.toList()));
    Collections.sort(messages);

    if (messages.isEmpty()) {
      context.output(PrintOutput.log("Your working set is empty"));
      return;
    }
    int maxFiles = 20;
    for (String message : Iterables.limit(messages, maxFiles)) {
      context.output(PrintOutput.log("  " + message));
    }
    if (messages.size() > maxFiles) {
      context.output(PrintOutput.log(String.format("  (and %d more)", messages.size() - maxFiles)));
    }
    context.output(PrintOutput.output(""));
  }

  private void printTargets(
      BlazeContext context, String owner, Collection<? extends TargetExpression> targets) {
    StringBuilder sb = new StringBuilder("Sync targets from ");
    sb.append(owner).append(':').append('\n');
    for (TargetExpression targetExpression : targets) {
      sb.append("  ").append(targetExpression).append('\n');
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
          context.output(new StatusOutput("Building blaze targets..."));
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
