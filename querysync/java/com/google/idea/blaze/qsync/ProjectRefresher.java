/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.qsync;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.common.vcs.VcsState;
import com.google.idea.blaze.common.vcs.WorkspaceFileChange;
import com.google.idea.blaze.qsync.project.PostQuerySyncData;
import com.google.idea.blaze.qsync.project.ProjectDefinition;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Project refresher creates an appropriate {@link RefreshOperation} based on the project and
 * current VCS state.
 */
public class ProjectRefresher {

  private final PackageReader packageReader;
  private final Path workspaceRoot;

  public ProjectRefresher(PackageReader packageReader, Path workspaceRoot) {
    this.packageReader = packageReader;
    this.workspaceRoot = workspaceRoot;
  }

  public FullProjectUpdate startFullUpdate(Context context, ProjectDefinition spec) {
    return new FullProjectUpdate(context, workspaceRoot, spec, packageReader);
  }

  public RefreshOperation startPartialRefresh(
      Context<?> context,
      PostQuerySyncData currentProject,
      Optional<VcsState> latestVcsState,
      ProjectDefinition latestProjectDefinition) {
    if (!currentProject.projectDefinition().equals(latestProjectDefinition)) {
      context.output(PrintOutput.output("Project definition has changed; performing full query"));
      return fullUpdate(context, latestProjectDefinition, latestVcsState);
    }
    if (!currentProject.vcsState().isPresent()) {
      context.output(PrintOutput.output("No VCS state from last sync: performing full query"));
      return fullUpdate(context, currentProject.projectDefinition(), latestVcsState);
    }
    if (!latestVcsState.isPresent()) {
      context.output(
          PrintOutput.output("VCS doesn't support delta updates: performing full query"));
      return fullUpdate(context, currentProject.projectDefinition(), latestVcsState);
    }
    if (!Objects.equals(
        currentProject.vcsState().get().upstreamRevision, latestVcsState.get().upstreamRevision)) {
      context.output(
          PrintOutput.output(
              "Upstream revision has changed %s -> %s: performing full query",
              currentProject.vcsState().get().upstreamRevision,
              latestVcsState.get().upstreamRevision));
      return fullUpdate(context, currentProject.projectDefinition(), latestVcsState);
    }
    // Build the effective working set. This includes the working set as was when the original
    // sync query was run, as it's possible that files have been reverted since then but the
    // earlier query output will reflect the un-reverted file state.

    ImmutableSet<Path> newWorkingSetFiles =
        latestVcsState.get().workingSet.stream()
            .map(c -> c.workspaceRelativePath)
            .collect(toImmutableSet());

    // Files that were in the working set previously, but are no longer, must have been reverted.
    // Find them, and then invert them to ensure that all state is updated appropriately.
    ImmutableSet<WorkspaceFileChange> revertedChanges =
        currentProject.vcsState().get().workingSet.stream()
            .filter(c -> !newWorkingSetFiles.contains(c.workspaceRelativePath))
            .map(WorkspaceFileChange::invert)
            .collect(toImmutableSet());

    AffectedPackages affected =
        AffectedPackagesCalculator.builder()
            .context(context)
            .projectIncludes(currentProject.projectDefinition().projectIncludes())
            .projectExcludes(currentProject.projectDefinition().projectExcludes())
            .changedFiles(Sets.union(latestVcsState.get().workingSet, revertedChanges))
            .lastQuery(currentProject.querySummary())
            .build()
            .getAffectedPackages();
    // TODO check affected.isIncomplete() and offer (or just do?) a full sync in that case.

    return new PartialProjectRefresh(
        context,
        packageReader,
        currentProject,
        latestVcsState,
        affected.getModifiedPackages(),
        affected.getDeletedPackages());
  }

  private RefreshOperation fullUpdate(
      Context context, ProjectDefinition projectDefinition, Optional<VcsState> latestVcsState) {
    FullProjectUpdate fullQuery = startFullUpdate(context, projectDefinition);
    fullQuery.setVcsState(latestVcsState);
    return fullQuery;
  }
}
