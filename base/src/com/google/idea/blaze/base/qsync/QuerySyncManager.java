/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.qsync;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.idea.blaze.base.async.executor.ProgressiveTaskWithProgressIndicator;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper.GetArtifactsException;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.qsync.cache.ArtifactFetcher;
import com.google.idea.blaze.base.qsync.cache.FileApiArtifactFetcher;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.BlazeScope;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.ScopedOperation;
import com.google.idea.blaze.base.scope.scopes.ProblemsViewScope;
import com.google.idea.blaze.base.scope.scopes.ProgressIndicatorScope;
import com.google.idea.blaze.base.scope.scopes.ToolWindowScope;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.settings.BlazeUserSettings.FocusBehavior;
import com.google.idea.blaze.base.settings.BuildBinaryType;
import com.google.idea.blaze.base.sync.SyncListener;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.SyncResult;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.status.BlazeSyncStatus;
import com.google.idea.blaze.base.toolwindow.Task;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.qsync.BlazeProject;
import com.google.idea.blaze.qsync.project.BlazeProjectSnapshot;
import com.google.idea.blaze.qsync.project.PostQuerySyncData;
import com.google.idea.blaze.qsync.project.ProjectDefinition;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.serviceContainer.NonInjectable;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The project component for a query based sync.
 *
 * <p>This class manages sync'ing the intelliJ project state to the state of the Bazel project in
 * the workspace, as well as building dependencies of the project.
 *
 * <p>The sync'd state of a project is represented by {@link BlazeProjectSnapshot}. During the sync
 * process, different parts of that are available at different phases:
 *
 * <ul>
 *   <li>{@link ProjectDefinition}: the input to the sync process that can be created from the
 *       project configuration. This class remained unchanged throughout sync.
 *   <li>{@link PostQuerySyncData}: the state after the query invocation has been made, or after a
 *       delta has been applied to that. This class is the input and output to the partial update
 *       operation, and also contains the data that will be persisted to disk over an IDE restart.
 *   <li>{@link BlazeProjectSnapshot}: the full project state, created in the last phase of sync
 *       from {@link PostQuerySyncData}.
 * </ul>
 */
public class QuerySyncManager {
  private static final ExtensionPointName<ArtifactFetcher> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.qsync.ArtifactFetcher");
  private final Logger logger = Logger.getInstance(getClass());

  private final Project project;
  private final BlazeProject graph;
  private final QuerySyncProjectDataManager projectDataManager;
  private final DependencyTracker dependencyTracker;
  private final ProjectQuerier projectQuerier;
  private final ProjectUpdater projectUpdater;
  private final DependencyBuilder builder;
  private final DependencyCache cache;

  public static QuerySyncManager getInstance(Project project) {
    return ServiceManager.getService(project, QuerySyncManager.class);
  }

  public QuerySyncManager(Project project) {
    ProjectDeps.Builder projectDepsBuilder = ProjectDeps.builder(project);
    this.project = project;
    this.graph = new BlazeProject();
    this.projectDataManager = new QuerySyncProjectDataManager(projectDepsBuilder);
    this.builder = new BazelBinaryDependencyBuilder(project);
    this.cache = new DependencyCache(project, createArtifactFetcher());
    this.dependencyTracker = new DependencyTracker(project, graph, builder, cache);
    this.projectQuerier =
        ProjectQuerierImpl.create(project, projectDepsBuilder.getWorkspaceRoot().path());
    this.projectUpdater = new ProjectUpdater(project, graph);
    graph.addListener(projectDataManager);
  }

  @VisibleForTesting
  @NonInjectable
  public QuerySyncManager(
      Project project,
      BlazeProject graph,
      QuerySyncProjectDataManager projectDataManager,
      DependencyTracker dependencyTracker,
      ProjectQuerier projectQuerier,
      ProjectUpdater projectUpdater,
      DependencyBuilder builder,
      DependencyCache cache) {
    this.project = project;
    this.graph = graph;
    this.projectDataManager = projectDataManager;
    this.dependencyTracker = dependencyTracker;
    this.projectQuerier = projectQuerier;
    this.projectUpdater = projectUpdater;
    this.builder = builder;
    this.cache = cache;
  }

  private ArtifactFetcher createArtifactFetcher() {
    Preconditions.checkState(
        EP_NAME.getExtensions().length <= 1, "There are too many artifact fetchers");
    ArtifactFetcher defaultArtifactFetcher = new FileApiArtifactFetcher();
    BuildBinaryType buildBinaryType =
        Blaze.getBuildSystemProvider(project)
            .getBuildSystem()
            .getDefaultInvoker(project, BlazeContext.create())
            .getType();
    for (ArtifactFetcher artifactFetcher : EP_NAME.getExtensions()) {
      if (artifactFetcher.isEnabled(buildBinaryType)) {
        return artifactFetcher;
      }
    }
    return defaultArtifactFetcher;
  }

  /** Log & display a message to the user when a user-initiated action fails. */
  private void onError(String description, Exception e, BlazeContext context) {
    logger.error(description, e);
    context.output(PrintOutput.error(description + ": " + e.getClass().getSimpleName()));
    if (e.getMessage() != null) {
      context.output(PrintOutput.error("Cause: " + e.getMessage()));
    }
  }

