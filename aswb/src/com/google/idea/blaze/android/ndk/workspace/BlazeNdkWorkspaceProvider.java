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
package com.google.idea.blaze.android.ndk.workspace;

import com.android.tools.ndk.workspace.NdkWorkspaceProvider;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.cpp.BlazeCWorkspace;
import com.intellij.openapi.project.Project;
import com.jetbrains.cidr.lang.workspace.OCWorkspace;
import javax.annotation.Nullable;

/** Extension to provide an NDK workspace. */
public class BlazeNdkWorkspaceProvider extends NdkWorkspaceProvider {
  @Nullable
  @Override
  public OCWorkspace findNdkWorkspace(Project project) {
    if (Blaze.isBlazeProject(project)) {
      return BlazeCWorkspace.getInstance(project);
    }
    return null;
  }
}
