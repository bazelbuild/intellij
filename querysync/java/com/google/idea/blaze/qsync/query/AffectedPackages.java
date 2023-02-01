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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.stream.Collectors.joining;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.qsync.query.WorkspaceFileChange.Operation;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

/**
 * Encapsulates build packages that are affected by changes to files in the project view, and logic
 * to calculate that.
 */
@AutoValue
public abstract class AffectedPackages {

  public abstract ImmutableSet<Path> getModifiedPackages();

  public abstract ImmutableSet<Path> getDeletedPackages();

  public boolean isEmpty() {
    return getModifiedPackages().isEmpty() && getDeletedPackages().isEmpty();
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  /**
   * Calculates the set of affected packages based on the project imports & excludes, output from a
   * previous query and a set of modified files in the workspace.
   */
  public static class Builder {
    // Note we don't use AutoValue.Builder here as the state that we accumulate does not exist
    // directly inside AffectedPackages, instead it is derived from it.
    private ImmutableList<Path> projectIncludes;
    private ImmutableList<Path> projectExcludes = ImmutableList.of();
    private QuerySummary lastQuery;
    private ImmutableSet<WorkspaceFileChange> changedFiles;

    Builder() {}

    @CanIgnoreReturnValue
    public Builder projectIncludes(ImmutableList<Path> projectIncludes) {
      this.projectIncludes = Preconditions.checkNotNull(projectIncludes);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder projectExcludes(ImmutableList<Path> projectExcludes) {
      this.projectExcludes = Preconditions.checkNotNull(projectExcludes);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder lastQuery(QuerySummary lastQuery) {
      this.lastQuery = Preconditions.checkNotNull(lastQuery);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder changedFiles(Collection<WorkspaceFileChange> changes) {
      this.changedFiles = ImmutableSet.copyOf(changes);
      return this;
    }

    public AffectedPackages build(Context context) {
      Preconditions.checkNotNull(projectIncludes, "projectIncludes");
      Preconditions.checkNotNull(lastQuery, "lastQuery");
      Preconditions.checkNotNull(changedFiles, "changedFiles");
      List<WorkspaceFileChange> included = Lists.newArrayList();
      List<WorkspaceFileChange> excluded = Lists.newArrayList();
      for (WorkspaceFileChange change : changedFiles) {
        if (isIncluded(change.workspaceRelativePath)) {
          included.add(change);
        } else {
          excluded.add(change);
        }
      }
      if (!excluded.isEmpty()) {
        // TODO should we have some better user messaging here, with the option to perform a full
        //  re-sync?
        context.output(
            PrintOutput.output(
                "Edited %d files outside of your project view, this may cause your project to be"
                    + " out of sync. Files:\n  %s",
                excluded.size(),
                excluded.stream()
                    .map(c -> c.workspaceRelativePath)
                    .map(Path::toString)
                    .collect(joining("\n  "))));
      }

      ImmutableSet.Builder<Path> affectedPackages = ImmutableSet.builder();
      ImmutableSet.Builder<Path> deletedPackages = ImmutableSet.builder();

      ImmutableList<WorkspaceFileChange> buildFileChanges =
          included.stream()
              .filter(c -> c.workspaceRelativePath.getFileName().toString().equals("BUILD"))
              .collect(toImmutableList());
      if (!buildFileChanges.isEmpty()) {
        context.output(
            PrintOutput.log("Edited %d BUILD files, updating project.", buildFileChanges.size()));
        for (WorkspaceFileChange c : buildFileChanges) {
          Path buildPackage = c.workspaceRelativePath.getParent();
          if (c.operation != Operation.ADD) {
            // modifying/deleting an existing package
            if (!lastQuery.getPackages().contains(buildPackage)) {
              context.output(
                  PrintOutput.log(
                      "Modified BUILD file %s not in a known package; your project may be out of"
                          + " sync",
                      c.workspaceRelativePath));
            }
          }
          switch (c.operation) {
            case ADD:
              // Adding a new BUILD files also affects the parent package (if any).
              affectedPackages.add(buildPackage);
              lastQuery.getParentPackage(buildPackage).ifPresent(affectedPackages::add);
              break;
            case DELETE:
              // Deleting a build package only affects the parent (if any).
              deletedPackages.add(buildPackage);
              lastQuery.getParentPackage(buildPackage).ifPresent(affectedPackages::add);
              break;
            case MODIFY:
              affectedPackages.add(buildPackage);
              break;
          }
        }
      }
      // TODO also process changes to file other that BUILD files?
      // Ignoring modifications to source files seems ok (the IDE should pick them up), but adding/
      // removing source files may matter? What about changes to .bzl files?

      return new AutoValue_AffectedPackages(affectedPackages.build(), deletedPackages.build());
    }

    private boolean isIncluded(Path file) {
      for (Path includePath : projectIncludes) {
        if (file.startsWith(includePath)) {
          for (Path excludePath : projectExcludes) {
            if (file.startsWith(excludePath)) {
              return false;
            }
          }
          return true;
        }
      }
      return false;
    }
  }
}
