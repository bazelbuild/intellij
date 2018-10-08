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
import java.util.function.Predicate;

/** Determines which @{link BuildResultHelper} to use for the current project. */
public interface BuildResultHelperProvider {

  ExtensionPointName<BuildResultHelperProvider> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.BuildResultHelperProvider");

  /** Constructs a BuildResultHelper if enabled under the current project for non-sync cases. */
  Optional<BuildResultHelper> createForFiles(Project project, Predicate<String> fileFilter);

  /** Constructs a BuildResultHelper, for the purposes of sync. */
  Optional<BuildResultHelper> createForFilesForSync(
      Project project, BlazeInfo blazeInfo, Predicate<String> fileFilter);

  /**
   * Constructs a new build result helper.
   *
   * @param project The current project.
   * @param fileFilter A filter for the output artifacts you are interested in.
   */
  @MustBeClosed
  static BuildResultHelper forFiles(Project project, Predicate<String> fileFilter) {
    for (BuildResultHelperProvider extension : EP_NAME.getExtensions()) {
      Optional<BuildResultHelper> helper = extension.createForFiles(project, fileFilter);
      if (helper.isPresent()) {
        return helper.get();
      }
    }
    return new BuildResultHelperBep(fileFilter);
  }

  /**
   * Constructs a new build result helper for sync.
   *
   * @param project The current project.
   * @param blazeInfo The latest BlazeInfo data relevant to sync
   * @param fileFilter A filter for the output artifacts you are interested in.
   */
  @MustBeClosed
  static BuildResultHelper forFilesForSync(
      Project project, BlazeInfo blazeInfo, Predicate<String> fileFilter) {
    for (BuildResultHelperProvider extension : EP_NAME.getExtensions()) {
      Optional<BuildResultHelper> helper =
          extension.createForFilesForSync(project, blazeInfo, fileFilter);
      if (helper.isPresent()) {
        return helper.get();
      }
    }
    return new BuildResultHelperBep(fileFilter);
  }
}
