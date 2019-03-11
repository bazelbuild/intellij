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
package com.google.idea.sdkcompat.cidr;

import com.intellij.openapi.project.Project;
import com.jetbrains.cidr.lang.workspace.OCWorkspace;
import com.jetbrains.cidr.lang.workspace.OCWorkspaceEventImpl;
import com.jetbrains.cidr.lang.workspace.OCWorkspaceModificationTrackers;
import com.jetbrains.cidr.lang.workspace.OCWorkspaceModificationTrackersImpl;

/** Adapter for {@link OCWorkspaceModificationTrackers}. */
public class OCWorkspaceModificationTrackersCompat {
  /**
   * Notifies the workspace of changes in inputs to the resolve configuration. See {@link
   * com.jetbrains.cidr.lang.workspace.OCWorkspaceListener.OCWorkspaceEvent}.
   *
   * <p>#api183: The Project Model API changed in api 191 with the change 5044004f0216219fc
   */
  public static void incrementModificationTrackers(
      Project project,
      boolean resolveConfigurationsChanged,
      boolean sourceFilesChanged,
      boolean compilerSettingsChanged) {
    OCWorkspaceEventImpl event =
        new OCWorkspaceEventImpl(
            resolveConfigurationsChanged, sourceFilesChanged, compilerSettingsChanged);
    ((OCWorkspaceModificationTrackersImpl)
            OCWorkspace.getInstance(project).getModificationTrackers())
        .fireWorkspaceChanged(event);
  }
}
