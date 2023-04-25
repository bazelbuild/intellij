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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.idea.blaze.base.qsync.cache.ArtifactTracker.UpdateResult;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.BlazeProject;
import com.google.idea.blaze.qsync.project.BlazeProjectSnapshot;
import com.intellij.openapi.vfs.VfsUtil;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
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

  private final BlazeProject blazeProject;
  private final DependencyBuilder builder;
  private final DependencyCache cache;

  public DependencyTracker(
      BlazeProject blazeProject,
      DependencyBuilder builder,
      DependencyCache cache) {
    this.blazeProject = blazeProject;
    this.builder = builder;
    this.cache = cache;
  }

  /** Recursively get all the transitive deps outside the project */
  @Nullable
  public Set<Label> getPendingTargets(Path workspaceRelativePath) {
    Preconditions.checkState(!workspaceRelativePath.isAbsolute(), workspaceRelativePath);

    Optional<BlazeProjectSnapshot> currentSnapshot = blazeProject.getCurrent();
    if (currentSnapshot.isEmpty()) {
      return null;
    }
    ImmutableSet<Label> targets = currentSnapshot.get().getFileDependencies(workspaceRelativePath);
    if (targets == null) {
      return null;
    }
    Set<Label> cachedTargets = cache.getCachedTargets();
    return Sets.difference(targets, cachedTargets).immutableCopy();
  }

  /**
   * Builds the external dependencies of the given files, putting the resultant libraries in the
   * shared library directory so that they are picked up by the IDE.
   */
  public void buildDependenciesForFile(BlazeContext context, List<Path> workspaceRelativePaths)
      throws IOException, BuildException {
    workspaceRelativePaths.forEach(path -> Preconditions.checkState(!path.isAbsolute(), path));

    BlazeProjectSnapshot snapshot =
        blazeProject
            .getCurrent()
            .orElseThrow(() -> new IllegalStateException("Sync is not yet complete"));

    Set<Label> targets = new HashSet<>();
    Set<Label> buildTargets = new HashSet<>();
    for (Path workspaceRelativePath : workspaceRelativePaths) {
      Label targetOwner = snapshot.getTargetOwner(workspaceRelativePath);
      if (targetOwner != null) {
        buildTargets.add(targetOwner);
      } else {
        context.output(
            PrintOutput.error(
                "File %s does not seem to be part of a build rule that the IDE supports.",
                workspaceRelativePath));
        context.output(
            PrintOutput.error(
                "If this is a newly added supported rule, please re-sync your project."));
        context.setHasError();
        return;
      }
      ImmutableSet<Label> t = snapshot.getFileDependencies(workspaceRelativePath);
      if (t != null) {
        targets.addAll(t);
      }
    }

    OutputInfo outputInfo = builder.build(context, buildTargets);

    long now = System.nanoTime();
    UpdateResult updateResult = cache.update(targets, outputInfo, context);
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
