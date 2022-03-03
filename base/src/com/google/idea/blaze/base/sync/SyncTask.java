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
package com.google.idea.blaze.base.sync;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.logging.EventLoggingService;
import com.google.idea.blaze.base.logging.utils.SyncStats;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.output.StatusOutput;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.util.SaveUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;

/**
 * A sync task. This class encapsulates a single sync operation.
 *
 * <p>Currently, this class handles only the startup sync. Other sync types will be migrated from
 * {@link SyncPhaseCoordinator}.
 */
public class SyncTask {

  private final Logger logger = Logger.getInstance(SyncTask.class);
  private final Project project;
  private final int buildId;
  private final BlazeSyncParams params;
  private final BlazeContext rootContext;
  private final Instant startTime;
  private final SyncStats.Builder stats;

  public SyncTask(Project project, int buildId, BlazeSyncParams params, BlazeContext context) {
    this.project = project;
    this.buildId = buildId;
    this.params = params;
    this.rootContext = context;
    this.startTime = Instant.now();
    this.stats =
        SyncStats.builder()
            .setSyncMode(params.syncMode())
            .setSyncTitle(params.title())
            .setSyncOrigin(params.syncOrigin())
            .setSyncBinaryType(Blaze.getBuildSystemProvider(project).getSyncBinaryType())
            .setStartTime(startTime);
  }

  public void run() {
    SyncResult result = SyncResult.FAILURE;
    try {
      SaveUtil.saveAllFiles();
      sendOnStartEvent();
      if (!rootContext.shouldContinue()) {
        return;
      }
      if (params.syncMode() == SyncMode.STARTUP) {
        result = SyncResult.SUCCESS;
        return;
      }
    } catch (Throwable t) {
      result = SyncResult.FAILURE;
      logSyncError(t);
    } finally {
      finish(result);
    }
  }

  private static void forEachListener(Consumer<SyncListener> action) {
    final SyncListener[] syncListeners = SyncListener.EP_NAME.getExtensions();
    for (SyncListener syncListener : syncListeners) {
      action.accept(syncListener);
    }
  }

  private void sendOnStartEvent() {
    SyncScope.push(
        rootContext,
        context -> {
          for (SyncListener listener : SyncListener.EP_NAME.getExtensions()) {
            listener.onSyncStart(project, context, params.syncMode());
          }
        });
  }

  private static String getSyncStatus(SyncResult result) {
    if (result.successful()) {
      return "finished";
    }
    if (SyncResult.CANCELLED.equals(result)) {
      return "cancelled";
    }
    return "failed";
  }

  private void finish(SyncResult result) {
    try {
      if (result.successful()) {
        // TODO
      }
      // TODO set blazeExecTime elsewhere?
      stats.setSyncResult(result).setTotalClockTime(Duration.between(startTime, Instant.now()));
      EventLoggingService.getInstance().log(stats.build());
      rootContext.output(new StatusOutput(("Sync " + getSyncStatus(result))));
    } catch (Throwable t) {
      logSyncError(t);
    } finally {
      forEachListener(
          l ->
              l.afterSync(
                  project, rootContext, params.syncMode(), result, ImmutableSet.of(buildId)));
    }
  }

  private void logSyncError(Throwable e) {
    // ignore ProcessCanceledException
    Throwable cause = e;
    while (cause != null) {
      if (cause instanceof ProcessCanceledException) {
        return;
      }
      cause = cause.getCause();
    }
    logger.error(e);
    IssueOutput.error("Internal error: " + e.getMessage()).submit(rootContext);
  }
}
