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

import static com.google.common.base.Predicates.not;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.stream.Collectors.joining;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.qsync.query.QuerySummary;
import com.google.idea.blaze.qsync.vcs.WorkspaceFileChange;
import com.google.idea.blaze.qsync.vcs.WorkspaceFileChange.Operation;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Calculates the set of affected packages based on the project imports & excludes, output from a
 * previous query and a set of modified files in the workspace.
 */
@AutoValue
public abstract class AffectedPackagesCalculator {
  abstract Context context();

  abstract ImmutableList<Path> projectIncludes();

  abstract ImmutableList<Path> projectExcludes();

  abstract QuerySummary lastQuery();

  abstract ImmutableSet<WorkspaceFileChange> changedFiles();

  static Builder builder() {
    return new AutoValue_AffectedPackagesCalculator.Builder().projectExcludes(ImmutableList.of());
  }

  public AffectedPackages getAffectedPackages() {
    List<WorkspaceFileChange> projectChanges = Lists.newArrayList();
    List<WorkspaceFileChange> nonProjectChanges = Lists.newArrayList();
    AffectedPackages.Builder result = AffectedPackages.builder();
    for (WorkspaceFileChange change : changedFiles()) {
      if (isIncludedInProject(change.workspaceRelativePath)) {
        projectChanges.add(change);
      } else {
        nonProjectChanges.add(change);
      }
    }
    if (!nonProjectChanges.isEmpty()) {
      // TODO should we have some better user messaging here, with the option to perform a full
      //  re-sync?
      result.setIncomplete(true);
      context()
          .output(
              PrintOutput.output(
                  "Edited %d files outside of your project view, this may cause your project to be"
                      + " out of sync. Files:\n  %s",
                  nonProjectChanges.size(),
                  nonProjectChanges.stream()
                      .map(c -> c.workspaceRelativePath)
                      .map(Path::toString)
                      .collect(joining("\n  "))));
    }

    // Find BUILD files that have been directly affected by edits.
    ImmutableList<WorkspaceFileChange> buildFileChanges =
        projectChanges.stream()
            .filter(c -> c.workspaceRelativePath.getFileName().toString().equals("BUILD"))
            .collect(toImmutableList());
    if (!buildFileChanges.isEmpty()) {
      context().output(PrintOutput.log("Edited %d BUILD files", buildFileChanges.size()));
      for (WorkspaceFileChange c : buildFileChanges) {
        Path buildPackage = c.workspaceRelativePath.getParent();
        if (c.operation != Operation.ADD) {
          // modifying/deleting an existing package
          if (!lastQuery().getPackages().contains(buildPackage)) {
            context()
                .output(
                    PrintOutput.log(
                        "Modified BUILD file %s not in a known package; your project may be out of"
                            + " sync",
                        c.workspaceRelativePath));
            result.setIncomplete(true);
          }
        }
        switch (c.operation) {
          case ADD:
            // Adding a new BUILD files also affects the parent package (if any).
            result.addAffectedPackage(buildPackage);
            lastQuery().getParentPackage(buildPackage).ifPresent(result::addAffectedPackage);
            break;
          case DELETE:
            // Deleting a build package only affects the parent (if any).
            result.addDeletedPackage(buildPackage);
            lastQuery().getParentPackage(buildPackage).ifPresent(result::addAffectedPackage);
            break;
          case MODIFY:
            result.addAffectedPackage(buildPackage);
            break;
        }
      }
    }

    // Find BUILD files that have been affected by edits to a subinclude (.bzl file)
    ImmutableList<Path> affectedBySubinclude =
        changedFiles().stream()
            .map(c -> c.workspaceRelativePath)
            .flatMap(path -> lastQuery().getReverseSubincludeMap().get(path).stream())
            .filter(Objects::nonNull)
            .filter(path -> path.endsWith("BUILD"))
            .collect(toImmutableList());

    long nonProjectBuildAffectedCount =
        affectedBySubinclude.stream().filter(not(this::isIncludedInProject)).count();
    if (nonProjectBuildAffectedCount > 0) {
      context()
          .output(
              PrintOutput.log(
                  "%d BUILD files outside of your project view are affected by changes to their"
                      + " includes; your project may be out of sync",
                  nonProjectBuildAffectedCount));
      result.setIncomplete(true);
    }
    affectedBySubinclude =
        affectedBySubinclude.stream().filter(this::isIncludedInProject).collect(toImmutableList());
    if (!affectedBySubinclude.isEmpty()) {
      context()
          .output(
              PrintOutput.log(
                  "%d BUILD files affected by changes to .bzl files they load",
                  affectedBySubinclude.size()));
      for (Path buildFile : affectedBySubinclude) {
        Path buildPackage = buildFile.getParent();
        if (!lastQuery().getPackages().contains(buildPackage)) {
          context()
              .output(
                  PrintOutput.log(
                      "Affected BUILD file %s not in a known package; your project may be out of"
                          + " sync",
                      buildFile));
          result.setIncomplete(true);
        }
        result.addAffectedPackage(buildPackage);
      }
    }

    // TODO also process changes to file other than BUILD files?
    // Ignoring modifications to source files seems ok (the IDE should pick them up), but adding/
    // removing source files may matter?

    return result.build();
  }

  private boolean isIncludedInProject(Path file) {
    for (Path includePath : projectIncludes()) {
      if (file.startsWith(includePath)) {
        for (Path excludePath : projectExcludes()) {
          if (file.startsWith(excludePath)) {
            return false;
          }
        }
        return true;
      }
    }
    return false;
  }

  /** Builder for {@link AffectedPackagesCalculator}. */
  @AutoValue.Builder
  abstract static class Builder {

    public abstract Builder context(Context value);

    public abstract Builder projectIncludes(ImmutableList<Path> value);

    public abstract Builder projectExcludes(ImmutableList<Path> value);

    public abstract Builder lastQuery(QuerySummary value);

    public abstract Builder changedFiles(Set<WorkspaceFileChange> value);

    public abstract AffectedPackagesCalculator build();
  }
}
