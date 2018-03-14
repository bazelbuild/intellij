/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.sdkcompat.cidr;

import com.intellij.openapi.project.Project;
import com.jetbrains.cidr.lang.workspace.OCWorkspaceModificationTrackers;

/** Handles changes to modification trackers between our supported versions. */
public class OCWorkspaceModificationTrackersCompatUtils {

  public static OCWorkspaceModificationTrackers getTrackers(Project project) {
    return OCWorkspaceModificationTrackers.getInstance(project);
  }

  /**
   * Causes symbol tables to be rebuilt and invalidates cidr caches attached to resolve
   * configurations.
   *
   * <p>Must be called inside a write action, on the EDT.
   */
  public static void incrementModificationCounts(Project project) {
    OCWorkspaceModificationTrackers modTrackers = getTrackers(project);
    modTrackers.getProjectFilesListTracker().incModificationCount();
    modTrackers.getSourceFilesListTracker().incModificationCount();
    modTrackers.getSelectedResolveConfigurationTracker().incModificationCount();
    modTrackers.getBuildSettingsChangesTracker().incModificationCount();
  }
}
