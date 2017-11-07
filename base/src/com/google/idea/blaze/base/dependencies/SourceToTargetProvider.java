/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.dependencies;

import com.google.common.collect.ImmutableList;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import java.util.List;
import java.util.Optional;

/** Maps a source file to the blaze targets building that source file. */
public interface SourceToTargetProvider {

  ExtensionPointName<SourceToTargetProvider> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.SourceToTargetProvider");

  static boolean hasProvider() {
    return EP_NAME.getExtensions().length != 0;
  }

  /**
   * Returns the blaze targets provided by the first available {@link SourceToTargetProvider} able
   * to handle the given source file.
   */
  static List<TargetInfo> findTargetsBuildingSourceFile(
      Project project, String workspaceRelativePath) {
    for (SourceToTargetProvider provider : EP_NAME.getExtensions()) {
      Optional<List<TargetInfo>> targets =
          provider.getTargetsBuildingSourceFile(project, workspaceRelativePath);
      if (targets.isPresent()) {
        return targets.get();
      }
    }
    return ImmutableList.of();
  }

  /**
   * Query the blaze targets building the given source file.
   *
   * <p>Returns Optional#empty if this provider was unable to query the blaze targets.
   */
  Optional<List<TargetInfo>> getTargetsBuildingSourceFile(
      Project project, String workspaceRelativePath);
}
