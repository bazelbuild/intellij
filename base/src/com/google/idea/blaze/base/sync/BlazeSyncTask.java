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
package com.google.idea.blaze.base.sync;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.SettableFuture;
import com.google.idea.blaze.base.async.FutureUtil;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.command.BlazeInvocationContext;
import com.google.idea.blaze.base.command.BlazeInvocationContext.ContextType;
import com.google.idea.blaze.base.command.buildresult.RemoteOutputArtifact;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.command.info.BlazeInfoRunner;
import com.google.idea.blaze.base.experiments.ExperimentScope;
import com.google.idea.blaze.base.filecache.FileCaches;
import com.google.idea.blaze.base.filecache.RemoteOutputsCache;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.io.VirtualFileSystemProvider;
import com.google.idea.blaze.base.issueparser.IssueOutputFilter;
import com.google.idea.blaze.base.logging.EventLoggingService;
import com.google.idea.blaze.base.logging.utils.SyncStats;
import com.google.idea.blaze.base.model.BlazeLibrary;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.BlazeVersionData;
import com.google.idea.blaze.base.model.RemoteOutputArtifacts;
import com.google.idea.blaze.base.model.SyncState;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.plugin.BuildSystemVersionChecker;
import com.google.idea.blaze.base.prefetch.PrefetchService;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.ProjectViewVerifier;
import com.google.idea.blaze.base.projectview.section.sections.TargetSection;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.output.PrintOutput;
import com.google.idea.blaze.base.scope.output.StatusOutput;
import com.google.idea.blaze.base.scope.scopes.BlazeConsoleScope;
import com.google.idea.blaze.base.scope.scopes.IdeaLogScope;
import com.google.idea.blaze.base.scope.scopes.IssuesScope;
import com.google.idea.blaze.base.scope.scopes.NotificationScope;
import com.google.idea.blaze.base.scope.scopes.PerformanceWarningScope;
import com.google.idea.blaze.base.scope.scopes.ProgressIndicatorScope;
import com.google.idea.blaze.base.scope.scopes.TimingScope;
import com.google.idea.blaze.base.scope.scopes.TimingScope.EventType;
import com.google.idea.blaze.base.scope.scopes.TimingScopeListener.TimedEvent;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.google.idea.blaze.base.settings.BlazeUserSettings.FocusBehavior;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin.ModuleEditor;
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs;
import com.google.idea.blaze.base.sync.aspects.BlazeIdeInterface;
import com.google.idea.blaze.base.sync.aspects.BuildResult;
import com.google.idea.blaze.base.sync.aspects.strategy.AspectStrategy;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManagerImpl;
import com.google.idea.blaze.base.sync.libraries.BlazeLibraryCollector;
import com.google.idea.blaze.base.sync.libraries.LibraryEditor;
import com.google.idea.blaze.base.sync.projectstructure.ContentEntryEditor;
import com.google.idea.blaze.base.sync.projectstructure.DirectoryStructure;
import com.google.idea.blaze.base.sync.projectstructure.ModuleEditorImpl;
import com.google.idea.blaze.base.sync.projectstructure.ModuleEditorProvider;
import com.google.idea.blaze.base.sync.projectstructure.ModuleFinder;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.base.sync.projectview.LanguageSupport;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.sharding.BlazeBuildTargetSharder;
import com.google.idea.blaze.base.sync.sharding.BlazeBuildTargetSharder.ShardedTargetsResult;
import com.google.idea.blaze.base.sync.sharding.ShardedTargetList;
import com.google.idea.blaze.base.sync.sharding.SuggestBuildShardingNotification;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoderImpl;
import com.google.idea.blaze.base.sync.workspace.WorkingSet;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverImpl;
import com.google.idea.blaze.base.util.SaveUtil;
import com.google.idea.blaze.base.vcs.BlazeVcsHandler;
import com.google.idea.common.experiments.BoolExperiment;
import com.google.idea.common.transactions.Transactions;
import com.google.idea.sdkcompat.openapi.SaveFromInsideWriteAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Progressive;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** Syncs the project with blaze. */
final class BlazeSyncTask implements Progressive {

  private static final Logger logger = Logger.getInstance(BlazeSyncTask.class);
  private static final BoolExperiment saveStateDuringRootsChange =
      new BoolExperiment("blaze.save.state.during.roots.change", true);

