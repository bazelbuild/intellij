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

import static java.util.stream.Collectors.joining;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.command.BlazeInvocationContext.ContextType;
import com.google.idea.blaze.base.experiments.ExperimentScope;
import com.google.idea.blaze.base.issueparser.IssueOutputFilter;
import com.google.idea.blaze.base.logging.EventLoggingService;
import com.google.idea.blaze.base.logging.utils.SyncStats;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.output.PrintOutput;
import com.google.idea.blaze.base.scope.output.StatusOutput;
import com.google.idea.blaze.base.scope.scopes.BlazeConsoleScope;
import com.google.idea.blaze.base.scope.scopes.IdeaLogScope;
import com.google.idea.blaze.base.scope.scopes.NotificationScope;
import com.google.idea.blaze.base.scope.scopes.PerformanceWarningScope;
import com.google.idea.blaze.base.scope.scopes.ProblemsViewScope;
import com.google.idea.blaze.base.scope.scopes.ProgressIndicatorScope;
import com.google.idea.blaze.base.scope.scopes.TimingScope;
import com.google.idea.blaze.base.scope.scopes.TimingScope.EventType;
import com.google.idea.blaze.base.scope.scopes.TimingScopeListener.TimedEvent;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.google.idea.blaze.base.settings.BlazeUserSettings.FocusBehavior;
import com.google.idea.blaze.base.sync.aspects.BuildResult;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManagerImpl;
import com.google.idea.blaze.base.sync.projectstructure.ModuleFinder;
import com.google.idea.blaze.base.util.SaveUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Progressive;
import com.intellij.openapi.project.Project;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/** Syncs the project with blaze. */
final class BlazeSyncTask implements Progressive {

  private static final Logger logger = Logger.getInstance(BlazeSyncTask.class);

