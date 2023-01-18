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
import static java.util.stream.Collectors.joining;

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
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.qsync.BlazeQueryParser;
import com.google.idea.blaze.qsync.BuildGraph;
import com.google.idea.blaze.qsync.BuildGraphData;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

/** An object that knows how to */
public class ProjectQuerier {

  private final Project project;
  private final BuildGraph graph;

  public ProjectQuerier(Project project, BuildGraph buildGraph) {
    this.project = project;
    this.graph = buildGraph;
  }

  public void rebuild(BlazeContext context) throws IOException {

    BlazeImportSettings settings =
        BlazeImportSettingsManager.getInstance(project).getImportSettings();
    WorkspaceRoot root = WorkspaceRoot.fromImportSettings(settings);

    ProjectViewSet projectViewSet =
        checkNotNull(ProjectViewManager.getInstance(project).reloadProjectView(context));
    ImportRoots ir =
        ImportRoots.builder(root, settings.getBuildSystem()).add(projectViewSet).build();

    List<String> includes = getValidDirectories(context, root, ir.rootDirectories());
    List<String> excludes = getValidDirectories(context, root, ir.excludeDirectories());

    BuildSystem buildSystem = Blaze.getBuildSystemProvider(project).getBuildSystem();
    BuildInvoker invoker = buildSystem.getDefaultInvoker(project, context);

    // This is the main query, note the use of :* that means that the query output has
    // all the files in that directory too. So we can identify all that is reachable.
    StringBuilder targets = new StringBuilder();
    targets.append("(");
    targets.append(
        includes.stream().map(w -> String.format("//%s/...:*", w)).collect(joining(" + ")));
    for (String excluded : excludes) {
      targets.append(String.format(" - //%s/...:*", excluded));
    }
    targets.append(")");

    BlazeCommand builder =
        BlazeCommand.builder(invoker, BlazeCommandName.QUERY)
            .addBlazeFlags(targets.toString())
            .addBlazeFlags("--output=streamed_proto")
            .build();

    File protoFile = new File("/tmp/q.proto");
    FileOutputStream out = new FileOutputStream(protoFile);
    LineProcessingOutputStream lpos =
        LineProcessingOutputStream.of(
            BlazeConsoleLineProcessorProvider.getAllStderrLineProcessors(context));
    ExternalTask.builder(root)
        .addBlazeCommand(builder)
        .context(context)
        .stdout(out)
        .stderr(lpos)
        .build()
        .run();

    BuildGraphData graphData =
        new BlazeQueryParser(root.directory().toPath().toAbsolutePath(), context).parse(protoFile);
    graph.setCurrent(context, graphData);
  }

  private static List<String> getValidDirectories(
      BlazeContext context, WorkspaceRoot workspaceRoot, Collection<WorkspacePath> ir)
      throws IOException {
    ArrayList<String> strings = new ArrayList<>();
    for (WorkspacePath rootDirectory : ir) {
      String root = rootDirectory.toString();
      Path workspacePath = workspaceRoot.directory().toPath();
      Path candidate = workspacePath.resolve(root);
      if (isValid(context, candidate)) {
        strings.add(workspacePath.relativize(candidate).toString());
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
