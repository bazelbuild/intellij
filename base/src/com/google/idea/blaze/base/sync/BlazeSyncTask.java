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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.idea.blaze.base.async.FutureUtil;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.experiments.ExperimentScope;
import com.google.idea.blaze.base.filecache.FileCaches;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.io.FileAttributeProvider;
import com.google.idea.blaze.base.io.VirtualFileSystemProvider;
import com.google.idea.blaze.base.model.BlazeLibrary;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.BlazeVersionData;
import com.google.idea.blaze.base.model.SyncState;
import com.google.idea.blaze.base.model.SyncState.Builder;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
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
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.google.idea.blaze.base.sync.BlazeSyncParams.SyncMode;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin.ModuleEditor;
import com.google.idea.blaze.base.sync.SyncListener.SyncResult;
import com.google.idea.blaze.base.sync.aspects.BlazeIdeInterface;
import com.google.idea.blaze.base.sync.aspects.BlazeIdeInterface.BuildResult;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManagerImpl;
import com.google.idea.blaze.base.sync.libraries.BlazeLibraryCollector;
import com.google.idea.blaze.base.sync.libraries.LibraryEditor;
import com.google.idea.blaze.base.sync.projectstructure.ContentEntryEditor;
import com.google.idea.blaze.base.sync.projectstructure.ModuleEditorImpl;
import com.google.idea.blaze.base.sync.projectstructure.ModuleEditorProvider;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.base.sync.projectview.LanguageSupport;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoderImpl;
import com.google.idea.blaze.base.sync.workspace.BlazeRoots;
import com.google.idea.blaze.base.sync.workspace.WorkingSet;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverImpl;
import com.google.idea.blaze.base.targetmaps.ReverseDependencyMap;
import com.google.idea.blaze.base.util.SaveUtil;
import com.google.idea.blaze.base.vcs.BlazeVcsHandler;
import com.google.idea.sdkcompat.transactions.Transactions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Progressive;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFileManager;
import java.io.File;
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
  private final BlazeSyncParams syncParams;
  private final boolean showPerformanceWarnings;

  BlazeSyncTask(
      Project project, BlazeImportSettings importSettings, final BlazeSyncParams syncParams) {
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
          context.push(new ProgressIndicatorScope(indicator)).push(new TimingScope("Sync"));

          if (!syncParams.backgroundSync) {
            context
                .push(new BlazeConsoleScope.Builder(project, indicator).build())
                .push(new IssuesScope(project))
                .push(new IdeaLogScope())
                .push(
                    new NotificationScope(
                        project, "Sync", "Sync project", "Sync successful", "Sync failed"));
          }

          context.output(new StatusOutput("Syncing project..."));
          syncProject(context);
        });
  }

  /** Returns true if sync successfully completed */
  @VisibleForTesting
  boolean syncProject(BlazeContext context) {
    SyncResult syncResult = SyncResult.FAILURE;
    SyncMode syncMode = syncParams.syncMode;
    try {
      SaveUtil.saveAllFiles();
      BlazeProjectData oldBlazeProjectData =
          syncMode != SyncMode.FULL
              ? BlazeProjectDataManagerImpl.getImpl(project)
                  .loadProjectRoot(context, importSettings)
              : null;
      if (oldBlazeProjectData == null) {
        syncMode = SyncMode.FULL;
      }

      onSyncStart(project, context, syncMode);
      if (syncMode != SyncMode.STARTUP) {
        syncResult = doSyncProject(context, syncMode, oldBlazeProjectData);
      } else {
        syncResult = SyncResult.SUCCESS;
      }
      if (syncResult.successful()) {
        ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
        BlazeProjectData blazeProjectData =
            BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
        updateInMemoryState(project, context, projectViewSet, blazeProjectData);
        onSyncComplete(project, context, projectViewSet, blazeProjectData, syncMode, syncResult);
      }
    } catch (AssertionError | Exception e) {
      logger.error(e);
      IssueOutput.error("Internal error: " + e.getMessage()).submit(context);
    } finally {
      afterSync(project, context, syncMode, syncResult);
    }
    return syncResult == SyncResult.SUCCESS || syncResult == SyncResult.PARTIAL_SUCCESS;
  }

  /** @return true if sync successfully completed */
  private SyncResult doSyncProject(
      BlazeContext context, SyncMode syncMode, @Nullable BlazeProjectData oldBlazeProjectData) {
    long syncStartTime = System.currentTimeMillis();

    if (!FileAttributeProvider.getInstance().exists(workspaceRoot.directory())) {
      IssueOutput.error(String.format("Workspace '%s' doesn't exist.", workspaceRoot.directory()))
          .submit(context);
      return SyncResult.FAILURE;
    }

    BlazeVcsHandler vcsHandler = BlazeVcsHandler.vcsHandlerForProject(project);
    if (vcsHandler == null) {
      IssueOutput.error("Could not find a VCS handler").submit(context);
      return SyncResult.FAILURE;
    }

    ListenableFuture<ImmutableMap<String, String>> blazeInfoFuture =
        BlazeInfo.getInstance()
            .runBlazeInfo(
                context, importSettings.getBuildSystem(), workspaceRoot, ImmutableList.of());

    ListeningExecutorService executor = BlazeExecutor.getInstance().getExecutor();
    ListenableFuture<WorkingSet> workingSetFuture =
        vcsHandler.getWorkingSet(project, context, workspaceRoot, executor);

    ImmutableMap<String, String> blazeInfo =
        FutureUtil.waitForFuture(context, blazeInfoFuture)
            .timed(Blaze.buildSystemName(project) + "Info")
            .withProgressMessage(
                String.format("Running %s info...", Blaze.buildSystemName(project)))
            .onError(String.format("Could not run %s info", Blaze.buildSystemName(project)))
            .run()
            .result();
    if (blazeInfo == null) {
      return SyncResult.FAILURE;
    }
    BlazeRoots blazeRoots =
        BlazeRoots.build(importSettings.getBuildSystem(), workspaceRoot, blazeInfo);
    BlazeVersionData blazeVersionData =
        BlazeVersionData.build(importSettings.getBuildSystem(), workspaceRoot, blazeInfo);

    WorkspacePathResolverAndProjectView workspacePathResolverAndProjectView =
        computeWorkspacePathResolverAndProjectView(context, blazeRoots, vcsHandler, executor);
    if (workspacePathResolverAndProjectView == null) {
      return SyncResult.FAILURE;
    }
    WorkspacePathResolver workspacePathResolver =
        workspacePathResolverAndProjectView.workspacePathResolver;
    ArtifactLocationDecoder artifactLocationDecoder =
        new ArtifactLocationDecoderImpl(blazeRoots, workspacePathResolver);
    ProjectViewSet projectViewSet = workspacePathResolverAndProjectView.projectViewSet;

    WorkspaceLanguageSettings workspaceLanguageSettings =
        LanguageSupport.createWorkspaceLanguageSettings(context, projectViewSet);
    if (workspaceLanguageSettings == null) {
      return SyncResult.FAILURE;
    }

    for (BlazeSyncPlugin syncPlugin : BlazeSyncPlugin.EP_NAME.getExtensions()) {
      syncPlugin.installSdks(context);
    }

    if (!ProjectViewVerifier.verifyProjectView(
        context, workspacePathResolver, projectViewSet, workspaceLanguageSettings)) {
      return SyncResult.FAILURE;
    }

    final BlazeProjectData newBlazeProjectData;

    WorkingSet workingSet =
        FutureUtil.waitForFuture(context, workingSetFuture)
            .timed("WorkingSet")
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
    if (syncParams.addProjectViewTargets || oldBlazeProjectData == null) {
      Collection<TargetExpression> projectViewTargets = projectViewSet.listItems(TargetSection.KEY);
      if (!projectViewTargets.isEmpty()) {
        targets.addAll(projectViewTargets);
        printTargets(context, "project view", projectViewTargets);
      }
    }
    if (syncParams.addWorkingSet && workingSet != null) {
      Collection<? extends TargetExpression> workingSetTargets =
          getWorkingSetTargets(projectViewSet, workingSet);
      if (!workingSetTargets.isEmpty()) {
        targets.addAll(workingSetTargets);
        printTargets(context, "working set", workingSetTargets);
      }
    }
    if (!syncParams.targetExpressions.isEmpty()) {
      targets.addAll(syncParams.targetExpressions);
      printTargets(context, syncParams.title, syncParams.targetExpressions);
    }

    boolean mergeWithOldState = !syncParams.addProjectViewTargets;
    BlazeIdeInterface.IdeResult ideQueryResult =
        getIdeQueryResult(
            project,
            context,
            projectViewSet,
            blazeVersionData,
            targets,
            workspaceLanguageSettings,
            artifactLocationDecoder,
            syncStateBuilder,
            previousSyncState,
            mergeWithOldState);
    if (context.isCancelled()) {
      return SyncResult.CANCELLED;
    }
    if (ideQueryResult.targetMap == null || ideQueryResult.buildResult == BuildResult.FATAL_ERROR) {
      context.setHasError();
      return SyncResult.FAILURE;
    }

    TargetMap targetMap = ideQueryResult.targetMap;
    BuildResult ideInfoResult = ideQueryResult.buildResult;

    ListenableFuture<ImmutableMultimap<TargetKey, TargetKey>> reverseDependenciesFuture =
        BlazeExecutor.getInstance().submit(() -> ReverseDependencyMap.createRdepsMap(targetMap));

    BuildResult ideResolveResult =
        resolveIdeArtifacts(
            project, context, workspaceRoot, projectViewSet, blazeVersionData, targets);
    if (ideResolveResult == BuildResult.FATAL_ERROR) {
      context.setHasError();
      return SyncResult.FAILURE;
    }
    if (context.isCancelled()) {
      return SyncResult.CANCELLED;
    }

    Scope.push(
        context,
        (childContext) -> {
          childContext.push(new TimingScope("UpdateSyncState"));
          for (BlazeSyncPlugin syncPlugin : BlazeSyncPlugin.EP_NAME.getExtensions()) {
            syncPlugin.updateSyncState(
                project,
                childContext,
                workspaceRoot,
                projectViewSet,
                workspaceLanguageSettings,
                blazeRoots,
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
            .timed("ReverseDependencies")
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
            blazeRoots,
            blazeVersionData,
            workspacePathResolver,
            artifactLocationDecoder,
            workspaceLanguageSettings,
            syncStateBuilder.build(),
            reverseDependencies,
            null);

    FileCaches.onSync(project, context, projectViewSet, newBlazeProjectData, syncMode);
    ListenableFuture<?> prefetch =
        PrefetchService.getInstance().prefetchProjectFiles(project, newBlazeProjectData);
    FutureUtil.waitForFuture(context, prefetch)
        .withProgressMessage("Prefetching files...")
        .timed("PrefetchFiles")
        .onError("Prefetch failed")
        .run();

    refreshVirtualFileSystem(context, newBlazeProjectData);

    boolean success =
        updateProject(
            context, projectViewSet, blazeVersionData, oldBlazeProjectData, newBlazeProjectData);
    if (!success) {
      return SyncResult.FAILURE;
    }

    SyncResult syncResult = SyncResult.SUCCESS;

    if (ideInfoResult == BuildResult.BUILD_ERROR || ideResolveResult == BuildResult.BUILD_ERROR) {
      final String errorType =
          ideInfoResult == BuildResult.BUILD_ERROR ? "BUILD file errors" : "compilation errors";

      String message =
          String.format(
              "Sync was successful, but there were %s. "
                  + "The project may not be fully updated or resolve until fixed. "
                  + "If the errors are from your working set, please uncheck "
                  + "'Blaze > Expand Sync to Working Set' and try again.",
              errorType);
      context.output(PrintOutput.error(message));
      IssueOutput.warn(message).submit(context);
      syncResult = SyncResult.PARTIAL_SUCCESS;
    }

    return syncResult;
  }

  private static void refreshVirtualFileSystem(
      BlazeContext context, BlazeProjectData blazeProjectData) {
    Transactions.submitTransactionAndWait(
        () ->
            ApplicationManager.getApplication()
                .runWriteAction(
                    (Runnable)
                        () ->
                            Scope.push(
                                context,
                                (childContext) -> {
                                  childContext.push(new TimingScope("RefreshVirtualFileSystem"));
                                  for (BlazeSyncPlugin syncPlugin :
                                      BlazeSyncPlugin.EP_NAME.getExtensions()) {
                                    syncPlugin.refreshVirtualFileSystem(blazeProjectData);
                                  }
                                })));
  }

  static class WorkspacePathResolverAndProjectView {
    final WorkspacePathResolver workspacePathResolver;
    final ProjectViewSet projectViewSet;

    public WorkspacePathResolverAndProjectView(
        WorkspacePathResolver workspacePathResolver, ProjectViewSet projectViewSet) {
      this.workspacePathResolver = workspacePathResolver;
      this.projectViewSet = projectViewSet;
    }
  }

  private WorkspacePathResolverAndProjectView computeWorkspacePathResolverAndProjectView(
      BlazeContext context,
      BlazeRoots blazeRoots,
      BlazeVcsHandler vcsHandler,
      ListeningExecutorService executor) {
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
                  childContext.push(new TimingScope("UpdateVcs"));
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
              : new WorkspacePathResolverImpl(workspaceRoot, blazeRoots);

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
      List<TargetExpression> targets,
      WorkspaceLanguageSettings workspaceLanguageSettings,
      ArtifactLocationDecoder artifactLocationDecoder,
      Builder syncStateBuilder,
      @Nullable SyncState previousSyncState,
      boolean mergeWithOldState) {

    return Scope.push(
        parentContext,
        context -> {
          context.push(new TimingScope("IdeQuery"));
          context.output(new StatusOutput("Building IDE info files..."));
          context.setPropagatesErrors(false);

          BlazeIdeInterface blazeIdeInterface = BlazeIdeInterface.getInstance();
          return blazeIdeInterface.updateTargetMap(
              project,
              context,
              workspaceRoot,
              projectViewSet,
              blazeVersionData,
              targets,
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
      List<TargetExpression> targetExpressions) {
    return Scope.push(
        parentContext,
        context -> {
          context.push(new TimingScope(Blaze.buildSystemName(project) + "Build"));
          context.output(new StatusOutput("Building IDE resolve files..."));

          // We don't want IDE resolve errors to fail the whole sync
          context.setPropagatesErrors(false);

          if (targetExpressions.isEmpty()) {
            return BuildResult.SUCCESS;
          }
          BlazeIdeInterface blazeIdeInterface = BlazeIdeInterface.getInstance();
          return blazeIdeInterface.resolveIdeArtifacts(
              project, context, workspaceRoot, projectViewSet, blazeVersionData, targetExpressions);
        });
  }

  private boolean updateProject(
      BlazeContext parentContext,
      ProjectViewSet projectViewSet,
      BlazeVersionData blazeVersionData,
      @Nullable BlazeProjectData oldBlazeProjectData,
      BlazeProjectData newBlazeProjectData) {
    return Scope.push(
        parentContext,
        context -> {
          context.push(new TimingScope("UpdateProjectStructure"));
          context.output(new StatusOutput("Committing project structure..."));

          try {
            Transactions.submitTransactionAndWait(
                () ->
                    ApplicationManager.getApplication()
                        .runWriteAction(
                            (Runnable)
                                () ->
                                    ProjectRootManagerEx.getInstanceEx(this.project)
                                        .mergeRootsChangesDuring(
                                            () -> {
                                              updateProjectSdk(
                                                  context,
                                                  projectViewSet,
                                                  blazeVersionData,
                                                  newBlazeProjectData);
                                              updateProjectStructure(
                                                  context,
                                                  importSettings,
                                                  projectViewSet,
                                                  newBlazeProjectData,
                                                  oldBlazeProjectData);
                                            })));
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

  private void updateProjectSdk(
      BlazeContext context,
      ProjectViewSet projectViewSet,
      BlazeVersionData blazeVersionData,
      BlazeProjectData newBlazeProjectData) {
    for (BlazeSyncPlugin syncPlugin : BlazeSyncPlugin.EP_NAME.getExtensions()) {
      syncPlugin.updateProjectSdk(
          project, context, projectViewSet, blazeVersionData, newBlazeProjectData);
    }
  }

  private void updateProjectStructure(
      BlazeContext context,
      BlazeImportSettings importSettings,
      ProjectViewSet projectViewSet,
      BlazeProjectData newBlazeProjectData,
      @Nullable BlazeProjectData oldBlazeProjectData) {

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
        project, workspaceRoot, projectViewSet, newBlazeProjectData, workspaceModifiableModel);

    List<BlazeLibrary> libraries = BlazeLibraryCollector.getLibraries(newBlazeProjectData);
    LibraryEditor.updateProjectLibraries(project, context, newBlazeProjectData, libraries);
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
          context.push(new TimingScope("UpdateInMemoryState"));
          context.output(new StatusOutput("Updating in-memory state..."));
          ApplicationManager.getApplication()
              .runReadAction(
                  () -> {
                    Module workspaceModule =
                        ModuleManager.getInstance(project)
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

  private static void afterSync(
      Project project, BlazeContext context, SyncMode syncMode, SyncResult syncResult) {
    final SyncListener[] syncListeners = SyncListener.EP_NAME.getExtensions();
    for (SyncListener syncListener : syncListeners) {
      syncListener.afterSync(project, context, syncMode, syncResult);
    }
  }

  private void onSyncComplete(
      Project project,
      BlazeContext context,
      ProjectViewSet projectViewSet,
      BlazeProjectData blazeProjectData,
      SyncMode syncMode,
      SyncResult syncResult) {
    validate(project, context, blazeProjectData);

    final SyncListener[] syncListeners = SyncListener.EP_NAME.getExtensions();
    for (SyncListener syncListener : syncListeners) {
      syncListener.onSyncComplete(
          project, context, importSettings, projectViewSet, blazeProjectData, syncMode, syncResult);
    }
  }

  private static void validate(
      Project project, BlazeContext context, BlazeProjectData blazeProjectData) {
    for (BlazeSyncPlugin syncPlugin : BlazeSyncPlugin.EP_NAME.getExtensions()) {
      syncPlugin.validate(project, context, blazeProjectData);
    }
  }
}
