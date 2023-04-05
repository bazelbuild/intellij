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
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.idea.blaze.base.async.executor.ProgressiveTaskWithProgressIndicator;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.BlazeScope;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.ScopedOperation;
import com.google.idea.blaze.base.scope.scopes.ProblemsViewScope;
import com.google.idea.blaze.base.scope.scopes.ProgressIndicatorScope;
import com.google.idea.blaze.base.scope.scopes.ToolWindowScope;
import com.google.idea.blaze.base.settings.BlazeUserSettings.FocusBehavior;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.SyncResult;
import com.google.idea.blaze.base.sync.status.BlazeSyncStatus;
import com.google.idea.blaze.base.targetmaps.SourceToTargetMap;
import com.google.idea.blaze.base.toolwindow.Task;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.qsync.project.BlazeProjectSnapshot;
import com.google.idea.blaze.qsync.project.PostQuerySyncData;
import com.google.idea.blaze.qsync.project.ProjectDefinition;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.serviceContainer.NonInjectable;
import java.io.IOException;
import java.util.Optional;
import javax.annotation.Nullable;

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
  private final Logger logger = Logger.getInstance(getClass());

  private final Project project;

  private final ProjectLoader loader;
  private volatile QuerySyncProject loadedProject;

  public static QuerySyncManager getInstance(Project project) {
    return ServiceManager.getService(project, QuerySyncManager.class);
  }

  public QuerySyncManager(Project project) {
    this.project = project;
    this.loader = new ProjectLoader(project);
  }

  @VisibleForTesting
  @NonInjectable
  public QuerySyncManager(Project project, ProjectLoader loader) {
    this.project = project;
    this.loader = loader;
  }

  public void loadProject(BlazeContext context) {
    try {
      QuerySyncProject newProject = loader.loadProject(context);
      if (!context.hasErrors()) {
        loadedProject = Preconditions.checkNotNull(newProject);
      }
    } catch (IOException e) {
      onError("Failed to load project", e, context);
    }
  }

  public Optional<QuerySyncProject> getLoadedProject() {
    return Optional.ofNullable(loadedProject);
  }

  private void assertProjectLoaded() {
    if (loadedProject == null) {
      throw new IllegalStateException("Project not loaded yet");
    }
  }

  /** Log & display a message to the user when a user-initiated action fails. */
  void onError(String description, Exception e, BlazeContext context) {
    logger.error(description, e);
    context.output(PrintOutput.error(description + ": " + e.getClass().getSimpleName()));
    context.setHasError();
    if (e.getMessage() != null) {
      context.output(PrintOutput.error("Cause: " + e.getMessage()));
    }
  }

  public DependencyCache getDependencyCache() {
    assertProjectLoaded();
    return loadedProject.getDependencyCache();
  }

  public SourceToTargetMap getSourceToTargetMap() {
    assertProjectLoaded();
    return loadedProject.getSourceToTargetMap();
  }

  @CanIgnoreReturnValue
  public ListenableFuture<Boolean> onStartup() {
    return run("Loading project", "Initializing project structure", this::loadProject);
  }

  @CanIgnoreReturnValue
  public ListenableFuture<Boolean> fullSync() {
    return run("Updating project structure", "Re-importing project", loadedProject::fullSync);
  }

  @CanIgnoreReturnValue
  public ListenableFuture<Boolean> deltaSync() {
    assertProjectLoaded();
    return run("Updating project structure", "Refreshing project", loadedProject::deltaSync);
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

  @Nullable
  public DependencyTracker getDependencyTracker() {
    return loadedProject == null ? null : loadedProject.getDependencyTracker();
  }

  @CanIgnoreReturnValue
  public ListenableFuture<Boolean> enableAnalysis(PsiFile psiFile) {
    assertProjectLoaded();
    return run(
        "Building dependencies",
        "Building...",
        context -> loadedProject.enableAnalysis(context, psiFile));
  }

  public boolean isReadyForAnalysis(PsiFile psiFile) {
    if (loadedProject == null) {
      return false;
    }
    return loadedProject.isReadyForAnalysis(psiFile);
  }
}
