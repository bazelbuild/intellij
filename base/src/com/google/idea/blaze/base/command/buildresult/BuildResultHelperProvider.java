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
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import java.util.Optional;

/** Determines which @{link BuildResultHelper} to use for the current project. */
public interface BuildResultHelperProvider {

  ExtensionPointName<BuildResultHelperProvider> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.BuildResultHelperProvider");

  /** Constructs a BuildResultHelper if enabled under the current project for non-sync cases. */
  Optional<BuildResultHelper> doCreate(Project project, BlazeInfo blazeInfo);

  /**
   * Constructs a BuildResultHelper that supports a local BEP and artifacts. This is required
   * because parts of Blaze Plugin implicitly depended on {@link BuildResultHelperProvider#create}
   * returning {@link BuildResultHelper} corresponding to local builds. Eventually, all consumers
   * should be migrated to use {@link #doCreate} and handle local or remote builds seamlessly.
   */
  Optional<BuildResultHelper> doCreateForLocalBuild(Project project);

  /** Constructs a BuildResultHelper, for the purposes of sync. */
  Optional<BuildResultHelper> doCreateForSync(Project project, BlazeInfo blazeInfo);

  /** Constructs a new build result helper. */
  @MustBeClosed
  static BuildResultHelper create(Project project, BlazeInfo blazeInfo) {
    for (BuildResultHelperProvider extension : EP_NAME.getExtensions()) {
      Optional<BuildResultHelper> helper = extension.doCreate(project, blazeInfo);
      if (helper.isPresent()) {
        return helper.get();
      }
    }
    return new BuildResultHelperBep();
  }

  /**
   * Constructs a new build result helper for local builds.
   *
   * @deprecated All new consumers should use {@link #create} to support local and remote builds
   *     seamlessly. The existing consumers should be migrated to do the same.
   */
  @Deprecated
  @MustBeClosed
  static BuildResultHelper createForLocalBuild(Project project) {
    for (BuildResultHelperProvider extension : EP_NAME.getExtensions()) {
      Optional<BuildResultHelper> helper = extension.doCreateForLocalBuild(project);
      if (helper.isPresent()) {
        return helper.get();
      }
    }
    return new BuildResultHelperBep();
  }

  /**
   * Constructs a new build result helper for sync.
   *
   * @param project The current project.
   * @param blazeInfo The latest BlazeInfo data relevant to sync
   */
  @MustBeClosed
  static BuildResultHelper createForSync(Project project, BlazeInfo blazeInfo) {
    for (BuildResultHelperProvider extension : EP_NAME.getExtensions()) {
      Optional<BuildResultHelper> helper = extension.doCreateForSync(project, blazeInfo);
      if (helper.isPresent()) {
        return helper.get();
      }
    }
    return new BuildResultHelperBep();
  }
}
