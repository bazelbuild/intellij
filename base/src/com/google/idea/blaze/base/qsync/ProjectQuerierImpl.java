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
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.Uninterruptibles;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.google.idea.blaze.base.logging.utils.querysync.SyncQueryStats;
import com.google.idea.blaze.base.logging.utils.querysync.SyncQueryStatsScope;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.vcs.BlazeVcsHandlerProvider.BlazeVcsHandler;
import com.google.idea.blaze.common.vcs.VcsState;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.FullProjectUpdate;
import com.google.idea.blaze.qsync.ProjectRefresher;
import com.google.idea.blaze.qsync.RefreshOperation;
import com.google.idea.blaze.qsync.project.BlazeProjectSnapshot;
import com.google.idea.blaze.qsync.project.PostQuerySyncData;
import com.google.idea.blaze.qsync.project.ProjectDefinition;
import com.google.idea.blaze.qsync.query.QuerySpec;
import com.google.idea.blaze.qsync.query.QuerySummary;
import com.intellij.openapi.diagnostic.Logger;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

/** An object that knows how to */
public class ProjectQuerierImpl implements ProjectQuerier {

  private static final Logger logger = Logger.getInstance(ProjectQuerierImpl.class);

  private final QueryRunner queryRunner;
  private final ProjectRefresher projectRefresher;
  private final Optional<BlazeVcsHandler> vcsHandler;

  @VisibleForTesting
  public ProjectQuerierImpl(
      QueryRunner queryRunner,
      ProjectRefresher projectRefresher,
      Optional<BlazeVcsHandler> vcsHandler) {
    this.queryRunner = queryRunner;
    this.projectRefresher = projectRefresher;
    this.vcsHandler = vcsHandler;
  }

  /**
   * Performs a full query for the project, starting from scratch.
   *
   * <p>This includes reloading the project view.
   */
  @Override
  public BlazeProjectSnapshot fullQuery(ProjectDefinition projectDef, BlazeContext context)
      throws IOException, BuildException {

    Optional<VcsState> vcsState = getVcsState(context);
    SyncQueryStatsScope.fromContext(context)
        .ifPresent(stats -> stats.setSyncMode(SyncQueryStats.SyncMode.FULL));

    logger.info(
        String.format(
            "Starting full query update; upstream rev=%s; snapshot path=%s",
            vcsState.map(s -> s.upstreamRevision).orElse("<unknown>"),
            vcsState.flatMap(s -> s.workspaceSnapshotPath).map(Object::toString).orElse("<none>")));

    FullProjectUpdate fullQuery = projectRefresher.startFullUpdate(context, projectDef, vcsState);

    QuerySpec querySpec = fullQuery.getQuerySpec().get();
    fullQuery.setQueryOutput(queryRunner.runQuery(querySpec, context));

    return fullQuery.createBlazeProject();
  }

  private Optional<VcsState> getVcsState(BlazeContext context) {
    Optional<ListenableFuture<VcsState>> stateFuture =
        vcsHandler.flatMap(h -> h.getVcsState(context, BlazeExecutor.getInstance().getExecutor()));
    if (stateFuture.isEmpty()) {
      return Optional.empty();
    }
    try {
      return Optional.of(Uninterruptibles.getUninterruptibly(stateFuture.get()));
    } catch (ExecutionException e) {
      // We can continue without the VCS state, but it means that when we update later on
      // we have to re-run the entire query rather than performing an minimal update.
      context.handleExceptionAsWarning(
          "WARNING: Could not get VCS state, future updates may be suboptimal", e.getCause());
      return Optional.empty();
    }
  }

  /**
   * Performs a delta query to update the state based on the state from the last query run, if
   * possible. The project view is not reloaded.
   *
   * <p>There are various cases when we will fall back to {@link #fullQuery}, including:
   *
   * <ul>
   *   <li>if the VCS state is not available for any reason
   *   <li>if the upstream revision has changed
   * </ul>
   */
  @Override
  public BlazeProjectSnapshot update(
      ProjectDefinition currentProjectDef, PostQuerySyncData previousState, BlazeContext context)
      throws IOException, BuildException {

    Optional<VcsState> vcsState = getVcsState(context);
    SyncQueryStatsScope.fromContext(context)
        .ifPresent(stats -> stats.setSyncMode(SyncQueryStats.SyncMode.DELTA));
    logger.info(
        String.format(
            "Starting partial query update; upstream rev=%s; snapshot path=%s",
            vcsState.map(s -> s.upstreamRevision).orElse("<unknown>"),
            vcsState.flatMap(s -> s.workspaceSnapshotPath).map(Object::toString).orElse("<none>")));

    RefreshOperation refresh =
        projectRefresher.startPartialRefresh(context, previousState, vcsState, currentProjectDef);

    Optional<QuerySpec> spec = refresh.getQuerySpec();
    if (spec.isPresent()) {
      refresh.setQueryOutput(queryRunner.runQuery(spec.get(), context));
    } else {
      refresh.setQueryOutput(QuerySummary.EMPTY);
    }
    return refresh.createBlazeProject();
  }
}
