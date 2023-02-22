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
import com.google.idea.blaze.base.async.executor.ProgressiveTaskWithProgressIndicator;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper.GetArtifactsException;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.BlazeScope;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.ScopedOperation;
import com.google.idea.blaze.base.scope.scopes.ProblemsViewScope;
import com.google.idea.blaze.base.scope.scopes.ProgressIndicatorScope;
import com.google.idea.blaze.base.scope.scopes.ToolWindowScope;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.settings.BlazeUserSettings.FocusBehavior;
import com.google.idea.blaze.base.sync.SyncListener;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.SyncResult;
import com.google.idea.blaze.base.sync.status.BlazeSyncStatus;
import com.google.idea.blaze.base.toolwindow.Task;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.qsync.BlazeProject;
import com.google.idea.blaze.qsync.BlazeProjectSnapshot;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.serviceContainer.NonInjectable;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;

/** The project component for a query based sync. */
public class QuerySyncManager {

  private final Logger logger = Logger.getInstance(getClass());

  private final Project project;
  private final BlazeProject graph;
  private final DependencyTracker dependencyTracker;
  private final ProjectQuerier projectQuerier;
  private final ProjectUpdater projectUpdater;
  private final DependencyBuilder builder;
  private final DependencyCache cache;

  public static QuerySyncManager getInstance(Project project) {
    return ServiceManager.getService(project, QuerySyncManager.class);
  }

  public QuerySyncManager(Project project) {
    this.project = project;
    this.graph = new BlazeProject();
    this.builder = new BazelBinaryDependencyBuilder(project);
    this.cache = new DependencyCache(project);
    this.dependencyTracker = new DependencyTracker(project, graph, builder, cache);
    this.projectQuerier = ProjectQuerierImpl.create(project);
    this.projectUpdater = new ProjectUpdater(project, graph);
  }

  @VisibleForTesting
  @NonInjectable
  public QuerySyncManager(
      Project project,
      BlazeProject graph,
      DependencyTracker dependencyTracker,
      ProjectQuerier projectQuerier,
      ProjectUpdater projectUpdater,
      DependencyBuilder builder,
      DependencyCache cache) {
    this.project = project;
    this.graph = graph;
    this.dependencyTracker = dependencyTracker;
    this.projectQuerier = projectQuerier;
    this.projectUpdater = projectUpdater;
    this.builder = builder;
    this.cache = cache;
  }

  /** Log & display a message to the user when a user-initiated action fails. */
  private void onError(String description, Exception e, BlazeContext context) {
    logger.error(description, e);
    context.output(PrintOutput.error(description + ": " + e.getClass().getSimpleName()));
    if (e.getMessage() != null) {
      context.output(PrintOutput.error("Cause: " + e.getMessage()));
    }
  }

  public void build(List<WorkspacePath> wps) {
    run(
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

  private void sync(BlazeContext context, boolean full) {
    try {
      BlazeProjectSnapshot newProject =
          full
              ? projectQuerier.fullQuery(context)
              : projectQuerier.update(graph.getCurrent(), context);
      graph.setCurrent(context, newProject);
      for (SyncListener syncListener : SyncListener.EP_NAME.getExtensions()) {
        syncListener.afterSync(project, context);
      }
    } catch (IOException e) {
      onError("Project sync failed", e, context);
    }
  }

  public void initialProjectSync() {
    run("Initiating project sync", "Importing project", context -> sync(context, true));
  }

  public void deltaSync() {
    run("Updating project structure", "Refreshing project", context -> sync(context, false));
  }

  private void run(String title, String subTitle, ScopedOperation operation) {
    BlazeSyncStatus.getInstance(project).syncStarted();
    DumbService.getInstance(project)
        .runWhenSmart(
            () -> {
              Future<Void> unusedFuture =
                  ProgressiveTaskWithProgressIndicator.builder(project, title)
                      .submitTask(
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
                                  }));
            });
  }

  public void build(BlazeContext context, List<WorkspacePath> wps)
      throws IOException, GetArtifactsException {

    dependencyTracker.buildDependenciesForFile(context, wps);
  }

  public DependencyTracker getDependencyTracker() {
    return dependencyTracker;
  }

  public void enableAnalysis(PsiFile psiFile) {
    BlazeImportSettings settings =
        BlazeImportSettingsManager.getInstance(project).getImportSettings();

    Path path = Paths.get(psiFile.getVirtualFile().getPath());
    String rel = Paths.get(settings.getWorkspaceRoot()).relativize(path).toString();
    build(List.of(WorkspacePath.createIfValid(rel)));
  }

  public boolean isReadyForAnalysis(PsiFile psiFile) {
    Set<Label> pendingTargets =
        dependencyTracker.getPendingTargets(project, psiFile.getVirtualFile());
    int unsynced = pendingTargets == null ? 0 : pendingTargets.size();
    return unsynced == 0;
  }
}
