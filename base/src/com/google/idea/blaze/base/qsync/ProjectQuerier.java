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

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.google.idea.blaze.base.async.process.ExternalTask;
import com.google.idea.blaze.base.async.process.LineProcessingOutputStream;
import com.google.idea.blaze.base.bazel.BuildSystem;
import com.google.idea.blaze.base.bazel.BuildSystem.BuildInvoker;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.console.BlazeConsoleLineProcessorProvider;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.base.sync.workspace.WorkingSet;
import com.google.idea.blaze.base.vcs.BlazeVcsHandlerProvider;
import com.google.idea.blaze.base.vcs.BlazeVcsHandlerProvider.BlazeVcsHandler;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.qsync.BlazeProjectSnapshot;
import com.google.idea.blaze.qsync.FullProjectUpdate;
import com.google.idea.blaze.qsync.PackageStatementParser;
import com.google.idea.blaze.qsync.ProjectRefresher;
import com.google.idea.blaze.qsync.ProjectUpdate;
import com.google.idea.blaze.qsync.WorkspaceResolvingPackageReader;
import com.google.idea.blaze.qsync.query.QuerySpec;
import com.google.idea.blaze.qsync.query.QuerySummary;
import com.google.idea.blaze.qsync.vcs.VcsState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
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
public class ProjectQuerier {

  private final Logger logger = Logger.getInstance(getClass());

  private final Project project;
  private final BlazeImportSettings settings;
  private final Path workspaceRoot;
  private final ProjectRefresher projectRefresher;

  public ProjectQuerier(Project project) {
    this.project = project;
    settings = BlazeImportSettingsManager.getInstance(project).getImportSettings();
    workspaceRoot = WorkspaceRoot.fromImportSettings(settings).path();
    projectRefresher =
        new ProjectRefresher(
            new WorkspaceResolvingPackageReader(workspaceRoot, new PackageStatementParser()));
  }

  /**
   * Performs a full query for the project, starting from scratch.
   *
   * <p>This includes reloading the project view.
   */
  public BlazeProjectSnapshot fullQuery(BlazeContext context) throws IOException {

    ProjectViewSet projectViewSet =
        checkNotNull(ProjectViewManager.getInstance(project).reloadProjectView(context));
    ImportRoots ir =
        ImportRoots.builder(WorkspaceRoot.fromProject(project), settings.getBuildSystem())
            .add(projectViewSet)
            .build();

    FullProjectUpdate fullQuery =
        projectRefresher.startFullUpdate(
            context,
            getValidDirectories(context, ir.rootDirectories()),
            getValidDirectories(context, ir.excludeDirectories()));

    ListenableFuture<Optional<VcsState>> vcsStateFuture = getVcsState(context);
    // TODO if we throw between here and when we get this future, perhaps we should cancel it?

    QuerySpec querySpec = fullQuery.getQuerySpec().get();
    try (InputStream queryStream = runQuery(querySpec.getQueryArgs(), context)) {
      fullQuery.setQueryOutput(QuerySummary.create(queryStream));
    }

    Optional<VcsState> vcsState = Optional.empty();
    try {
      vcsState = getUninterruptibly(vcsStateFuture);
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
  public BlazeProjectSnapshot update(BlazeProjectSnapshot previousState, BlazeContext context)
      throws IOException {

    Optional<VcsState> vcsState = Optional.empty();
    try {
      vcsState = getUninterruptibly(getVcsState(context));
    } catch (ExecutionException e) {
      logger.warn("Failed to get VCS state", e.getCause());
      context.output(PrintOutput.output("WARNING: Could not get VCS state"));
      if (e.getCause().getMessage() != null) {
        context.output(PrintOutput.output("Cause: %s", e.getCause().getMessage()));
      }
    }

    ProjectUpdate update = projectRefresher.startPartialUpdate(context, previousState, vcsState);

    Optional<QuerySpec> spec = update.getQuerySpec();
    if (spec.isPresent()) {
      InputStream queryStream = runQuery(spec.get().getQueryArgs(), context);
      update.setQueryOutput(QuerySummary.create(queryStream));
    } else {
      update.setQueryOutput(QuerySummary.EMPTY);
    }
    return update.createBlazeProject();
  }

  private ListenableFuture<Optional<VcsState>> getVcsState(BlazeContext context) {
    BlazeVcsHandler vcsHandler = BlazeVcsHandlerProvider.vcsHandlerForProject(project);
    if (vcsHandler == null) {
      return Futures.immediateFuture(Optional.empty());
    }
    Optional<ListenableFuture<String>> upstreamRev =
        vcsHandler.getUpstreamVersion(context, BlazeExecutor.getInstance().getExecutor());
    if (!upstreamRev.isPresent()) {
      return Futures.immediateFuture(Optional.empty());
    }
    ListenableFuture<String> upstreamFuture = upstreamRev.get();
    ListenableFuture<WorkingSet> workingSet =
        vcsHandler.getWorkingSet(context, BlazeExecutor.getInstance().getExecutor());
    return Futures.whenAllSucceed(upstreamFuture, workingSet)
        .call(
            () ->
                Optional.of(
                    new VcsState(
                        Futures.getDone(upstreamFuture),
                        Futures.getDone(workingSet).toWorkspaceFileChanges())),
            BlazeExecutor.getInstance().getExecutor());
  }

  private InputStream runQuery(List<String> queryArgs, BlazeContext context) throws IOException {

    BuildSystem buildSystem = Blaze.getBuildSystemProvider(project).getBuildSystem();
    BuildInvoker invoker = buildSystem.getDefaultInvoker(project, context);

    BlazeCommand builder =
        BlazeCommand.builder(invoker, BlazeCommandName.QUERY).addBlazeFlags(queryArgs).build();

    File protoFile = new File("/tmp/q.proto");
    FileOutputStream out = new FileOutputStream(protoFile);
    LineProcessingOutputStream lpos =
        LineProcessingOutputStream.of(
            BlazeConsoleLineProcessorProvider.getAllStderrLineProcessors(context));
    ExternalTask.builder(workspaceRoot.toFile())
        .addBlazeCommand(builder)
        .context(context)
        .stdout(out)
        .stderr(lpos)
        .build()
        .run();

    return new BufferedInputStream(new FileInputStream(protoFile));
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
