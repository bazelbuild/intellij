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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper.GetArtifactsException;
import com.google.idea.blaze.base.command.buildresult.OutputArtifact;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.qsync.BuildGraph;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * A file that tracks what files in the project can be analyzed and what is the status of their
 * dependencies.
 */
public class DependencyTracker {

  private final Project project;

  private final BuildGraph graph;
  private final Set<String> syncedTargets = new HashSet<>();
  private final DependencyBuilder builder;
  private final DependencyCache cache;

  public DependencyTracker(
      Project project, BuildGraph graph, DependencyBuilder builder, DependencyCache cache) {
    this.project = project;
    this.graph = graph;
    this.builder = builder;
    this.cache = cache;
  }

  /** Recursively get all the transitive deps outside the project */
  @Nullable
  public Set<String> getPendingTargets(Project project, VirtualFile vf) {
    BlazeImportSettings settings =
        BlazeImportSettingsManager.getInstance(project).getImportSettings();
    String rel =
        Paths.get(settings.getWorkspaceRoot()).relativize(Paths.get(vf.getPath())).toString();

    Set<String> targets = graph.getFileDependencies(rel);
    if (targets == null) {
      return null;
    }
    return Sets.difference(targets, syncedTargets).immutableCopy();
  }

  public void buildDependenciesForFile(BlazeContext context, List<WorkspacePath> paths)
      throws IOException, GetArtifactsException {

    BlazeImportSettings settings =
        BlazeImportSettingsManager.getInstance(project).getImportSettings();

    WorkspaceRoot workspaceRoot = WorkspaceRoot.fromImportSettings(settings);

    ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
    ImportRoots ir =
        ImportRoots.builder(workspaceRoot, settings.getBuildSystem()).add(projectViewSet).build();

    Set<String> targets = new HashSet<>();
    Set<String> buildTargets = new HashSet<>();
    for (WorkspacePath path : paths) {
      buildTargets.add(graph.getTargetOwner(path.toString()));
      Set<String> t = graph.getFileDependencies(path.toString());
      if (t != null) {
        targets.addAll(t);
      }
    }

    int size = targets.size();
    targets.removeIf(syncedTargets::contains);
    context.output(
        PrintOutput.log(
            String.format("Removing already synced targets %d", size - targets.size())));

    if (targets.isEmpty()) {
      return;
    }

    ImmutableList<OutputArtifact> artifacts =
        builder.build(project, context, buildTargets, ir, workspaceRoot);

    ArrayList<File> newFiles = new ArrayList<>();
    for (OutputArtifact l : artifacts) {
      newFiles.addAll(cache.addArchive(context, l));
    }

    syncedTargets.addAll(targets);

    context.output(PrintOutput.log("Refreshing Vfs..."));
    VfsUtil.markDirtyAndRefresh(true, false, false, newFiles.toArray(new File[] {}));
    context.output(PrintOutput.log("Done"));
  }
}
