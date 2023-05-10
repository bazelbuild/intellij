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

import static com.google.common.util.concurrent.Uninterruptibles.getUninterruptibly;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.Uninterruptibles;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.vcs.BlazeVcsHandlerProvider;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.FullProjectUpdate;
import com.google.idea.blaze.qsync.ProjectRefresher;
import com.google.idea.blaze.qsync.RefreshOperation;
import com.google.idea.blaze.qsync.project.BlazeProjectSnapshot;
import com.google.idea.blaze.qsync.project.PostQuerySyncData;
import com.google.idea.blaze.qsync.project.ProjectDefinition;
import com.google.idea.blaze.qsync.query.QuerySpec;
import com.google.idea.blaze.qsync.query.QuerySummary;
import com.google.idea.blaze.qsync.vcs.VcsState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

/** An object that knows how to */
public class ProjectQuerierImpl implements ProjectQuerier {

  private final Logger logger = Logger.getInstance(getClass());

  private final Project project;
  private final ProjectRefresher projectRefresher;
  private final QueryRunner queryRunner;

  @VisibleForTesting
  public ProjectQuerierImpl(
      Project project, QueryRunner queryRunner, ProjectRefresher projectRefresher) {
    this.project = project;
    this.projectRefresher = projectRefresher;
    this.queryRunner = queryRunner;
  }

  /**
   * Performs a full query for the project, starting from scratch.
   *
   * <p>This includes reloading the project view.
   */
  @Override
  public BlazeProjectSnapshot fullQuery(ProjectDefinition projectDef, BlazeContext context)
      throws IOException, BuildException {

    FullProjectUpdate fullQuery = projectRefresher.startFullUpdate(context, projectDef);

    Optional<ListenableFuture<VcsState>> vcsStateFuture = getVcsState(context);
    // TODO if we throw between here and when we get this future, perhaps we should cancel it?

    QuerySpec querySpec = fullQuery.getQuerySpec().get();
    fullQuery.setQueryOutput(queryRunner.runQuery(querySpec, context));

    Optional<VcsState> vcsState = Optional.empty();
    try {
      if (vcsStateFuture.isPresent()) {
        vcsState = Optional.of(getUninterruptibly(vcsStateFuture.get()));
      }
    } catch (ExecutionException e) {
      // We can continue without the VCS state, but it means that when we update later on
      // we have to re-run the entire query rather than performing an minimal update.
      logger.warn("Failed to get VCS state", e);
      context.output(
          PrintOutput.output("WARNING: Could not get VCS state, future updates may be suboptimal"));
      if (e.getCause().getMessage() != null) {
        context.output(PrintOutput.output("Cause: %s", e.getCause().getMessage()));
      }
    }

    fullQuery.setVcsState(vcsState);

    return fullQuery.createBlazeProject();
  }

  private Optional<ListenableFuture<VcsState>> getVcsState(BlazeContext context) {
    return Optional.ofNullable(BlazeVcsHandlerProvider.vcsHandlerForProject(project))
        .flatMap(h -> h.getVcsState(context, BlazeExecutor.getInstance().getExecutor()));
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
  public BlazeProjectSnapshot update(PostQuerySyncData previousState, BlazeContext context)
      throws IOException, BuildException {

    Optional<VcsState> vcsState = Optional.empty();
    try {
      Optional<ListenableFuture<VcsState>> stateFuture = getVcsState(context);
      // conceptually, this is stateFuture.map(Uninterruptibles::getUninterruptibly) but the
      // declared exception prevents us doing it so concisely.
      if (stateFuture.isPresent()) {
        vcsState = Optional.of(Uninterruptibles.getUninterruptibly(stateFuture.get()));
      }
    } catch (ExecutionException e) {
      logger.warn("Failed to get VCS state", e.getCause());
      context.output(PrintOutput.output("WARNING: Could not get VCS state"));
      if (e.getCause().getMessage() != null) {
        context.output(PrintOutput.output("Cause: %s", e.getCause().getMessage()));
      }
    }

    RefreshOperation refresh =
        projectRefresher.startPartialRefresh(context, previousState, vcsState);

    Optional<QuerySpec> spec = refresh.getQuerySpec();
    if (spec.isPresent()) {
      refresh.setQueryOutput(queryRunner.runQuery(spec.get(), context));
    } else {
      refresh.setQueryOutput(QuerySummary.EMPTY);
    }
    return refresh.createBlazeProject();
  }
}
