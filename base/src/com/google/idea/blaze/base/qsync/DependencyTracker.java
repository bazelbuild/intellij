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
package com.google.idea.blaze.base.qsync;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.exception.BuildException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * A service that tracks what files in the project can be analyzed and what is the status of their
 * dependencies.
 */
public interface DependencyTracker {

  /**
   * For a given project targets, returns all the targets outside the project that its source files
   * need to be edited fully. This method return the dependencies for the target with fewest pending
   * so that if dependencies have been built for one, the empty set will be returned even if others
   * have pending dependencies.
   */
  @Nullable
  Set<Label> getPendingExternalDeps(Set<Label> projectTargets);

  /** Recursively get all the transitive deps outside the project */
  @Nullable
  Set<Label> getPendingTargets(Path workspaceRelativePath);

  /**
   * Builds the external dependencies of the given targets, putting the resultant libraries in the
   * shared library directory so that they are picked up by the IDE.
   */
  boolean buildDependenciesForTargets(BlazeContext context, Set<Label> projectTargets)
      throws IOException, BuildException;

  /**
   * Builds the dependencies of the given target, putting the resultant libraries in the shared
   * library directory so that they are picked up by the IDE.
   */
  void buildDependenciesForTarget(BlazeContext context, Label target)
      throws IOException, BuildException;

  /**
   * Returns the list of project targets related to the given workspace paths.
   *
   * @param context Context
   * @param workspaceRelativePath Workspace relative path to find targets for. This may be a source
   *     file, directory or BUILD file.
   * @return Corresponding project targets. For a source file, this is the targets that build that
   *     file. For a BUILD file, it's the set or targets defined in that file. For a directory, it's
   *     the set of all targets defined in all build packages within the directory (recursively).
   */
  TargetsToBuild getProjectTargets(BlazeContext context, Path workspaceRelativePath);

  /**
   * Returns a list of local cache files that build by target provided. Returns Optional.empty() if
   * the target has not yet been built.
   */
  Optional<ImmutableSet<Path>> getCachedArtifacts(Label target);

  /**
   * A data structure that describes what targets were requested to be built and what targets
   * (including transitive ones) are expected to be built as a result.
   */
  class RequestedTargets {
    public final ImmutableSet<Label> buildTargets;
    public final ImmutableSet<Label> expectedDependencyTargets;

    RequestedTargets(
        ImmutableSet<Label> targetsToRequestBuild, ImmutableSet<Label> expectedToBeBuiltTargets) {
      this.buildTargets = targetsToRequestBuild;
      this.expectedDependencyTargets = expectedToBeBuiltTargets;
    }
  }
}
