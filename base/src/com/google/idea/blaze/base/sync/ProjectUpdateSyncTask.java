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
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.idea.blaze.base.async.FutureUtil;
import com.google.idea.blaze.base.filecache.FileCaches;
import com.google.idea.blaze.base.filecache.RemoteOutputsCache;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.io.VirtualFileSystemProvider;
import com.google.idea.blaze.base.model.BlazeLibrary;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.BlazeVersionData;
import com.google.idea.blaze.base.model.ProjectTargetData;
import com.google.idea.blaze.base.model.RemoteOutputArtifacts;
import com.google.idea.blaze.base.model.SyncState;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.prefetch.PrefetchService;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.output.PrintOutput;
import com.google.idea.blaze.base.scope.output.StatusOutput;
import com.google.idea.blaze.base.scope.scopes.TimingScope;
import com.google.idea.blaze.base.scope.scopes.TimingScope.EventType;
import com.google.idea.blaze.base.scope.scopes.TimingScopeListener.TimedEvent;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin.ModuleEditor;
import com.google.idea.blaze.base.sync.SyncScope.SyncCanceledException;
import com.google.idea.blaze.base.sync.SyncScope.SyncFailedException;
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs;
import com.google.idea.blaze.base.sync.aspects.BlazeIdeInterface;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManagerImpl;
import com.google.idea.blaze.base.sync.libraries.BlazeLibraryCollector;
import com.google.idea.blaze.base.sync.libraries.LibraryEditor;
import com.google.idea.blaze.base.sync.projectstructure.ContentEntryEditor;
import com.google.idea.blaze.base.sync.projectstructure.DirectoryStructure;
import com.google.idea.blaze.base.sync.projectstructure.ModuleEditorImpl;
import com.google.idea.blaze.base.sync.projectstructure.ModuleEditorProvider;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoderImpl;
import com.google.idea.blaze.base.util.SaveUtil;
import com.google.idea.common.experiments.BoolExperiment;
import com.google.idea.common.transactions.Transactions;
import com.google.idea.sdkcompat.openapi.SaveFromInsideWriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.progress.ProcessCanceledException;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;

/** Runs the 'project update' phase of sync, after the blaze build phase has completed. */
final class ProjectUpdateSyncTask {

  private static final Logger logger = Logger.getInstance(ProjectUpdateSyncTask.class);

  private static final BoolExperiment saveStateDuringRootsChange =
      new BoolExperiment("blaze.save.state.during.roots.change", true);

  /** Runs the project update phase of sync, returning timing information for logging purposes. */
  static List<TimedEvent> runProjectUpdatePhase(
      Project project,
      BlazeSyncParams params,
      SyncProjectState projectState,
      BlazeSyncBuildResult buildPhaseResult,
      BlazeContext context) {
    if (!buildPhaseResult.isValid()) {
      return ImmutableList.of();
    }
    SaveUtil.saveAllFiles();
    ProjectUpdateSyncTask task =
        new ProjectUpdateSyncTask(project, params, projectState, buildPhaseResult.getBuildResult());
    return task.runWithTiming(context);
  }

  private final Project project;
  private final BlazeImportSettings importSettings;
  private final WorkspaceRoot workspaceRoot;
  private final BlazeSyncParams syncParams;
  private final SyncProjectState projectState;
  private final BlazeBuildOutputs buildResult;
  @Nullable private final BlazeProjectData oldProjectData;

  private ProjectUpdateSyncTask(
      Project project,
      BlazeSyncParams params,
      SyncProjectState projectState,
      BlazeBuildOutputs buildResult) {
    this.project = project;
    this.importSettings = BlazeImportSettingsManager.getInstance(project).getImportSettings();
    this.workspaceRoot = WorkspaceRoot.fromImportSettings(importSettings);
    this.projectState = projectState;
    this.syncParams = params;
    this.buildResult = buildResult;
    this.oldProjectData =
        syncParams.syncMode == SyncMode.FULL
            ? null
            : BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
  }

