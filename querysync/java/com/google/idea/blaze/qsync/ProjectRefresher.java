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
import com.google.idea.blaze.qsync.vcs.VcsState;
import com.google.idea.blaze.qsync.vcs.WorkspaceFileChange;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Project refresher creates an appropriate {@link RefreshOperation} based on the project and
 * current VCS state.
 */
public class ProjectRefresher {

  private final PackageReader packageReader;

  public ProjectRefresher(PackageReader packageReader) {
    this.packageReader = packageReader;
  }

  public FullProjectUpdate startFullUpdate(
      Context context, List<Path> projectIncludes, List<Path> projectExcludes) {
    return new FullProjectUpdate(context, projectIncludes, projectExcludes, packageReader);
  }

  public RefreshOperation startPartialRefresh(
      Context context, BlazeProjectSnapshot currentProject, Optional<VcsState> latestVcsState) {
    if (!currentProject.vcsState().isPresent()) {
      context.output(PrintOutput.output("No VCS state from last query: performing full query"));
      return fullUpdate(context, currentProject, latestVcsState);
    }
    if (!latestVcsState.isPresent()) {
      context.output(
          PrintOutput.output("VCS doesn't support delta updates: performing full query"));
      return fullUpdate(context, currentProject, latestVcsState);
    }
    if (!Objects.equals(
        currentProject.vcsState().get().upstreamRevision, latestVcsState.get().upstreamRevision)) {
      context.output(
          PrintOutput.output(
              "Upstream revision has changed %s -> %s: performing full query",
              currentProject.vcsState().get().upstreamRevision,
              latestVcsState.get().upstreamRevision));
      return fullUpdate(context, currentProject, latestVcsState);
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
            .projectIncludes(currentProject.projectIncludes())
            .projectExcludes(currentProject.projectExcludes())
            .changedFiles(Sets.union(latestVcsState.get().workingSet, revertedChanges))
            .lastQuery(currentProject.queryOutput())
            .build()
            .getAffectedPackages();
    // TODO check affected.isIncomplete() and offer (or just do?) a full sync in that case.

    if (affected.isEmpty()) {
      // this implies that the user was in a clean client, and still is.
      context.output(PrintOutput.output("Nothing has changed, nothing to do."));
      return new NoopProjectRefresh(currentProject);
    }
    return new PartialProjectRefresh(
        context,
        packageReader,
        currentProject,
        latestVcsState,
        affected.getModifiedPackages(),
        affected.getDeletedPackages());
  }

  private RefreshOperation fullUpdate(
      Context context, BlazeProjectSnapshot currentProject, Optional<VcsState> latestVcsState) {
    FullProjectUpdate fullQuery =
        new FullProjectUpdate(
            context,
            currentProject.projectIncludes(),
            currentProject.projectExcludes(),
            packageReader);
    fullQuery.setVcsState(latestVcsState);
    return fullQuery;
  }
}
