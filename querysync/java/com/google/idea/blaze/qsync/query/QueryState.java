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
package com.google.idea.blaze.qsync.query;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.common.PrintOutput;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * The query state of a project.
 *
 * <p>This encapsulates state at the time that a sync query was run.
 */
@AutoValue
public abstract class QueryState {

  public abstract ImmutableList<Path> includePaths();

  public abstract ImmutableList<Path> excludePaths();

  public abstract QuerySummary queryOutput();

  public abstract Optional<String> upstreamRevision();

  public abstract Optional<ImmutableSet<WorkspaceFileChange>> workingSet();

  public static Builder builder() {
    return new AutoValue_QueryState.Builder().excludePaths(ImmutableList.of());
  }

  public boolean canPerformDeltaUpdate() {
    return !upstreamRevision().isEmpty() && !workingSet().isEmpty();
  }

  /**
   * Calculates the set of build packages that we must query to perform a delta update to the
   * project state.
   *
   * <p>The set of packages is based on the VCS state at the time of the last query, and the current
   * VCS state.
   *
   * <p>This method must only be called if {@link #canPerformDeltaUpdate()} returns {@code true},
   * otherwise a full query update must be run.
   *
   * @param currentUpstreamRevision the current VCS upstream revision.
   * @param currentWorkingSet the current VCS working set.
   * @param context context for user messages.
   * @return A set of affected packages to query, or {@code null} if a full update must be performed
   *     instead, e.g. because the upstream revision has changed.
   */
  @Nullable
  public AffectedPackages deltaUpdate(
      String currentUpstreamRevision,
      ImmutableSet<WorkspaceFileChange> currentWorkingSet,
      Context context) {
    Preconditions.checkState(canPerformDeltaUpdate(), "No previous VCS state");

    if (!Objects.equals(currentUpstreamRevision, upstreamRevision().get())) {
      context.output(
          PrintOutput.output(
              "Upstream revision has changed: %s -> %s, performing full query.",
              upstreamRevision().get(), currentUpstreamRevision));
      return null;
    }

    // Build the effective working set. This includes the working set as was when the original
    // sync query was run, as it's possible that files have been reverted since then but the
    // earlier query output will reflect the un-reverted file state.

    ImmutableSet<Path> currentWorkingSetFiles =
        currentWorkingSet.stream().map(c -> c.workspaceRelativePath).collect(toImmutableSet());

    // Files that were in the working set previously, but are no longer, must have been reverted.
    // Find them, and then invert them to ensure that all state is updated appropriately.
    ImmutableSet<WorkspaceFileChange> revertedChanges =
        workingSet().get().stream()
            .filter(c -> !currentWorkingSetFiles.contains(c.workspaceRelativePath))
            .map(WorkspaceFileChange::invert)
            .collect(toImmutableSet());

    return AffectedPackages.newBuilder()
        .projectIncludes(includePaths())
        .projectExcludes(excludePaths())
        .lastQuery(queryOutput())
        .changedFiles(Sets.union(currentWorkingSet, revertedChanges))
        .build(context);
  }

  /** Builder for {@link QueryState}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder includePaths(List<Path> value);

    public abstract Builder excludePaths(List<Path> value);

    public abstract Builder queryOutput(QuerySummary value);

    public abstract Builder upstreamRevision(String value);

    public abstract Builder workingSet(ImmutableSet<WorkspaceFileChange> value);

    public abstract QueryState build();
  }
}
