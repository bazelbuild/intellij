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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper.GetArtifactsException;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.qsync.cache.ArtifactTracker.UpdateResult;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.qsync.BlazeProject;
import com.google.idea.blaze.qsync.project.BlazeProjectSnapshot;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * A file that tracks what files in the project can be analyzed and what is the status of their
 * dependencies.
 */
public class DependencyTracker {

  private final Project project;

  private final BlazeProject blazeProject;
  private final DependencyBuilder builder;
  private final DependencyCache cache;

  public DependencyTracker(
      Project project,
      BlazeProject blazeProject,
      DependencyBuilder builder,
      DependencyCache cache) {
    this.project = project;
    this.blazeProject = blazeProject;
    this.builder = builder;
    this.cache = cache;
  }

  /** Recursively get all the transitive deps outside the project */
  @Nullable
  public Set<Label> getPendingTargets(Project project, VirtualFile vf) {
    BlazeImportSettings settings =
        BlazeImportSettingsManager.getInstance(project).getImportSettings();
    Path rel = Paths.get(settings.getWorkspaceRoot()).relativize(Paths.get(vf.getPath()));

    Optional<BlazeProjectSnapshot> currentSnapshot = blazeProject.getCurrent();
    if (currentSnapshot.isEmpty()) {
      return null;
    }
    ImmutableSet<Label> targets = currentSnapshot.get().getFileDependencies(rel);
    if (targets == null) {
      return null;
    }
    Set<Label> cachedTargets = cache.getCachedTargets();
    return Sets.difference(targets, cachedTargets).immutableCopy();
  }

  public void buildDependenciesForFile(BlazeContext context, List<WorkspacePath> paths)
      throws IOException, GetArtifactsException {

    BlazeImportSettings settings =
        BlazeImportSettingsManager.getInstance(project).getImportSettings();

    WorkspaceRoot workspaceRoot = WorkspaceRoot.fromImportSettings(settings);

    ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
    ImportRoots ir =
        ImportRoots.builder(workspaceRoot, settings.getBuildSystem()).add(projectViewSet).build();

    BlazeProjectSnapshot snapshot =
        blazeProject
            .getCurrent()
            .orElseThrow(() -> new IllegalStateException("Sync is not yet complete"));

    Set<Label> targets = new HashSet<>();
    Set<Label> buildTargets = new HashSet<>();
    for (WorkspacePath path : paths) {
      buildTargets.add(snapshot.getTargetOwner(path.asPath()));
      ImmutableSet<Label> t = snapshot.getFileDependencies(path.asPath());
      if (t != null) {
        targets.addAll(t);
      }
    }

    OutputInfo outputInfo = builder.build(context, buildTargets, ir, workspaceRoot);

    long now = System.nanoTime();
    UpdateResult updateResult = cache.update(targets, outputInfo);
    long elapsedMs = (System.nanoTime() - now) / 1000000L;
    context.output(
        PrintOutput.log(
            String.format(
                "Updated cache in %d ms: updated %d artifacts, removed %d artifacts",
                elapsedMs, updateResult.updatedFiles().size(), updateResult.removedKeys().size())));
    cache.saveState();

    context.output(PrintOutput.log("Refreshing Vfs..."));
    VfsUtil.markDirtyAndRefresh(
        true,
        false,
        false,
        updateResult.updatedFiles().stream().map(Path::toFile).toArray(File[]::new));
    context.output(PrintOutput.log("Done"));
  }
}
