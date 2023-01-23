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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.stream.Collectors.joining;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
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
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.qsync.BlazeQueryParser;
import com.google.idea.blaze.qsync.BuildGraph;
import com.google.idea.blaze.qsync.query.AffectedPackages;
import com.google.idea.blaze.qsync.query.QuerySummary;
import com.google.idea.blaze.qsync.query.WorkspaceFileChange;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.stream.Stream;

/** Continually updates the project structure base on changes to the filesystem. */
public class ContinuousSync {
  static class SyncState {
    public static final SyncState NONE =
        new SyncState(QuerySummary.EMPTY, ImmutableList.of(), ImmutableList.of());
    private final QuerySummary queryOutput;
    ImmutableList<Path> includes;
    ImmutableList<Path> excludes;

    public SyncState(
        QuerySummary queryOutput, ImmutableList<Path> includes, ImmutableList<Path> excludes) {
      this.queryOutput = queryOutput;
      this.includes = includes;
      this.excludes = excludes;
    }
  }

  private final Logger logger = Logger.getInstance(getClass());

  private final Project project;
  private final BuildGraph buildGraph;
  private final BlazeImportSettings importSettings;
  private final ProjectViewManager projectViewManager;
  private final WorkspaceRoot workspaceRoot;

  private volatile SyncState lastSync = SyncState.NONE;

  public ContinuousSync(Project project, BuildGraph graph) {
    this.project = project;
    this.buildGraph = graph;
    importSettings = BlazeImportSettingsManager.getInstance(project).getImportSettings();
    projectViewManager = ProjectViewManager.getInstance(project);
    workspaceRoot = WorkspaceRoot.fromImportSettings(importSettings);
  }

  public void initialSync(BlazeContext context) throws IOException {
    ProjectViewSet projectViewSet = projectViewManager.reloadProjectView(context);
    ImportRoots ir =
        ImportRoots.builder(workspaceRoot, Blaze.getBuildSystemName(project))
            .add(projectViewSet)
            .build();
    ImmutableList<Path> includes =
        getValidDirectories(context, workspaceRoot.path(), ir.rootDirectories());
    ImmutableList<Path> excludes =
        getValidDirectories(context, workspaceRoot.path(), ir.excludeDirectories());

    QuerySummary queryOutput =
        runQuery(
            includes.stream().map(s -> s + "/...").collect(toImmutableList()),
            excludes.stream().map(s -> s + "/...").collect(toImmutableList()),
            context);
    lastSync = new SyncState(queryOutput, includes, excludes);

    buildGraph.setCurrent(context, new BlazeQueryParser(context).parse(queryOutput.getProto()));
  }

  public static String getQueryCommand(
      ImmutableCollection<String> includes, ImmutableCollection<String> excludes) {
    // This is the main query, note the use of :* that means that the query output has
    // all the files in that directory too. So we can identify all that is reachable.
    StringBuilder targets = new StringBuilder();
    targets.append("(");
    targets.append(includes.stream().map(w -> String.format("//%s:*", w)).collect(joining(" + ")));
    for (String excluded : excludes) {
      targets.append(String.format(" - //%s:*", excluded));
    }
    targets.append(")");
    return targets.toString();
  }

  public QuerySummary runQuery(
      ImmutableCollection<String> includes,
      ImmutableCollection<String> excludes,
      BlazeContext context)
      throws IOException {
    String query = getQueryCommand(includes, excludes);
    BuildSystem buildSystem = Blaze.getBuildSystemProvider(project).getBuildSystem();
    BuildInvoker invoker = buildSystem.getDefaultInvoker(project, context);
    BlazeCommand command =
        BlazeCommand.builder(invoker, BlazeCommandName.QUERY)
            .addBlazeFlags("--output=streamed_proto")
            .addBlazeFlags("--relative_locations=true")
            .addBlazeFlags(query)
            .build();
    logger.info(String.format("Running: %s", command));
    File protoFile = new File("/tmp/q.proto");
    FileOutputStream out = new FileOutputStream(protoFile);
    LineProcessingOutputStream lpos =
        LineProcessingOutputStream.of(
            BlazeConsoleLineProcessorProvider.getAllStderrLineProcessors(context));
    ExternalTask.builder(workspaceRoot)
        .addBlazeCommand(command)
        .context(context)
        .stdout(out)
        .stderr(lpos)
        .build()
        .run();

    QuerySummary querySummary = QuerySummary.create(protoFile);
    logger.info(
        String.format(
            "Query output of size %d summarized to size %d",
            protoFile.length(), querySummary.getProto().toByteArray().length));

    return querySummary;
  }

  public boolean onFileSystemChange(
      BlazeContext context, ImmutableCollection<WorkspaceFileChange> changes) throws IOException {
    SyncState syncState = lastSync;
    if (syncState == SyncState.NONE) {
      // TODO we should process them after instead.
      logger.info("Ignoring filesystem changes as initial sync not complete yet");
      return false;
    }
    logger.info(String.format("Got %d file changes", changes.size()));

    AffectedPackages affected =
        AffectedPackages.newBuilder()
            .projectIncludes(lastSync.includes)
            .projectExcludes(lastSync.excludes)
            .lastQuery(lastSync.queryOutput)
            .changedFiles(changes)
            .build(context);
    if (affected.isEmpty()) {
      // nothing to do.
      return true;
    }

    QuerySummary partialGraph =
        runQuery(
            affected.getModifiedPackages().stream().map(Path::toString).collect(toImmutableSet()),
            ImmutableList.of(),
            context);

    QuerySummary newQuery =
        syncState.queryOutput.applyDelta(partialGraph, affected.getDeletedPackages());
    lastSync = new SyncState(newQuery, syncState.includes, syncState.excludes);

    buildGraph.setCurrent(context, new BlazeQueryParser(context).parse(newQuery.getProto()));
    return true;
  }

  private static ImmutableList<Path> getValidDirectories(
      Context context, Path workspaceRoot, Collection<WorkspacePath> roots) throws IOException {
    ImmutableList.Builder<Path> validRoots = ImmutableList.builder();
    for (WorkspacePath rootDirectory : roots) {
      Path root = rootDirectory.asPath();
      Path absolute = workspaceRoot.resolve(root);
      if (isValid(context, absolute)) {
        validRoots.add(root);
      }
    }
    return validRoots.build();
  }

  private static boolean isValid(Context context, Path candidate) throws IOException {
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
