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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMultimap;
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
import com.google.idea.blaze.base.command.info.BlazeConfigurationHandler;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.command.info.BlazeInfoRunner;
import com.google.idea.blaze.base.experiments.ExperimentScope;
import com.google.idea.blaze.base.filecache.FileCaches;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.io.VirtualFileSystemProvider;
import com.google.idea.blaze.base.issueparser.IssueOutputFilter;
import com.google.idea.blaze.base.logging.EventLoggingService;
import com.google.idea.blaze.base.logging.utils.SyncStats;
import com.google.idea.blaze.base.model.BlazeLibrary;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.BlazeVersionData;
import com.google.idea.blaze.base.model.SyncState;
import com.google.idea.blaze.base.model.SyncState.Builder;
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
import com.google.idea.blaze.base.scope.scopes.TimingScopeListener;
import com.google.idea.blaze.base.scope.scopes.TimingScopeListener.TimedEvent;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.google.idea.blaze.base.sync.BlazeSyncParams.SyncMode;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin.ModuleEditor;
import com.google.idea.blaze.base.sync.SyncListener.SyncResult;
import com.google.idea.blaze.base.sync.aspects.BlazeIdeInterface;
import com.google.idea.blaze.base.sync.aspects.BuildResult;
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
import com.google.idea.blaze.base.targetmaps.ReverseDependencyMap;
import com.google.idea.blaze.base.util.SaveUtil;
import com.google.idea.blaze.base.vcs.BlazeVcsHandler;
import com.google.idea.common.transactions.Transactions;
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
import com.intellij.openapi.vfs.VirtualFileManager;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** Syncs the project with blaze. */
final class BlazeSyncTask implements Progressive {

  private static final Logger logger = Logger.getInstance(BlazeSyncTask.class);

  private final Project project;
  private final BlazeImportSettings importSettings;
  private final WorkspaceRoot workspaceRoot;
  private final boolean showPerformanceWarnings;
  private final SyncStats.Builder syncStats = SyncStats.builder();
  private final TimingScopeListener timingScopeListener;
  private final List<TimedEvent> timedEvents = new ArrayList<>();

  private BlazeSyncParams syncParams;

