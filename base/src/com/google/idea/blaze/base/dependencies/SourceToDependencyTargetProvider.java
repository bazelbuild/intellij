/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.dependencies;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import java.util.List;
import java.util.concurrent.Future;

/**
 * Maps a source file to appropriate blaze target(s) building that source file. Here 'appropriate'
 * means for the purposes of using the targets as build dependencies.
 *
 * <p>This provider is similar to {@link SourceToTargetProvider} but it attempts to return all known
 * blaze target(s) building the source file.
 */
public interface SourceToDependencyTargetProvider {

  ExtensionPointName<SourceToDependencyTargetProvider> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.SourceToDependencyTargetProvider");

  /**
   * Query the blaze targets building the given source file.
   *
   * <p>Future returns null if this provider was unable to query the blaze targets.
   */
  Future<List<TargetInfo>> getTargetsBuildingSourceFile(
      Project project, String workspaceRelativePath);
}
