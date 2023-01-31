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
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.util.concurrent.Uninterruptibles.getUninterruptibly;
import static java.util.stream.Collectors.joining;

import com.google.common.collect.ImmutableList;
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
import com.google.idea.blaze.base.vcs.BlazeVcsHandler;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.qsync.query.AffectedPackages;
import com.google.idea.blaze.qsync.query.QueryState;
import com.google.idea.blaze.qsync.query.QuerySummary;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
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
  private final WorkspaceRoot workspaceRoot;

  public ProjectQuerier(Project project) {
    this.project = project;
    settings = BlazeImportSettingsManager.getInstance(project).getImportSettings();
    workspaceRoot = WorkspaceRoot.fromImportSettings(settings);
  }

  /**
   * Performs a full query for the project, starting from scratch.
   *
   * <p>This includes reloading the project view.
   */
  public QueryState fullQuery(BlazeContext context) throws IOException {

    ProjectViewSet projectViewSet =
        checkNotNull(ProjectViewManager.getInstance(project).reloadProjectView(context));
    ImportRoots ir =
        ImportRoots.builder(workspaceRoot, settings.getBuildSystem()).add(projectViewSet).build();

    QueryState.Builder state = QueryState.builder();

    Optional<ListenableFuture<String>> upstreamVersion = Optional.empty();
    Optional<ListenableFuture<WorkingSet>> workingSet = Optional.empty();
    BlazeVcsHandler vcsHandler = BlazeVcsHandler.vcsHandlerForProject(project);
    if (vcsHandler != null) {
      upstreamVersion =
          vcsHandler.getUpstreamVersion(
              project, context, workspaceRoot, BlazeExecutor.getInstance().getExecutor());
      workingSet =
          Optional.of(
              vcsHandler.getWorkingSet(
                  project, context, workspaceRoot, BlazeExecutor.getInstance().getExecutor()));
    }

    List<Path> includes = getValidDirectories(context, workspaceRoot, ir.rootDirectories());
    List<Path> excludes = getValidDirectories(context, workspaceRoot, ir.excludeDirectories());
    state.includePaths(includes).excludePaths(excludes);

    // Convert root directories into blaze target patterns:
    ImmutableList<String> includeTargets =
        includes.stream().map(path -> String.format("//%s/...", path)).collect(toImmutableList());
    ImmutableList<String> excludeTargets =
        excludes.stream().map(path -> String.format("//%s/...", path)).collect(toImmutableList());

    QuerySummary querySummary = runQuery(includeTargets, excludeTargets, context);
    state.queryOutput(querySummary);

    if (upstreamVersion.isPresent() && workingSet.isPresent()) {
      try {
        state
            .upstreamRevision(getUninterruptibly(upstreamVersion.get()))
            .workingSet(getUninterruptibly(workingSet.get()).toWorkspaceFileChanges());
      } catch (ExecutionException e) {
        // We can continue without the VCS state, but it means that when we update later on
        // we have to re-run the entire query rather than performing an minimal update.
        logger.warn("Failed to get VCS state", e);
        context.output(
            PrintOutput.output("WARNING: Could not VCS state, future updates may be suboptimal"));
        if (e.getCause().getMessage() != null) {
          context.output(PrintOutput.output("Cause: %s", e.getCause().getMessage()));
        }
      }
    }
    return state.build();
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
  public QueryState update(QueryState previousState, BlazeContext context) throws IOException {
    // if we failed to get the upstream revision or working set, we can't do a delta update.
    // Fall back to a full query.
    if (!previousState.canPerformDeltaUpdate()) {
      context.output(PrintOutput.output("No VCS state from last query: performing full query"));
      return fullQuery(context);
    }

    Optional<ListenableFuture<String>> upstreamVersionFuture = Optional.empty();
    Optional<ListenableFuture<WorkingSet>> workingSetFuture = Optional.empty();
    BlazeVcsHandler vcsHandler = BlazeVcsHandler.vcsHandlerForProject(project);
    if (vcsHandler != null) {
      upstreamVersionFuture =
          vcsHandler.getUpstreamVersion(
              project, context, workspaceRoot, BlazeExecutor.getInstance().getExecutor());
      workingSetFuture =
          Optional.of(
              vcsHandler.getWorkingSet(
                  project, context, workspaceRoot, BlazeExecutor.getInstance().getExecutor()));
    }
    if (upstreamVersionFuture.isEmpty() || workingSetFuture.isEmpty()) {
      context.output(
          PrintOutput.output("VCS doesn't support delta updates: performing full query"));
      return fullQuery(context);
    }
    String upstream;
    WorkingSet workingSet;
    try {
      upstream = getUninterruptibly(upstreamVersionFuture.get());
      workingSet = getUninterruptibly(workingSetFuture.get());
    } catch (ExecutionException e) {
      logger.warn("Failed to get VCS state", e.getCause());
      context.output(PrintOutput.output("WARNING: Could not VCS state, performing full query"));
      if (e.getCause().getMessage() != null) {
        context.output(PrintOutput.output("Cause: %s", e.getCause().getMessage()));
      }
      return fullQuery(context);
    }

    AffectedPackages affected =
        previousState.deltaUpdate(upstream, workingSet.toWorkspaceFileChanges(), context);
    if (affected == null) {
      return fullQuery(context);
    }

    if (affected.isEmpty()) {
      // this implies that the user was in a clean client, and still is.
      context.output(PrintOutput.output("Nothing has changed, nothing to do."));
      return previousState;
    }
    QuerySummary partialQuery =
        runQuery(
            affected.getModifiedPackages().stream()
                .map(p -> String.format("//%s", p))
                .collect(toImmutableList()),
            ImmutableList.of(),
            context);
    QuerySummary newState =
        previousState.queryOutput().applyDelta(partialQuery, affected.getDeletedPackages());

    return QueryState.builder()
        .workingSet(workingSet.toWorkspaceFileChanges())
        .upstreamRevision(upstream)
        .includePaths(previousState.includePaths())
        .excludePaths(previousState.excludePaths())
        .queryOutput(newState)
        .build();
  }

  public QuerySummary runQuery(
      List<String> includeTargets, List<String> excludeTargets, BlazeContext context)
      throws IOException {

    BuildSystem buildSystem = Blaze.getBuildSystemProvider(project).getBuildSystem();
    BuildInvoker invoker = buildSystem.getDefaultInvoker(project, context);

    // This is the main query, note the use of :* that means that the query output has
    // all the files in that directory too. So we can identify all that is reachable.
    StringBuilder targets = new StringBuilder();
    targets.append("(");
    targets.append(
        includeTargets.stream().map(w -> String.format("%s:*", w)).collect(joining(" + ")));
    for (String excluded : excludeTargets) {
      targets.append(String.format(" - %s:*", excluded));
    }
    targets.append(")");

    BlazeCommand builder =
        BlazeCommand.builder(invoker, BlazeCommandName.QUERY)
            .addBlazeFlags(targets.toString())
            .addBlazeFlags("--output=streamed_proto")
            .addBlazeFlags("--relative_locations=true")
            .build();

    File protoFile = new File("/tmp/q.proto");
    FileOutputStream out = new FileOutputStream(protoFile);
    LineProcessingOutputStream lpos =
        LineProcessingOutputStream.of(
            BlazeConsoleLineProcessorProvider.getAllStderrLineProcessors(context));
    ExternalTask.builder(workspaceRoot)
        .addBlazeCommand(builder)
        .context(context)
        .stdout(out)
        .stderr(lpos)
        .build()
        .run();

    QuerySummary summary = QuerySummary.create(protoFile);
    context.output(
        PrintOutput.output(
            "Summarized %d query bytes into %s of summary",
            protoFile.length(), summary.getProto().toByteArray().length));
    return summary;
  }

  private static List<Path> getValidDirectories(
      BlazeContext context, WorkspaceRoot workspaceRoot, Collection<WorkspacePath> ir)
      throws IOException {
    ArrayList<Path> strings = new ArrayList<>();
    for (WorkspacePath rootDirectory : ir) {
      String root = rootDirectory.toString();
      Path workspacePath = workspaceRoot.directory().toPath();
      Path candidate = workspacePath.resolve(root);
      if (isValid(context, candidate)) {
        strings.add(workspacePath.relativize(candidate));
      }
    }
    return strings;
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
