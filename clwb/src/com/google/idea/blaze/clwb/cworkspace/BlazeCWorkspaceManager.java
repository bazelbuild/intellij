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
package com.google.idea.blaze.clwb.cworkspace;

import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.cpp.BlazeCWorkspace;
import com.intellij.openapi.project.Project;
import com.jetbrains.cidr.lang.CPPWorkspaceManager;
import com.jetbrains.cidr.lang.workspace.OCWorkspace;
import com.jetbrains.cidr.lang.workspace.OCWorkspaceManager;

class BlazeCWorkspaceManager extends OCWorkspaceManager {
  private final Project project;
  private final OCWorkspaceManager delegate;

  public BlazeCWorkspaceManager(Project project) {
    this.project = project;
    this.delegate = new CPPWorkspaceManager(project);
  }

  @Override
  public OCWorkspace getWorkspace() {
    if (Blaze.isBlazeProject(project)) {
      return BlazeCWorkspace.getInstance(project);
    }
    // this is a gross hack, necessitated by OCWorkspaceManager being a service, rather than
    // using extension points. We don't actually know which OCWorkspaceManager would be used
    // if this one wasn't overriding -- but we'll guess that it was CPPWorkspaceManager...
    return delegate.getWorkspace();
  }
}
