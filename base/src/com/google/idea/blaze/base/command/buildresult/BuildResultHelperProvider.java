/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.command.buildresult;

import com.google.errorprone.annotations.MustBeClosed;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;

/** Determines which @{link BuildResultHelper} to use for the current project. */
public interface BuildResultHelperProvider {

  ExtensionPointName<BuildResultHelperProvider> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.BuildResultHelperProvider");

  /** Constructs a BuildResultHelper if enabled under the current project for non-sync cases. */
  @MustBeClosed
  BuildResultHelper doCreate();

  /**
   * Constructs a new build result helper for local builds.
   *
   * @deprecated All new consumers should use {@link
   *     com.google.idea.blaze.base.bazel.BazelBuildSystem.BazelBinary#buildResultProvider} to
   *     support local and remote builds seamlessly. The existing consumers should be migrated to do
   *     the same.
   */
  @Deprecated
  @MustBeClosed
  static BuildResultHelper createForLocalBuild(Project project) {
    return new BuildResultHelperBep.Provider().doCreateForLocalBuild(project).get();
  }
}
