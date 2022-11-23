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

import com.google.idea.blaze.base.async.executor.ProgressiveTaskWithProgressIndicator;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper.GetArtifactsException;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.BlazeScope;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.ScopedOperation;
import com.google.idea.blaze.base.scope.scopes.ProgressIndicatorScope;
import com.google.idea.blaze.base.scope.scopes.ToolWindowScope;
import com.google.idea.blaze.base.settings.BlazeUserSettings.FocusBehavior;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.SyncResult;
import com.google.idea.blaze.base.sync.status.BlazeSyncStatus;
import com.google.idea.blaze.base.toolwindow.Task;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;
import org.jetbrains.annotations.NotNull;

/** The project component for a query based sync. */
public class QuerySyncManager {

  public static final BoolExperiment useQuerySync = new BoolExperiment("use.query.sync", false);

  private final Project project;
  private final BuildGraph graph;
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
    this.graph = new BuildGraph();
    this.builder = new DependencyBuilder();
    this.cache = new DependencyCache(project);
    this.dependencyTracker = new DependencyTracker(project, graph, builder, cache);
    this.projectQuerier = new ProjectQuerier(project, graph);
    this.projectUpdater = new ProjectUpdater(project, graph);

    if (useQuerySync.getValue()) {
      FileEditorManagerListener listener = new MyFileEditorManagerListener(project);
      project
          .getMessageBus()
          .connect()
          .subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, listener);
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
            e.printStackTrace();
          }
        });
  }

  public void initialProjectSync() {
    run(
        "Initiating project sync",
        "Importing project",
        context -> {
          try {
            projectQuerier.rebuild(context);
          } catch (IOException e) {
            e.printStackTrace();
          }
        });
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
                                    context.push(new ProgressIndicatorScope(indicator)).push(scope);
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

  /** A listener for editors opened to decide if analysis should be enabled on them. */
  public class MyFileEditorManagerListener implements FileEditorManagerListener {

    private final Project project;

    public MyFileEditorManagerListener(Project project) {
      this.project = project;
      // TODO there is more work needed for this listener to catch all the files that are
      // already opened when the project is opened. This only catches newly opened files.
    }

    @Override
    public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
      PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
      Set<String> pendingTargets = dependencyTracker.getPendingTargets(project, file);
      int unsynced = pendingTargets == null ? 0 : pendingTargets.size();
      DaemonCodeAnalyzer.getInstance(project).setHighlightingEnabled(psiFile, unsynced == 0);
      DaemonCodeAnalyzer.getInstance(project).restart(psiFile);
    }
  }
}