  private final Project project;
  private final BlazeImportSettings importSettings;
  private final WorkspaceRoot workspaceRoot;
  private final boolean showPerformanceWarnings;
  private final SyncStats.Builder syncStats = SyncStats.builder();

  private final List<TimedEvent> timedEvents = Collections.synchronizedList(new ArrayList<>());

  private BlazeSyncParams syncParams;

  BlazeSyncTask(Project project, BlazeImportSettings importSettings, BlazeSyncParams syncParams) {
    this.project = project;
    this.importSettings = importSettings;
    this.workspaceRoot = WorkspaceRoot.fromImportSettings(importSettings);
    this.syncParams = syncParams;
    this.showPerformanceWarnings = BlazeUserSettings.getInstance().getShowPerformanceWarnings();
  }

  @Override
  public void run(final ProgressIndicator indicator) {
    Scope.root(
        (BlazeContext context) -> {
          context.push(new ExperimentScope());
          if (showPerformanceWarnings) {
            context.push(new PerformanceWarningScope());
          }
          context.push(new ProgressIndicatorScope(indicator));

          BlazeUserSettings userSettings = BlazeUserSettings.getInstance();
          context
              .push(
                  new BlazeConsoleScope.Builder(project, indicator)
                      .setPopupBehavior(
                          syncParams.backgroundSync
                              ? FocusBehavior.NEVER
                              : userSettings.getShowBlazeConsoleOnSync())
                      .addConsoleFilters(
                          new IssueOutputFilter(project, workspaceRoot, ContextType.Sync, true))
                      .build())
              .push(
                  new IssuesScope(
                      project,
                      syncParams.backgroundSync
                          ? FocusBehavior.NEVER
                          : userSettings.getShowProblemsViewOnSync()))
              .push(new IdeaLogScope());
          if (!syncParams.backgroundSync && syncParams.syncMode != SyncMode.NO_BUILD) {
            context.push(
                new NotificationScope(
                    project, "Sync", "Sync project", "Sync successful", "Sync failed"));
          }

          context.output(new StatusOutput(String.format("Syncing project: %s...", syncParams)));
          syncProject(context);
        });
  }

  @Nullable
  private BlazeProjectData getOldProjectData(BlazeContext context) {
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManagerImpl.getImpl(project).loadProjectRoot(importSettings);
    if (blazeProjectData == null && syncParams.syncMode != SyncMode.NO_BUILD) {
      context.output(
          new StatusOutput(
              "Couldn't load previously cached project data; full sync will be needed"));
    }
    return blazeProjectData;
  }

  /** Returns true if sync successfully completed */
  @VisibleForTesting
  boolean syncProject(BlazeContext context) {
    TimingScope timingScope = new TimingScope("Sync", EventType.Other);
    timingScope.addScopeListener(timedEvents::add);
    context.push(timingScope);

    long syncStartTime = System.currentTimeMillis();
    SyncResult syncResult = SyncResult.FAILURE;
    syncStats.setStartTimeInEpochTime(System.currentTimeMillis());
    try {
      SaveUtil.saveAllFiles();
      BlazeProjectData oldBlazeProjectData =
          syncParams.syncMode != SyncMode.FULL ? getOldProjectData(context) : null;
      if (oldBlazeProjectData == null && syncParams.syncMode != SyncMode.NO_BUILD) {
        syncParams =
            BlazeSyncParams.Builder.copy(syncParams)
                .setSyncMode(SyncMode.FULL)
                .addProjectViewTargets(true)
                .build();
      }
      syncStats.setSyncMode(syncParams.syncMode);

      onSyncStart(project, context, syncParams.syncMode);
      if (syncParams.syncMode != SyncMode.STARTUP) {
        syncResult = doSyncProject(context, oldBlazeProjectData);
        if (context.isCancelled()) {
          syncResult = SyncResult.CANCELLED;
        }
      } else {
        syncResult = SyncResult.SUCCESS;
      }
      if (syncResult.successful()) {
        ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
        BlazeProjectData blazeProjectData =
            BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
        if (syncParams.syncMode != SyncMode.NO_BUILD) {
          updateInMemoryState(
              project, context, projectViewSet, blazeProjectData, syncParams.syncMode);
        }
        onSyncComplete(project, context, projectViewSet, blazeProjectData, syncResult);
      }
    } catch (Throwable e) {
      logSyncError(context, e);
    } finally {
      try {
        syncStats
            .setSyncTitle(syncParams.title)
            .setTotalExecTimeMs(System.currentTimeMillis() - syncStartTime)
            .setSyncResult(syncResult);
        EventLoggingService.getInstance().log(buildStats(syncStats));
      } catch (Exception e) {
        logSyncError(context, e);
      }
      afterSync(project, context, syncResult);
    }
    return syncResult == SyncResult.SUCCESS || syncResult == SyncResult.PARTIAL_SUCCESS;
  }

