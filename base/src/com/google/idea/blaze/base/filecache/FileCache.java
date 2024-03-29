/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.filecache;

import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import javax.annotation.Nullable;

/** A cache of files from the build output. */
public interface FileCache {
  ExtensionPointName<FileCache> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.FileCache");

  /** Name of cache. Used for status messages. */
  String getName();

  /** Called during sync to fully refresh the file cache. */
  void onSync(
      Project project,
      BlazeContext context,
      ProjectViewSet projectViewSet,
      BlazeProjectData projectData,
      @Nullable BlazeProjectData oldProjectData,
      SyncMode syncMode);

  /**
   * Called after a build operation to refresh any updated files.
   *
   * @param buildOutputs outputs generated by the build after which this method is called.
   */
  void refreshFiles(Project project, BlazeContext context, BlazeBuildOutputs buildOutputs);

  /** Called after project open to deserialize the cache state. */
  void initialize(Project project);
}
