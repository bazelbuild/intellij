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

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.async.executor.ProgressiveTaskWithProgressIndicator;
import com.google.idea.blaze.base.command.BlazeInvocationContext.ContextType;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.issueparser.BlazeIssueParser;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.BlazeScope;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.output.PrintOutput;
import com.google.idea.blaze.base.scope.output.PrintOutput.OutputType;
import com.google.idea.blaze.base.scope.scopes.ProgressIndicatorScope;
import com.google.idea.blaze.base.scope.scopes.ToolWindowScope;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.google.idea.blaze.base.settings.BlazeUserSettings.FocusBehavior;
import com.google.idea.blaze.base.sync.projectview.SyncDirectoriesWarning;
import com.google.idea.blaze.base.sync.status.BlazeSyncStatus;
import com.google.idea.blaze.base.toolwindow.Task;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Predicate;

/** Manages syncing and its listeners. */
public class BlazeSyncManager {

  private final Project project;

  public BlazeSyncManager(Project project) {
    this.project = project;
  }

  public static BlazeSyncManager getInstance(Project project) {
    return ServiceManager.getService(project, BlazeSyncManager.class);
  }

  /** Requests a project sync with Blaze. */
  public void requestProjectSync(BlazeSyncParams syncParams) {
    if (syncParams.syncMode() == SyncMode.NO_BUILD
        && !syncParams.backgroundSync()
        && !SyncDirectoriesWarning.warn(project)) {
      return;
    }

    if (BlazeImportSettingsManager.getInstance(project).getImportSettings() == null) {
      throw new IllegalStateException(
          String.format("Attempt to sync non-%s project.", Blaze.buildSystemName(project)));
    }

    // an additional call to 'sync started'. This disables the sync actions while we wait for
    // 'runWhenSmart'
    BlazeSyncStatus.getInstance(project).syncStarted();
    DumbService.getInstance(project)
        .runWhenSmart(
            () -> {
              Future<Void> unusedFuture =
                  ProgressiveTaskWithProgressIndicator.builder(project, "Initiating project sync")
                      .submitTask(
                          indicator ->
                              Scope.root(
                                  context -> {
                                    context
                                        .push(new ProgressIndicatorScope(indicator))
                                        .push(buildToolWindowScope(syncParams, indicator));

                                    if (!runInitialDirectoryOnlySync(syncParams)) {
                                      executeTask(project, syncParams, context);
                                      return;
                                    }

                                    BlazeSyncParams initialUpdateSyncParams =
                                        BlazeSyncParams.builder()
                                            .setTitle("Initial directory update")
                                            .setSyncMode(SyncMode.NO_BUILD)
                                            .setSyncOrigin(syncParams.syncOrigin())
                                            .setBackgroundSync(true)
                                            .build();
                                    executeTask(project, initialUpdateSyncParams, context);

                                    if (!context.isCancelled()) {
                                      executeTask(project, syncParams, context);
                                    }
                                  }));
            });
  }

  private static void executeTask(Project project, BlazeSyncParams params, BlazeContext context) {
    try {
      SyncPhaseCoordinator.getInstance(project).syncProject(params, context).get();
    } catch (InterruptedException e) {
      context.output(new PrintOutput("Sync interrupted: " + e.getMessage()));
      context.setCancelled();
    } catch (ExecutionException e) {
      context.output(new PrintOutput(e.getMessage(), OutputType.ERROR));
      context.setHasError();
    }
  }

  private Task getRootInvocationTask(BlazeSyncParams params) {
    String taskTitle;
    if (params.syncMode() == SyncMode.STARTUP) {
      taskTitle = "Startup Sync";
    } else if (params.syncOrigin().equals(BlazeSyncStartupActivity.SYNC_REASON)) {
      taskTitle = "Importing " + project.getName();
    } else if (params.syncMode() == SyncMode.PARTIAL) {
      taskTitle = "Partial Sync";
    } else {
      taskTitle = "Incremental Sync";
    }
    return new Task(taskTitle, Task.Type.SYNC);
  }

  private BlazeScope buildToolWindowScope(BlazeSyncParams syncParams, ProgressIndicator indicator) {
    BlazeUserSettings userSettings = BlazeUserSettings.getInstance();
    return new ToolWindowScope.Builder(project, getRootInvocationTask(syncParams))
        .setProgressIndicator(indicator)
        .setPopupBehavior(
            syncParams.backgroundSync()
                ? FocusBehavior.NEVER
                : userSettings.getShowBlazeConsoleOnSync())
        .setIssueParsers(
            BlazeIssueParser.defaultIssueParsers(
                project, WorkspaceRoot.fromProject(project), ContextType.Sync))
        .build();
  }