  private void logSyncError(BlazeContext context, Throwable e) {
    // ignore ProcessCanceledException
    Throwable cause = e;
    while (cause != null) {
      if (cause instanceof ProcessCanceledException) {
        return;
      }
      cause = cause.getCause();
    }
    logger.error(e);
    IssueOutput.error("Internal error: " + e.getMessage()).submit(context);
  }

  private static class SyncFailedException extends Exception {}

  private static class SyncCanceledException extends Exception {}

  /** @return true if sync successfully completed */
  private SyncResult doSyncProject(
      BlazeContext context, @Nullable BlazeProjectData oldProjectData) {
    try {
      BlazeSyncBuildResult buildResult = runBuildPhase(context);
      if (context.isCancelled()) {
        return SyncResult.CANCELLED;
      }
      runProjectUpdatePhase(context, buildResult, oldProjectData);

      if (buildResult.getBuildResult().buildResult.status == BuildResult.Status.BUILD_ERROR) {
        String buildSystem = importSettings.getBuildSystem().getName();
        String message =
            String.format(
                "Sync was successful, but there were %1$s build errors. "
                    + "The project may not be fully updated or resolve until fixed. "
                    + "If the errors are from your working set, please uncheck "
                    + "'%1$s > Sync > Expand Sync to Working Set' and try again.",
                buildSystem);
        context.output(PrintOutput.error(message));
        IssueOutput.warn(message).submit(context);
        return SyncResult.PARTIAL_SUCCESS;
      }
      return SyncResult.SUCCESS;

    } catch (SyncCanceledException e) {
      return SyncResult.CANCELLED;
    } catch (SyncFailedException e) {
      return SyncResult.FAILURE;
    }
  }

  /** Runs the blaze build phase of sync. */
  private BlazeSyncBuildResult runBuildPhase(BlazeContext context)
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
    syncStats.setSyncFlags(syncFlags);
    syncStats.setSyncBinaryType(Blaze.getBuildSystemProvider(project).getSyncBinaryType());
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

    syncStats.setLanguagesActive(new ArrayList<>(workspaceLanguageSettings.getActiveLanguages()));
    syncStats.setWorkspaceType(workspaceLanguageSettings.getWorkspaceType());

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

    List<TargetExpression> targets = Lists.newArrayList();
    if (syncParams.addProjectViewTargets) {
      List<TargetExpression> projectViewTargets = projectViewSet.listItems(TargetSection.KEY);
      if (!projectViewTargets.isEmpty()) {
        syncStats.setBlazeProjectTargets(new ArrayList<>(projectViewTargets));
        targets.addAll(projectViewTargets);
        printTargets(context, "project view", projectViewTargets);
      }
    }
    if (syncParams.addWorkingSet && workingSet != null) {
      Collection<? extends TargetExpression> workingSetTargets =
          getWorkingSetTargets(projectViewSet, workingSet);
      if (!workingSetTargets.isEmpty()) {
        targets.addAll(workingSetTargets);
        syncStats.setWorkingSetTargets(new ArrayList<>(workingSetTargets));
        printTargets(context, "working set", workingSetTargets);
      }
    }
    if (!syncParams.targetExpressions.isEmpty()) {
      targets.addAll(syncParams.targetExpressions);
      printTargets(context, syncParams.title, syncParams.targetExpressions);
    }

    ShardedTargetsResult shardedTargetsResult =
        BlazeBuildTargetSharder.expandAndShardTargets(
            project, context, workspaceRoot, projectViewSet, workspacePathResolver, targets);
    if (shardedTargetsResult.buildResult.status == BuildResult.Status.FATAL_ERROR) {
      throw new SyncFailedException();
    }
    ShardedTargetList shardedTargets = shardedTargetsResult.shardedTargets;