  private final Project project;
  private final BlazeImportSettings importSettings;
  private final WorkspaceRoot workspaceRoot;
  private final boolean showPerformanceWarnings;

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
        context -> {
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
                  new ProblemsViewScope(
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
  void syncProject(BlazeContext context) {
    Instant startTime = Instant.now();
    SyncResult syncResult = SyncResult.FAILURE;
    SyncStats.Builder stats = SyncStats.builder();
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
      onSyncStart(project, context, syncParams.syncMode);
      if (syncParams.syncMode == SyncMode.STARTUP) {
        syncResult = SyncResult.SUCCESS;
        return;
      }
      syncResult = doSyncProject(context, oldBlazeProjectData, stats);
      if (context.isCancelled()) {
        syncResult = SyncResult.CANCELLED;
      }
      if (context.hasErrors()) {
        syncResult = SyncResult.FAILURE;
      }
    } catch (Throwable e) {
      logSyncError(context, e);
    } finally {
      finishSync(project, syncParams, context, syncResult, stats, startTime);
    }
  }

  private static void finishSync(
      Project project,
      BlazeSyncParams syncParams,
      BlazeContext context,
      SyncResult syncResult,
      SyncStats.Builder stats,
      Instant startTime) {
    try {
      if (syncResult.successful()) {
        ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
        BlazeProjectData blazeProjectData =
            BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
        if (syncParams.syncMode != SyncMode.NO_BUILD) {
          stats.addTimedEvents(
              updateInMemoryState(
                  project, context, projectViewSet, blazeProjectData, syncParams.syncMode));
        }
        onSyncComplete(project, context, projectViewSet, blazeProjectData, syncParams, syncResult);
      }
      stats
          .setSyncMode(syncParams.syncMode)
          .setSyncTitle(syncParams.title)
          .setSyncBinaryType(Blaze.getBuildSystemProvider(project).getSyncBinaryType())
          .setSyncResult(syncResult)
          .setStartTime(startTime)
          .setTotalClockTime(Duration.between(startTime, Instant.now()));
      EventLoggingService.getInstance().log(stats.build());
      outputTimingSummary(context, stats.getCurrentTimedEvents());

    } catch (Throwable e) {
      logSyncError(context, e);
    } finally {
      afterSync(project, syncParams, context, syncResult);
    }
  }

  private static void logSyncError(BlazeContext context, Throwable e) {
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

  private SyncResult doSyncProject(
      BlazeContext context, @Nullable BlazeProjectData oldProjectData, SyncStats.Builder stats) {
    BlazeSyncBuildResult buildResult =
        BuildPhaseSyncTask.runBuildPhase(project, syncParams, context);
    fillInBuildStats(stats, buildResult);
    if (!context.shouldContinue() || !buildResult.isValid()) {
      return SyncResult.FAILURE;
    }

    List<TimedEvent> timedEvents =
        ProjectUpdateSyncTask.runProjectUpdatePhase(project, buildResult, oldProjectData, context);
    stats.addTimedEvents(timedEvents);

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
  }

  private static void fillInBuildStats(SyncStats.Builder stats, BlazeSyncBuildResult buildResult) {
    SyncProjectState projectState = buildResult.getProjectState();
    if (projectState != null) {
      stats
          .setWorkspaceType(projectState.getLanguageSettings().getWorkspaceType())
          .setLanguagesActive(projectState.getLanguageSettings().getActiveLanguages());
    }
    buildResult
        .getBuildPhaseStats()
        .forEach(
            s -> {
              stats.addBuildPhaseStats(s);
              stats.addTimedEvents(s.timedEvents());
            });
  }

  private static List<TimedEvent> updateInMemoryState(
      Project project,
      BlazeContext parentContext,
      ProjectViewSet projectViewSet,
      BlazeProjectData blazeProjectData,
      SyncMode syncMode) {
    List<TimedEvent> timedEvents = new ArrayList<>();
    Scope.push(
        parentContext,
        context -> {
          context.push(
              new TimingScope("UpdateInMemoryState", EventType.Other)
                  .addScopeListener((events, duration) -> timedEvents.addAll(events)));
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
                          WorkspaceRoot.fromProject(project),
                          projectViewSet,
                          blazeProjectData,
                          workspaceModule,
                          syncMode);
                    }
                  });
        });
    return timedEvents;
  }

  private static void onSyncStart(Project project, BlazeContext context, SyncMode syncMode) {
    final SyncListener[] syncListeners = SyncListener.EP_NAME.getExtensions();
    for (SyncListener syncListener : syncListeners) {
      syncListener.onSyncStart(project, context, syncMode);
    }
  }

  private static void afterSync(
      Project project, BlazeSyncParams syncParams, BlazeContext context, SyncResult syncResult) {
    final SyncListener[] syncListeners = SyncListener.EP_NAME.getExtensions();
    for (SyncListener syncListener : syncListeners) {
      syncListener.afterSync(project, context, syncParams.syncMode, syncResult);
    }
  }

  private static void onSyncComplete(
      Project project,
      BlazeContext context,
      ProjectViewSet projectViewSet,
      BlazeProjectData blazeProjectData,
      BlazeSyncParams syncParams,
      SyncResult syncResult) {
    validate(project, context, blazeProjectData);
    final SyncListener[] syncListeners = SyncListener.EP_NAME.getExtensions();
    for (SyncListener syncListener : syncListeners) {
      syncListener.onSyncComplete(
          project,
          context,
          BlazeImportSettingsManager.getInstance(project).getImportSettings(),
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

  private static void outputTimingSummary(
      BlazeContext context, ImmutableList<TimedEvent> timedEvents) {
    Map<EventType, Long> totalTimes = new LinkedHashMap<>();
    for (EventType type : EventType.values()) {
      long totalTimeMillis =
          timedEvents.stream()
              .filter(e -> e.isLeafEvent && e.type == type)
              .mapToLong(e -> e.duration.toMillis())
              .sum();
      totalTimes.put(type, totalTimeMillis);
    }
    if (totalTimes.values().stream().mapToLong(l -> l).sum() < 1000) {
      return;
    }

    String summary =
        totalTimes.entrySet().stream()
            .map(e -> String.format("%s: %s", e.getKey(), durationStr(e.getValue())))
            .collect(joining(", "));

    context.output(PrintOutput.log("\nTiming summary:\n" + summary));
  }

  private static String durationStr(long timeMillis) {
    return timeMillis >= 1000
        ? String.format("%.1fs", timeMillis / 1000d)
        : String.format("%sms", timeMillis);
  }
}