  private static boolean runInitialDirectoryOnlySync(BlazeSyncParams syncParams) {
    switch (syncParams.syncMode()) {
      case NO_BUILD:
      case STARTUP:
        return false;
      case FULL:
        return true;
      case INCREMENTAL:
      case PARTIAL:
        return !syncParams.backgroundSync();
    }
    throw new AssertionError("Unhandled syncMode: " + syncParams.syncMode());
  }

  /**
   * Runs a non-incremental full project sync, clearing the previous project data.
   *
   * @param reason a description of what triggered this sync
   */
  public void fullProjectSync(String reason) {
    BlazeSyncParams syncParams =
        BlazeSyncParams.builder()
            .setTitle("Full Sync")
            .setSyncMode(SyncMode.FULL)
            .setSyncOrigin(reason)
            .setAddProjectViewTargets(true)
            .setAddWorkingSet(BlazeUserSettings.getInstance().getExpandSyncToWorkingSet())
            .build();
    requestProjectSync(syncParams);
  }

  /**
   * Syncs the entire project.
   *
   * @param reason a description of what triggered this sync
   */
  public void incrementalProjectSync(String reason) {
    BlazeSyncParams syncParams =
        BlazeSyncParams.builder()
            .setTitle("Sync")
            .setSyncMode(SyncMode.INCREMENTAL)
            .setSyncOrigin(reason)
            .setAddProjectViewTargets(true)
            .setAddWorkingSet(BlazeUserSettings.getInstance().getExpandSyncToWorkingSet())
            .build();
    requestProjectSync(syncParams);
  }

  /**
   * Runs a partial sync of the given targets.
   *
   * @param reason a description of what triggered this sync
   */
  public void partialSync(Collection<? extends TargetExpression> targetExpressions, String reason) {
    partialSync(targetExpressions, ImmutableList.of(), reason);
  }

  /**
   * Runs a partial sync of the given targets and source files. During sync, a query will be run to
   * convert the source files to the targets building them.
   *
   * @param reason a description of what triggered this sync
   */
  public void partialSync(
      Collection<? extends TargetExpression> targetExpressions,
      Collection<WorkspacePath> sources,
      String reason) {
    BlazeSyncParams syncParams =
        BlazeSyncParams.builder()
            .setTitle("Partial Sync")
            .setSyncMode(SyncMode.PARTIAL)
            .setSyncOrigin(reason)
            .addTargetExpressions(targetExpressions)
            .addSourceFilesToSync(sources)
            .build();
    requestProjectSync(syncParams);
  }

  /**
   * Filters the project targets as part of a coherent sync process, updating derived project data
   * and sending notifications accordingly.
   *
   * @param reason a description of what triggered this action
   */
  public void filterProjectTargets(Predicate<TargetKey> filter, String reason) {
    StartupManager.getInstance(project)
        .runWhenProjectIsInitialized(
            () -> SyncPhaseCoordinator.getInstance(project).filterProjectTargets(filter, reason));
  }

  /**
   * Runs a directory-only sync, without any 'blaze build' operations.
   *
   * @param inBackground run in the background, suppressing the normal 'no targets will be build'
   *     warning.
   * @param reason a description of what triggered this sync
   */
  public void directoryUpdate(boolean inBackground, String reason) {
    BlazeSyncParams syncParams =
        BlazeSyncParams.builder()
            .setTitle("Update Directories")
            .setSyncMode(SyncMode.NO_BUILD)
            .setSyncOrigin(reason)
            .setBackgroundSync(inBackground)
            .build();
    requestProjectSync(syncParams);
  }

  /**
   * Runs a sync of the 'working set' (the locally modified files).
   *
   * @param reason a description of what triggered this sync
   */
  public void workingSetSync(String reason) {
    BlazeSyncParams syncParams =
        BlazeSyncParams.builder()
            .setTitle("Sync Working Set")
            .setSyncMode(SyncMode.PARTIAL)
            .setSyncOrigin(reason)
            .setAddWorkingSet(true)
            .build();
    requestProjectSync(syncParams);
  }
}