    syncStats.setSyncSharded(shardedTargets.shardedTargets.size() > 1);

    BlazeBuildOutputs blazeBuildResult =
        getBlazeBuildResult(
            project, context, projectViewSet, blazeInfo, shardedTargets, workspaceLanguageSettings);
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
        .setProjectViewSet(projectViewSet)
        .setLanguageSettings(workspaceLanguageSettings)
        .setBlazeInfo(blazeInfo)
        .setBlazeVersionData(blazeVersionData)
        .setWorkingSet(workingSet)
        .setWorkspacePathResolver(workspacePathResolver)
        .setBuildResult(blazeBuildResult)
        .build();
  }

  private void runProjectUpdatePhase(
      BlazeContext context,
      BlazeSyncBuildResult buildResult,
      @Nullable BlazeProjectData oldProjectData)
      throws SyncFailedException, SyncCanceledException {

    SyncState.Builder syncStateBuilder = new SyncState.Builder();

    TargetMap targetMap = updateTargetMap(context, buildResult, oldProjectData, syncStateBuilder);
    if (targetMap == null) {
      context.setHasError();
      throw new SyncFailedException();
    }
    context.output(PrintOutput.log("Target map size: " + targetMap.targets().size()));

    RemoteOutputArtifacts oldRemoteState = RemoteOutputArtifacts.fromProjectData(oldProjectData);
    RemoteOutputArtifacts newRemoteState =
        oldRemoteState.appendNewOutputs(getTrackedRemoteOutputs(buildResult.getBuildResult()));
    syncStateBuilder.put(newRemoteState);
    ArtifactLocationDecoder artifactLocationDecoder =
        new ArtifactLocationDecoderImpl(
            buildResult.getBlazeInfo(), buildResult.getWorkspacePathResolver(), newRemoteState);

    Scope.push(
        context,
        childContext -> {
          childContext.push(new TimingScope("UpdateRemoteOutputsCache", EventType.Prefetching));
          RemoteOutputsCache.getInstance(project)
              .updateCache(
                  context,
                  targetMap,
                  buildResult.getLanguageSettings(),
                  newRemoteState,
                  oldRemoteState,
                  /* clearCache= */ syncParams.syncMode == SyncMode.FULL);
        });

    Scope.push(
        context,
        childContext -> {
          childContext.push(new TimingScope("UpdateSyncState", EventType.Other));
          for (BlazeSyncPlugin syncPlugin : BlazeSyncPlugin.EP_NAME.getExtensions()) {
            syncPlugin.updateSyncState(
                project,
                childContext,
                workspaceRoot,
                buildResult.getProjectViewSet(),
                buildResult.getLanguageSettings(),
                buildResult.getBlazeInfo(),
                buildResult.getBlazeVersionData(),
                buildResult.getWorkingSet(),
                buildResult.getWorkspacePathResolver(),
                artifactLocationDecoder,
                targetMap,
                syncStateBuilder,
                oldProjectData != null ? oldProjectData.getSyncState() : null,
                syncParams.syncMode);
          }
        });
    if (context.isCancelled()) {
      throw new SyncCanceledException();
    }
    if (context.hasErrors()) {
      throw new SyncFailedException();
    }

    BlazeProjectData newProjectData =
        new BlazeProjectData(
            targetMap,
            buildResult.getBlazeInfo(),
            buildResult.getBlazeVersionData(),
            buildResult.getWorkspacePathResolver(),
            artifactLocationDecoder,
            buildResult.getLanguageSettings(),
            syncStateBuilder.build());

    FileCaches.onSync(
        project,
        context,
        buildResult.getProjectViewSet(),
        newProjectData,
        oldProjectData,
        syncParams.syncMode);
    ListenableFuture<?> prefetch =
        PrefetchService.getInstance()
            .prefetchProjectFiles(project, buildResult.getProjectViewSet(), newProjectData);
    FutureUtil.waitForFuture(context, prefetch)
        .withProgressMessage("Prefetching files...")
        .timed("PrefetchFiles", EventType.Prefetching)
        .onError("Prefetch failed")
        .run();

    ListenableFuture<DirectoryStructure> directoryStructureFuture =
        DirectoryStructure.getRootDirectoryStructure(
            project, workspaceRoot, buildResult.getProjectViewSet());

    refreshVirtualFileSystem(context, newProjectData);

    DirectoryStructure directoryStructure =
        FutureUtil.waitForFuture(context, directoryStructureFuture)
            .withProgressMessage("Computing directory structure...")
            .timed("DirectoryStructure", EventType.Other)
            .onError("Directory structure computation failed")
            .run()
            .result();
    if (directoryStructure == null) {
      throw new SyncFailedException();
    }

    boolean success =
        updateProject(
            context,
            buildResult.getProjectViewSet(),
            buildResult.getBlazeVersionData(),
            directoryStructure,
            oldProjectData,
            newProjectData);
    if (!success) {
      throw new SyncFailedException();
    }
  }

  /** Returns the {@link RemoteOutputArtifact}s we want to track between syncs. */
  private static ImmutableSet<RemoteOutputArtifact> getTrackedRemoteOutputs(
      BlazeBuildOutputs buildOutput) {
    // don't track remote intellij-info.txt outputs -- they're already tracked in
    // BlazeIdeInterfaceState
    Predicate<String> pathFilter = AspectStrategy.ASPECT_OUTPUT_FILE_PREDICATE.negate();
    return buildOutput.perOutputGroupArtifacts.values().stream()
        .filter(a -> a instanceof RemoteOutputArtifact)
        .map(a -> (RemoteOutputArtifact) a)
        .filter(a -> pathFilter.test(a.getRelativePath()))
        .collect(toImmutableSet());
  }

  private static void refreshVirtualFileSystem(
      BlazeContext context, BlazeProjectData blazeProjectData) {
    Scope.push(
        context,
        (childContext) -> {
          childContext.push(new TimingScope("RefreshVirtualFileSystem", EventType.Other));
          childContext.output(new StatusOutput("Refreshing files"));
          ImmutableSetMultimap.Builder<RefreshRequestType, VirtualFile> requests =
              ImmutableSetMultimap.builder();
          for (BlazeSyncPlugin syncPlugin : BlazeSyncPlugin.EP_NAME.getExtensions()) {
            requests.putAll(syncPlugin.filesToRefresh(blazeProjectData));
          }
          // Like VfsUtil.markDirtyAndRefresh, but with callback for when async refreshes finish.
          List<ListenableFuture<?>> futures = new ArrayList<>();
          for (Map.Entry<RefreshRequestType, Collection<VirtualFile>> entry :
              requests.build().asMap().entrySet()) {
            RefreshRequestType refreshRequestType = entry.getKey();
            List<VirtualFile> list =
                VfsUtil.markDirty(
                    refreshRequestType.recursive(),
                    refreshRequestType.reloadChildren(),
                    entry.getValue().toArray(new VirtualFile[0]));
            if (list.isEmpty()) {
              continue;
            }
            SettableFuture<Boolean> completion = SettableFuture.create();
            futures.add(completion);
            LocalFileSystem.getInstance()
                .refreshFiles(
                    list,
                    /* async= */ true,
                    refreshRequestType.recursive(),
                    () -> completion.set(true));
          }
          try {
            Futures.allAsList(futures).get();
          } catch (InterruptedException e) {
            throw new ProcessCanceledException(e);
          } catch (ExecutionException e) {
            IssueOutput.warn("Failed to refresh file system").submit(childContext);
            logger.warn("Failed to refresh file system", e);
          }
        });
  }

  static class WorkspacePathResolverAndProjectView {
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

  @Nullable
  private TargetMap updateTargetMap(
      BlazeContext parentContext,
      BlazeSyncBuildResult buildResult,
      @Nullable BlazeProjectData oldProjectData,
      SyncState.Builder syncStateBuilder) {
    boolean mergeWithOldState = !syncParams.addProjectViewTargets;
    return Scope.push(
        parentContext,
        context -> {
          context.push(new TimingScope("ReadBuildOutputs", EventType.BlazeInvocation));
          context.output(new StatusOutput("Parsing build outputs..."));
          BlazeIdeInterface blazeIdeInterface = BlazeIdeInterface.getInstance();
          return blazeIdeInterface.updateTargetMap(
              project,
              context,
              workspaceRoot,
              buildResult,
              syncStateBuilder,
              mergeWithOldState,
              oldProjectData);
        });
  }

  private boolean updateProject(
      BlazeContext parentContext,
      ProjectViewSet projectViewSet,
      BlazeVersionData blazeVersionData,
      DirectoryStructure directoryStructure,
      @Nullable BlazeProjectData oldBlazeProjectData,
      BlazeProjectData newBlazeProjectData) {
    return Scope.push(
        parentContext,
        context -> {
          context.push(new TimingScope("UpdateProjectStructure", EventType.Other));
          context.output(new StatusOutput("Committing project structure..."));

          try {
            Transactions.submitWriteActionTransactionAndWait(
                () ->
                    ProjectRootManagerEx.getInstanceEx(this.project)
                        .mergeRootsChangesDuring(
                            () -> {
                              updateProjectStructure(
                                  context,
                                  importSettings,
                                  projectViewSet,
                                  blazeVersionData,
                                  directoryStructure,
                                  newBlazeProjectData,
                                  oldBlazeProjectData);
                              if (saveStateDuringRootsChange.getValue()) {
                                // a temporary workaround for IDEA-205934
                                // #api183: remove when the upstream bug is fixed
                                SaveFromInsideWriteAction.saveAll();
                              }
                            }));
          } catch (ProcessCanceledException e) {
            context.setCancelled();
            throw e;
          } catch (Throwable e) {
            IssueOutput.error("Internal error. Error: " + e).submit(context);
            logger.error(e);
            return false;
          }

          BlazeProjectDataManagerImpl.getImpl(project)
              .saveProject(importSettings, newBlazeProjectData);
          return true;
        });
  }

  private void updateProjectStructure(
      BlazeContext context,
      BlazeImportSettings importSettings,
      ProjectViewSet projectViewSet,
      BlazeVersionData blazeVersionData,
      DirectoryStructure directoryStructure,
      BlazeProjectData newBlazeProjectData,
      @Nullable BlazeProjectData oldBlazeProjectData) {

    for (BlazeSyncPlugin syncPlugin : BlazeSyncPlugin.EP_NAME.getExtensions()) {
      syncPlugin.updateProjectSdk(
          project, context, projectViewSet, blazeVersionData, newBlazeProjectData);
    }

    ModuleEditorImpl moduleEditor =
        ModuleEditorProvider.getInstance().getModuleEditor(project, importSettings);

    ModuleType workspaceModuleType = null;
    for (BlazeSyncPlugin syncPlugin : BlazeSyncPlugin.EP_NAME.getExtensions()) {
      workspaceModuleType =
          syncPlugin.getWorkspaceModuleType(
              newBlazeProjectData.getWorkspaceLanguageSettings().getWorkspaceType());
      if (workspaceModuleType != null) {
        break;
      }
    }
    if (workspaceModuleType == null) {
      workspaceModuleType = ModuleType.EMPTY;
      IssueOutput.warn("Could not set module type for workspace module.").submit(context);
    }

    Module workspaceModule =
        moduleEditor.createModule(BlazeDataStorage.WORKSPACE_MODULE_NAME, workspaceModuleType);
    ModifiableRootModel workspaceModifiableModel = moduleEditor.editModule(workspaceModule);

    ContentEntryEditor.createContentEntries(
        project,
        workspaceRoot,
        projectViewSet,
        newBlazeProjectData,
        directoryStructure,
        workspaceModifiableModel);

    List<BlazeLibrary> libraries =
        BlazeLibraryCollector.getLibraries(projectViewSet, newBlazeProjectData);
    LibraryEditor.updateProjectLibraries(
        project, context, projectViewSet, newBlazeProjectData, libraries);
    LibraryEditor.configureDependencies(workspaceModifiableModel, libraries);

    for (BlazeSyncPlugin blazeSyncPlugin : BlazeSyncPlugin.EP_NAME.getExtensions()) {
      blazeSyncPlugin.updateProjectStructure(
          project,
          context,
          workspaceRoot,
          projectViewSet,
          newBlazeProjectData,
          oldBlazeProjectData,
          moduleEditor,
          workspaceModule,
          workspaceModifiableModel);
    }

    createProjectDataDirectoryModule(
        moduleEditor, new File(importSettings.getProjectDataDirectory()), workspaceModuleType);

    moduleEditor.commitWithGc(context);
  }

  private void updateInMemoryState(
      Project project,
      BlazeContext parentContext,
      ProjectViewSet projectViewSet,
      BlazeProjectData blazeProjectData,
      SyncMode syncMode) {
    Scope.push(
        parentContext,
        context -> {
          context.push(new TimingScope("UpdateInMemoryState", EventType.Other));
          context.output(new StatusOutput("Updating in-memory state..."));
          ApplicationManager.getApplication()
              .runReadAction(
                  () -> {
                    Module workspaceModule =
                        ModuleFinder.getInstance(project)
                            .findModuleByName(BlazeDataStorage.WORKSPACE_MODULE_NAME);
                    for (BlazeSyncPlugin blazeSyncPlugin :
                        BlazeSyncPlugin.EP_NAME.getExtensions()) {
                      blazeSyncPlugin.updateInMemoryState(
                          project,
                          context,
                          workspaceRoot,
                          projectViewSet,
                          blazeProjectData,
                          workspaceModule,
                          syncMode);
                    }
                  });
        });
  }

  /**
   * Creates a module that includes the user's data directory.
   *
   * <p>This is useful to be able to edit the project view without IntelliJ complaining it's outside
   * the project.
   */
  private void createProjectDataDirectoryModule(
      ModuleEditor moduleEditor, File projectDataDirectory, ModuleType moduleType) {
    Module module = moduleEditor.createModule(".project-data-dir", moduleType);
    ModifiableRootModel modifiableModel = moduleEditor.editModule(module);
    ContentEntry rootContentEntry =
        modifiableModel.addContentEntry(pathToUrl(projectDataDirectory));
    rootContentEntry.addExcludeFolder(pathToUrl(new File(projectDataDirectory, ".idea")));
    rootContentEntry.addExcludeFolder(
        pathToUrl(BlazeDataStorage.getProjectDataDir(importSettings)));
  }

  private static String pathToUrl(File path) {
    String filePath = FileUtil.toSystemIndependentName(path.getPath());
    return VirtualFileManager.constructUrl(
        VirtualFileSystemProvider.getInstance().getSystem().getProtocol(), filePath);
  }

  private static void onSyncStart(Project project, BlazeContext context, SyncMode syncMode) {
    final SyncListener[] syncListeners = SyncListener.EP_NAME.getExtensions();
    for (SyncListener syncListener : syncListeners) {
      syncListener.onSyncStart(project, context, syncMode);
    }
  }

  private void afterSync(Project project, BlazeContext context, SyncResult syncResult) {
    final SyncListener[] syncListeners = SyncListener.EP_NAME.getExtensions();
    for (SyncListener syncListener : syncListeners) {
      syncListener.afterSync(project, context, syncParams.syncMode, syncResult);
    }
  }

  private void onSyncComplete(
      Project project,
      BlazeContext context,
      ProjectViewSet projectViewSet,
      BlazeProjectData blazeProjectData,
      SyncResult syncResult) {
    validate(project, context, blazeProjectData);

    final SyncListener[] syncListeners = SyncListener.EP_NAME.getExtensions();
    for (SyncListener syncListener : syncListeners) {
      syncListener.onSyncComplete(
          project,
          context,
          importSettings,
          projectViewSet,
          blazeProjectData,
          syncParams.syncMode,
          syncResult);
    }
  }

  private static void validate(
      Project project, BlazeContext context, BlazeProjectData blazeProjectData) {
    for (BlazeSyncPlugin syncPlugin : BlazeSyncPlugin.EP_NAME.getExtensions()) {
      syncPlugin.validate(project, context, blazeProjectData);
    }
  }

  private SyncStats buildStats(SyncStats.Builder stats) {
    ImmutableList<TimedEvent> eventsCopy;
    synchronized (timedEvents) {
      eventsCopy = ImmutableList.copyOf(timedEvents);
    }
    long blazeExecTime =
        eventsCopy.stream()
            .filter(e -> e.isLeafEvent && e.type == EventType.BlazeInvocation)
            .mapToLong(e -> e.durationMillis)
            .sum();
    stats.setBlazeExecTimeMs(blazeExecTime);
    stats.setTimedEvents(eventsCopy);
    return stats.build();
  }
}