  BlazeSyncTask(
      Project project, BlazeImportSettings importSettings, final BlazeSyncParams syncParams) {
    this.project = project;
    this.importSettings = importSettings;
    this.workspaceRoot = WorkspaceRoot.fromImportSettings(importSettings);
    this.syncParams = syncParams;
    this.showPerformanceWarnings = BlazeUserSettings.getInstance().getShowPerformanceWarnings();
    this.timingScopeListener =
        new TimingScopeListener() {
          @Override
          public void onScopeBegin(String name, EventType eventType) {}

          @Override
          public void onScopeEnd(TimedEvent event) {
            timedEvents.add(event);
          }
        };
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

          if (!syncParams.backgroundSync) {
            context
                .push(
                    new BlazeConsoleScope.Builder(project, indicator)
                        .setPopupBehavior(
                            BlazeUserSettings.getInstance().getShowBlazeConsoleOnSync())
                        .addConsoleFilters(
                            new IssueOutputFilter(
                                project, workspaceRoot, BlazeInvocationContext.Sync, true))
                        .build())
                .push(new IssuesScope(project, true))
                .push(new IdeaLogScope());
            if (syncParams.syncMode != SyncMode.NO_BUILD) {
              context.push(
                  new NotificationScope(
                      project, "Sync", "Sync project", "Sync successful", "Sync failed"));
            }
          }

          context.output(new StatusOutput(String.format("Syncing project: %s...", syncParams)));
          syncProject(context);
        });
  }

  @Nullable
  private BlazeProjectData getOldProjectData(BlazeContext context) {
    try {
      return BlazeProjectDataManagerImpl.getImpl(project).loadProjectRoot(importSettings);
    } catch (IOException e) {
      logger.info(e);
      if (syncParams.syncMode != SyncMode.NO_BUILD) {
        context.output(
            new StatusOutput(
                "Couldn't load previously cached project data; full sync will be needed"));
      }
      return null;
    }
  }

  /** Returns true if sync successfully completed */
  @VisibleForTesting
  boolean syncProject(BlazeContext context) {
    TimingScope timingScope = new TimingScope("Sync", EventType.Other);
    timingScope.addScopeListener(timingScopeListener, true);
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
          updateInMemoryState(project, context, projectViewSet, blazeProjectData);
        }
        onSyncComplete(project, context, projectViewSet, blazeProjectData, syncResult);
      }
    } catch (Throwable e) {
      logSyncError(context, e);
    } finally {
      try {
        syncStats.setTotalExecTimeMs(System.currentTimeMillis() - syncStartTime);
        syncStats.setSyncResult(syncResult);
        EventLoggingService.getInstance().ifPresent(s -> s.log(buildStats(syncStats)));
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

  /** @return true if sync successfully completed */
  private SyncResult doSyncProject(
      BlazeContext context, @Nullable BlazeProjectData oldBlazeProjectData) {
    long syncStartTime = System.currentTimeMillis();

    if (!FileOperationProvider.getInstance().exists(workspaceRoot.directory())) {
      IssueOutput.error(String.format("Workspace '%s' doesn't exist.", workspaceRoot.directory()))
          .submit(context);
      return SyncResult.FAILURE;
    }

    BlazeVcsHandler vcsHandler = BlazeVcsHandler.vcsHandlerForProject(project);
    if (vcsHandler == null) {
      IssueOutput.error("Could not find a VCS handler").submit(context);
      return SyncResult.FAILURE;
    }

    ListeningExecutorService executor = BlazeExecutor.getInstance().getExecutor();
    WorkspacePathResolverAndProjectView workspacePathResolverAndProjectView =
        computeWorkspacePathResolverAndProjectView(context, vcsHandler, executor);
    if (workspacePathResolverAndProjectView == null) {
      return SyncResult.FAILURE;
    }
    ProjectViewSet projectViewSet = workspacePathResolverAndProjectView.projectViewSet;

    List<String> syncFlags =
        BlazeFlags.blazeFlags(
            project, projectViewSet, BlazeCommandName.INFO, BlazeInvocationContext.Sync, null);
    syncStats.setSyncFlags(syncFlags);
    ListenableFuture<BlazeInfo> blazeInfoFuture =
        BlazeInfoRunner.getInstance()
            .runBlazeInfo(
                context,
                importSettings.getBuildSystem(),
                Blaze.getBuildSystemProvider(project).getSyncBinaryPath(),
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
      return SyncResult.FAILURE;
    }
    BlazeVersionData blazeVersionData =
        BlazeVersionData.build(importSettings.getBuildSystem(), workspaceRoot, blazeInfo);

    if (!BuildSystemVersionChecker.verifyVersionSupported(context, blazeVersionData)) {
      return SyncResult.FAILURE;
    }

    WorkspacePathResolver workspacePathResolver =
        workspacePathResolverAndProjectView.workspacePathResolver;
    ArtifactLocationDecoder artifactLocationDecoder =
        new ArtifactLocationDecoderImpl(blazeInfo, workspacePathResolver);

    WorkspaceLanguageSettings workspaceLanguageSettings =
        LanguageSupport.createWorkspaceLanguageSettings(projectViewSet);

    syncStats.setLanguagesActive(new ArrayList<>(workspaceLanguageSettings.activeLanguages));
    syncStats.setWorkspaceType(workspaceLanguageSettings.getWorkspaceType());

    for (BlazeSyncPlugin syncPlugin : BlazeSyncPlugin.EP_NAME.getExtensions()) {
      syncPlugin.installSdks(context);
    }

    if (!ProjectViewVerifier.verifyProjectView(
        project, context, workspacePathResolver, projectViewSet, workspaceLanguageSettings)) {
      return SyncResult.FAILURE;
    }

    final BlazeProjectData newBlazeProjectData;

    WorkingSet workingSet =
        FutureUtil.waitForFuture(context, workingSetFuture)
            .timed("WorkingSet", EventType.Other)
            .withProgressMessage("Computing VCS working set...")
            .onError("Could not compute working set")
            .run()
            .result();
    if (context.isCancelled()) {
      return SyncResult.CANCELLED;
    }
    if (context.hasErrors()) {
      return SyncResult.FAILURE;
    }

    if (workingSet != null) {
      printWorkingSet(context, workingSet);
    }

    SyncState.Builder syncStateBuilder = new SyncState.Builder();
    SyncState previousSyncState =
        oldBlazeProjectData != null ? oldBlazeProjectData.syncState : null;

    List<TargetExpression> targets = Lists.newArrayList();
    if (syncParams.addProjectViewTargets) {
      Collection<TargetExpression> projectViewTargets = projectViewSet.listItems(TargetSection.KEY);
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
      return SyncResult.FAILURE;
    }
    ShardedTargetList shardedTargets = shardedTargetsResult.shardedTargets;

    syncStats.setSyncSharded(shardedTargets.shardedTargets.size() > 1);

    BlazeConfigurationHandler configHandler = new BlazeConfigurationHandler(blazeInfo);
    boolean mergeWithOldState = !syncParams.addProjectViewTargets;
    BlazeIdeInterface.IdeResult ideQueryResult =
        getIdeQueryResult(
            project,
            context,
            projectViewSet,
            blazeVersionData,
            configHandler,
            shardedTargets,
            workspaceLanguageSettings,
            artifactLocationDecoder,
            syncStateBuilder,
            previousSyncState,
            mergeWithOldState);
    if (context.isCancelled()) {
      return SyncResult.CANCELLED;
    }
    context.output(PrintOutput.log("ide-query result: " + ideQueryResult.buildResult.status));
    if (ideQueryResult.targetMap == null
        || ideQueryResult.buildResult.status == BuildResult.Status.FATAL_ERROR) {
      context.setHasError();
      if (ideQueryResult.buildResult.outOfMemory()) {
        SuggestBuildShardingNotification.syncOutOfMemoryError(project, context);
      }
      return SyncResult.FAILURE;
    }

    TargetMap targetMap = ideQueryResult.targetMap;
    context.output(
        PrintOutput.log("Target map size: " + ideQueryResult.targetMap.targets().size()));
    BuildResult ideInfoResult = ideQueryResult.buildResult;

    ListenableFuture<ImmutableMultimap<TargetKey, TargetKey>> reverseDependenciesFuture =
        BlazeExecutor.getInstance().submit(() -> ReverseDependencyMap.createRdepsMap(targetMap));

    BuildResult ideResolveResult =
        resolveIdeArtifacts(
            project,
            context,
            workspaceRoot,
            projectViewSet,
            blazeVersionData,
            workspaceLanguageSettings,
            shardedTargets);
    if (ideResolveResult.status == BuildResult.Status.FATAL_ERROR) {
      context.setHasError();
      if (ideResolveResult.outOfMemory()) {
        SuggestBuildShardingNotification.syncOutOfMemoryError(project, context);
      }
      return SyncResult.FAILURE;
    }
    if (context.isCancelled()) {
      return SyncResult.CANCELLED;
    }

    Scope.push(
        context,
        (childContext) -> {
          childContext.push(new TimingScope("UpdateSyncState", EventType.Other));
          for (BlazeSyncPlugin syncPlugin : BlazeSyncPlugin.EP_NAME.getExtensions()) {
            syncPlugin.updateSyncState(
                project,
                childContext,
                workspaceRoot,
                projectViewSet,
                workspaceLanguageSettings,
                blazeInfo,
                blazeVersionData,
                workingSet,
                workspacePathResolver,
                artifactLocationDecoder,
                targetMap,
                syncStateBuilder,
                previousSyncState);
          }
        });

    ImmutableMultimap<TargetKey, TargetKey> reverseDependencies =
        FutureUtil.waitForFuture(context, reverseDependenciesFuture)
            .timed("ReverseDependencies", EventType.Other)
            .onError("Failed to compute reverse dependency map")
            .run()
            .result();
    if (reverseDependencies == null) {
      return SyncResult.FAILURE;
    }

    newBlazeProjectData =
        new BlazeProjectData(
            syncStartTime,
            targetMap,
            blazeInfo,
            blazeVersionData,
            workspacePathResolver,
            artifactLocationDecoder,
            workspaceLanguageSettings,
            syncStateBuilder.build(),
            reverseDependencies);

    FileCaches.onSync(project, context, projectViewSet, newBlazeProjectData, syncParams.syncMode);
    ListenableFuture<?> prefetch =
        PrefetchService.getInstance()
            .prefetchProjectFiles(project, projectViewSet, newBlazeProjectData);
    FutureUtil.waitForFuture(context, prefetch)
        .withProgressMessage("Prefetching files...")
        .timed("PrefetchFiles", EventType.Prefetching)
        .onError("Prefetch failed")
        .run();

    ListenableFuture<DirectoryStructure> directoryStructureFuture =
        DirectoryStructure.getRootDirectoryStructure(project, workspaceRoot, projectViewSet);

    refreshVirtualFileSystem(context, newBlazeProjectData);

    DirectoryStructure directoryStructure =
        FutureUtil.waitForFuture(context, directoryStructureFuture)
            .withProgressMessage("Computing directory structure...")
            .timed("DirectoryStructure", EventType.Other)
            .onError("Directory structure computation failed")
            .run()
            .result();
    if (directoryStructure == null) {
      return SyncResult.FAILURE;
    }

    boolean success =
        updateProject(
            context,
            projectViewSet,
            blazeVersionData,
            directoryStructure,
            oldBlazeProjectData,
            newBlazeProjectData);
    if (!success) {
      return SyncResult.FAILURE;
    }

    SyncResult syncResult = SyncResult.SUCCESS;

    if (ideInfoResult.status == BuildResult.Status.BUILD_ERROR
        || ideResolveResult.status == BuildResult.Status.BUILD_ERROR) {
      final String errorType =
          ideInfoResult.status == BuildResult.Status.BUILD_ERROR
              ? "BUILD file errors"
              : "compilation errors";

      String message =
          String.format(
              "Sync was successful, but there were %s. "
                  + "The project may not be fully updated or resolve until fixed. "
                  + "If the errors are from your working set, please uncheck "
                  + "'Blaze > Sync > Expand Sync to Working Set' and try again.",
              errorType);
      context.output(PrintOutput.error(message));
      IssueOutput.warn(message).submit(context);
      syncResult = SyncResult.PARTIAL_SUCCESS;
    }

    return syncResult;
  }

  private static void refreshVirtualFileSystem(
      BlazeContext context, BlazeProjectData blazeProjectData) {
    Transactions.submitWriteActionTransactionAndWait(
        () ->
            Scope.push(
                context,
                (childContext) -> {
                  childContext.push(new TimingScope("RefreshVirtualFileSystem", EventType.Other));
                  for (BlazeSyncPlugin syncPlugin : BlazeSyncPlugin.EP_NAME.getExtensions()) {
                    syncPlugin.refreshVirtualFileSystem(blazeProjectData);
                  }
                }));
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
        workingSet
            .addedFiles
            .stream()
            .map(file -> file.relativePath() + " (added)")
            .collect(Collectors.toList()));
    messages.addAll(
        workingSet
            .modifiedFiles
            .stream()
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

  private BlazeIdeInterface.IdeResult getIdeQueryResult(
      Project project,
      BlazeContext parentContext,
      ProjectViewSet projectViewSet,
      BlazeVersionData blazeVersionData,
      BlazeConfigurationHandler configHandler,
      ShardedTargetList shardedTargets,
      WorkspaceLanguageSettings workspaceLanguageSettings,
      ArtifactLocationDecoder artifactLocationDecoder,
      Builder syncStateBuilder,
      @Nullable SyncState previousSyncState,
      boolean mergeWithOldState) {

    return Scope.push(
        parentContext,
        context -> {
          context.push(new TimingScope("IdeQuery", EventType.BlazeInvocation));
          context.output(new StatusOutput("Building IDE info files..."));
          context.setPropagatesErrors(false);

          BlazeIdeInterface blazeIdeInterface = BlazeIdeInterface.getInstance();
          return blazeIdeInterface.updateTargetMap(
              project,
              context,
              workspaceRoot,
              projectViewSet,
              blazeVersionData,
              configHandler,
              shardedTargets,
              workspaceLanguageSettings,
              artifactLocationDecoder,
              syncStateBuilder,
              previousSyncState,
              mergeWithOldState);
        });
  }

  private static BuildResult resolveIdeArtifacts(
      Project project,
      BlazeContext parentContext,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      BlazeVersionData blazeVersionData,
      WorkspaceLanguageSettings workspaceLanguageSettings,
      ShardedTargetList shardedTargets) {
    return Scope.push(
        parentContext,
        context -> {
          context.push(
              new TimingScope(Blaze.buildSystemName(project) + "Build", EventType.BlazeInvocation));
          context.output(new StatusOutput("Building IDE resolve files..."));

          // We don't want IDE resolve errors to fail the whole sync
          context.setPropagatesErrors(false);

          if (shardedTargets.isEmpty()) {
            return BuildResult.SUCCESS;
          }
          BlazeIdeInterface blazeIdeInterface = BlazeIdeInterface.getInstance();
          return blazeIdeInterface.resolveIdeArtifacts(
              project,
              context,
              workspaceRoot,
              projectViewSet,
              blazeVersionData,
              workspaceLanguageSettings,
              shardedTargets);
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
                            () ->
                                updateProjectStructure(
                                    context,
                                    importSettings,
                                    projectViewSet,
                                    blazeVersionData,
                                    directoryStructure,
                                    newBlazeProjectData,
                                    oldBlazeProjectData)));
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
              newBlazeProjectData.workspaceLanguageSettings.getWorkspaceType());
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
      BlazeProjectData blazeProjectData) {
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
                          workspaceModule);
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
    long blazeExecTime =
        timedEvents
            .stream()
            .filter(e -> e.isLeafEvent && e.type == EventType.BlazeInvocation)
            .mapToLong(e -> e.durationMillis)
            .sum();
    stats.setBlazeExecTimeMs(blazeExecTime);
    stats.setTimedEvents(timedEvents);

    return stats.build();
  }
}