  private List<TimedEvent> runWithTiming(BlazeContext context) {
    // run under a child context to capture all timing information before finalizing the stats
    List<TimedEvent> timedEvents = new ArrayList<>();
    SyncScope.push(
        context,
        childContext -> {
          TimingScope timingScope = new TimingScope("Project update phase", EventType.Other);
          timingScope.addScopeListener((events, totalTime) -> timedEvents.addAll(events));
          childContext.push(timingScope);
          run(childContext);
        });
    return timedEvents;
  }

  private void run(BlazeContext context) throws SyncCanceledException, SyncFailedException {
    SyncState.Builder syncStateBuilder = new SyncState.Builder();

    ProjectTargetData targetData = updateTargetMap(context, oldProjectData);
    TargetMap targetMap = targetData.getTargetMap();
    if (targetMap == null) {
      context.setHasError();
      throw new SyncFailedException();
    }
    if (targetData.getIdeInterfaceState() != null) {
      syncStateBuilder.put(targetData.getIdeInterfaceState());
    }
    syncStateBuilder.put(targetData.getRemoteOutputs());
    context.output(PrintOutput.log("Target map size: " + targetMap.targets().size()));

    RemoteOutputArtifacts oldRemoteState = RemoteOutputArtifacts.fromProjectData(oldProjectData);
    RemoteOutputArtifacts newRemoteState = targetData.getRemoteOutputs();

    ArtifactLocationDecoder artifactLocationDecoder =
        new ArtifactLocationDecoderImpl(
            projectState.getBlazeInfo(), projectState.getWorkspacePathResolver(), newRemoteState);

    Scope.push(
        context,
        childContext -> {
          childContext.push(new TimingScope("UpdateRemoteOutputsCache", EventType.Prefetching));
          RemoteOutputsCache.getInstance(project)
              .updateCache(
                  context,
                  targetMap,
                  projectState.getLanguageSettings(),
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
                projectState.getProjectViewSet(),
                projectState.getLanguageSettings(),
                projectState.getBlazeVersionData(),
                projectState.getWorkingSet(),
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
            projectState.getBlazeInfo(),
            projectState.getBlazeVersionData(),
            projectState.getWorkspacePathResolver(),
            artifactLocationDecoder,
            projectState.getLanguageSettings(),
            syncStateBuilder.build());

    FileCaches.onSync(
        project,
        context,
        projectState.getProjectViewSet(),
        newProjectData,
        oldProjectData,
        syncParams.syncMode);
    ListenableFuture<?> prefetch =
        PrefetchService.getInstance()
            .prefetchProjectFiles(project, projectState.getProjectViewSet(), newProjectData);
    FutureUtil.waitForFuture(context, prefetch)
        .withProgressMessage("Prefetching files...")
        .timed("PrefetchFiles", EventType.Prefetching)
        .onError("Prefetch failed")
        .run();

    ListenableFuture<DirectoryStructure> directoryStructureFuture =
        DirectoryStructure.getRootDirectoryStructure(
            project, workspaceRoot, projectState.getProjectViewSet());

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
            projectState.getProjectViewSet(),
            projectState.getBlazeVersionData(),
            directoryStructure,
            oldProjectData,
            newProjectData);
    if (!success) {
      throw new SyncFailedException();
    }
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

  private ProjectTargetData updateTargetMap(
      BlazeContext parentContext, @Nullable BlazeProjectData oldProjectData) {
    boolean mergeWithOldState = !syncParams.addProjectViewTargets;
    return Scope.push(
        parentContext,
        context -> {
          context.push(new TimingScope("ReadBuildOutputs", EventType.BlazeInvocation));
          context.output(new StatusOutput("Parsing build outputs..."));
          BlazeIdeInterface blazeIdeInterface = BlazeIdeInterface.getInstance();
          return blazeIdeInterface.updateTargetData(
              project,
              context,
              workspaceRoot,
              projectState,
              buildResult,
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
}
