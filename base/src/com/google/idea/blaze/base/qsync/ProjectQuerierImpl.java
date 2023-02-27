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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.util.concurrent.Uninterruptibles.getUninterruptibly;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.Uninterruptibles;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.base.vcs.BlazeVcsHandlerProvider;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.qsync.BlazeProjectSnapshot;
import com.google.idea.blaze.qsync.FullProjectUpdate;
import com.google.idea.blaze.qsync.PackageStatementParser;
import com.google.idea.blaze.qsync.ProjectRefresher;
import com.google.idea.blaze.qsync.RefreshOperation;
import com.google.idea.blaze.qsync.WorkspaceResolvingPackageReader;
import com.google.idea.blaze.qsync.query.QuerySpec;
import com.google.idea.blaze.qsync.query.QuerySummary;
import com.google.idea.blaze.qsync.vcs.VcsState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

/** An object that knows how to */
public class ProjectQuerierImpl implements ProjectQuerier {

  private final Logger logger = Logger.getInstance(getClass());

  private final Project project;
  private final BuildSystemName buildSystem;
  private final Path workspaceRoot;
  private final ProjectRefresher projectRefresher;
  private final QueryRunner queryRunner;

  @VisibleForTesting
  public ProjectQuerierImpl(
      Project project,
      BuildSystemName buildSystem,
      Path workspaceRoot,
      QueryRunner queryRunner,
      ProjectRefresher projectRefresher) {
    this.project = project;
    this.buildSystem = buildSystem;
    this.workspaceRoot = workspaceRoot;
    this.projectRefresher = projectRefresher;
    this.queryRunner = queryRunner;
  }

  public static ProjectQuerier create(Project project) {
    BlazeImportSettings settings =
        BlazeImportSettingsManager.getInstance(project).getImportSettings();
    Path workspaceRoot = WorkspaceRoot.fromImportSettings(settings).path();
    ProjectRefresher projectRefresher =
        new ProjectRefresher(
            new WorkspaceResolvingPackageReader(workspaceRoot, new PackageStatementParser()));
    QueryRunner queryRunner = new BazelBinaryQueryRunner(project, workspaceRoot);
    return new ProjectQuerierImpl(
        project, settings.getBuildSystem(), workspaceRoot, queryRunner, projectRefresher);
  }

  /**
   * Performs a full query for the project, starting from scratch.
   *
   * <p>This includes reloading the project view.
   */
  @Override
  public BlazeProjectSnapshot fullQuery(BlazeContext context) throws IOException {

    ProjectViewSet projectViewSet =
        checkNotNull(ProjectViewManager.getInstance(project).reloadProjectView(context));
    ImportRoots ir =
        ImportRoots.builder(WorkspaceRoot.fromProject(project), buildSystem)
            .add(projectViewSet)
            .build();

    FullProjectUpdate fullQuery =
        projectRefresher.startFullUpdate(
            context,
            getValidDirectories(context, ir.rootDirectories()),
            getValidDirectories(context, ir.excludeDirectories()));

    Optional<ListenableFuture<VcsState>> vcsStateFuture = getVcsState(context);
    // TODO if we throw between here and when we get this future, perhaps we should cancel it?

    QuerySpec querySpec = fullQuery.getQuerySpec().get();
    try (InputStream queryStream = queryRunner.runQuery(querySpec, context)) {
      fullQuery.setQueryOutput(QuerySummary.create(queryStream));
    }

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

  /**
   * Performs a delta query to update the state based on the state from the last query run, if
   * possible. The project view is not reloaded.
   *
   * <p>There are various cases when we will fall back to {@link #fullQuery(BlazeContext)},
   * including:
   *
   * <ul>
   *   <li>if the VCS state is not available for any reason
   *   <li>if the upstream revision has changed
   * </ul>
   */
  @Override
  public BlazeProjectSnapshot update(BlazeProjectSnapshot previousState, BlazeContext context)
      throws IOException {

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
      try (InputStream queryStream = queryRunner.runQuery(spec.get(), context)) {
        refresh.setQueryOutput(QuerySummary.create(queryStream));
      }
    } else {
      refresh.setQueryOutput(QuerySummary.EMPTY);
    }
    return refresh.createBlazeProject();
  }

  private Optional<ListenableFuture<VcsState>> getVcsState(BlazeContext context) {
    return Optional.ofNullable(BlazeVcsHandlerProvider.vcsHandlerForProject(project))
        .flatMap(h -> h.getVcsState(context, BlazeExecutor.getInstance().getExecutor()));
  }

  private List<Path> getValidDirectories(BlazeContext context, Collection<WorkspacePath> ir)
      throws IOException {
    ArrayList<Path> paths = new ArrayList<>();
    for (WorkspacePath rootDirectory : ir) {
      String root = rootDirectory.toString();
      Path candidate = workspaceRoot.resolve(root);
      if (isValid(context, candidate)) {
        paths.add(workspaceRoot.relativize(candidate));
      }
    }
    return paths;
  }

  private static boolean isValid(BlazeContext context, Path candidate) throws IOException {
    if (Files.exists(candidate.resolve("BUILD"))) {
      return true;
    }
    if (!Files.isDirectory(candidate)) {
      context.output(
          PrintOutput.output(
              "Directory specified in project does not exist or is not a directory: %s",
              candidate));
      return false;
    }
    boolean valid = false;
    try (Stream<Path> stream = Files.list(candidate)) {
      for (Path child : stream.toArray(Path[]::new)) {
        if (Files.isDirectory(child)) {
          boolean validChild = isValid(context, child);
          valid = valid || validChild;
        } else {
          if (child.toString().endsWith(".java") || child.toString().endsWith(".kt")) {
            context.output(
                PrintOutput.log("WARNING: Sources found outside BUILD packages: " + child));
          }
        }
      }
    }
    return valid;
  }
}