  public BlazeProject getBlazeProject() {
    return graph;
  }

  public DependencyCache getDependencyCache() {
    return cache;
  }

  public QuerySyncProjectDataManager getProjectDataManager() {
    return projectDataManager;
  }

  private ListenableFuture<Boolean> build(List<WorkspacePath> wps) {
    return run(
        "Building dependencies",
        "Building...",
        context -> {
          try {
            build(context, wps);
          } catch (Exception e) {
            onError("Failed to build dependencies " + Joiner.on(' ').join(wps), e, context);
          }
        });
  }

  public void build(BlazeContext context, List<WorkspacePath> wps)
      throws IOException, GetArtifactsException {

    dependencyTracker.buildDependenciesForFile(context, wps);
  }

  private void sync(BlazeContext context, Optional<PostQuerySyncData> lastQuery) {
    try {
      BlazeProjectSnapshot newProject =
          lastQuery.isEmpty()
              ? projectQuerier.fullQuery(projectDataManager.getProjectDefinition(context), context)
              : projectQuerier.update(lastQuery.get(), context);
      graph.setCurrent(context, newProject);
      // TODO: Revisit SyncListeners once we switch fully to qsync
      BlazeImportSettings settings =
          BlazeImportSettingsManager.getInstance(project).getImportSettings();
      ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
      BlazeProjectData data = BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
      for (SyncListener syncListener : SyncListener.EP_NAME.getExtensions()) {
        // A callback shared between the old and query sync implementations.
        syncListener.onSyncComplete(
            project,
            context,
            settings,
            projectViewSet,
            ImmutableSet.of(),
            data,
            SyncMode.FULL,
            SyncResult.SUCCESS);
      }
    } catch (IOException e) {
      onError("Project sync failed", e, context);
    } finally {
      for (SyncListener syncListener : SyncListener.EP_NAME.getExtensions()) {
        // A query sync specific callback.
        syncListener.afterSync(project, context);
      }
    }
  }

  @CanIgnoreReturnValue
  public ListenableFuture<Boolean> onStartup() {
    return run(
        "Loading project",
        "Initializing project structure",
        context -> sync(context, projectDataManager.loadFromDisk(context)));
  }

  @CanIgnoreReturnValue
  public ListenableFuture<Boolean> fullSync() {
    return run(
        "Updating project structure",
        "Re-importing project",
        context -> sync(context, Optional.empty()));
  }

  @CanIgnoreReturnValue
  public ListenableFuture<Boolean> deltaSync() {
    return run(
        "Updating project structure",
        "Refreshing project",
        context -> sync(context, graph.getCurrent().map(BlazeProjectSnapshot::queryData)));
  }

  private ListenableFuture<Boolean> run(String title, String subTitle, ScopedOperation operation) {
    SettableFuture<Boolean> result = SettableFuture.create();
    BlazeSyncStatus.getInstance(project).syncStarted();
    DumbService.getInstance(project)
        .runWhenSmart(
            () -> {
              try {
                ListenableFuture<Boolean> innerResultFuture =
                    createAndSubmitRunTask(title, subTitle, operation);
                result.setFuture(innerResultFuture);
              } catch (Throwable t) {
                result.setException(t);
                throw t;
              }
            });
    return result;
  }

  private ListenableFuture<Boolean> createAndSubmitRunTask(
      String title, String subTitle, ScopedOperation operation) {
    return ProgressiveTaskWithProgressIndicator.builder(project, title)
        .submitTaskWithResult(
            indicator ->
                Scope.root(
                    context -> {
                      Task task = new Task(project, subTitle, Task.Type.SYNC);
                      BlazeScope scope =
                          new ToolWindowScope.Builder(project, task)
                              .setProgressIndicator(indicator)
                              .showSummaryOutput()
                              .setPopupBehavior(FocusBehavior.ALWAYS)
                              .build();
                      context
                          .push(new ProgressIndicatorScope(indicator))
                          .push(scope)
                          .push(new ProblemsViewScope(project, FocusBehavior.ALWAYS));
                      operation.execute(context);
                      // TODO cancel on exceptions
                      BlazeSyncStatus.getInstance(project)
                          .syncEnded(SyncMode.FULL, SyncResult.SUCCESS);
                      return !context.hasErrors();
                    }));
  }

  public DependencyTracker getDependencyTracker() {
    return dependencyTracker;
  }

  @CanIgnoreReturnValue
  public ListenableFuture<Boolean> enableAnalysis(PsiFile psiFile) {
    BlazeImportSettings settings =
        BlazeImportSettingsManager.getInstance(project).getImportSettings();

    Path path = Paths.get(psiFile.getVirtualFile().getPath());
    String rel = Paths.get(settings.getWorkspaceRoot()).relativize(path).toString();
    return build(List.of(WorkspacePath.createIfValid(rel)));
  }

  public boolean isReadyForAnalysis(PsiFile psiFile) {
    Set<Label> pendingTargets =
        dependencyTracker.getPendingTargets(project, psiFile.getVirtualFile());
    int unsynced = pendingTargets == null ? 0 : pendingTargets.size();
    return unsynced == 0;
  }
}
