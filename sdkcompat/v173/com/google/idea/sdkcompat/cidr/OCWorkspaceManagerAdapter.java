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
package com.google.idea.sdkcompat.cidr;

import com.intellij.openapi.project.Project;
import com.jetbrains.cidr.lang.CPPWorkspaceManager;
import com.jetbrains.cidr.lang.workspace.OCWorkspace;
import com.jetbrains.cidr.lang.workspace.OCWorkspaceManager;

/** Adapter to bridge different SDK versions. */
public abstract class OCWorkspaceManagerAdapter extends OCWorkspaceManager {
  public OCWorkspaceManagerWrapper getDelegate(Project project) {
    return new OCWorkspaceManagerWrapper(new CPPWorkspaceManager(project));
  }

  public abstract OCWorkspace getWorkspace();

  /**
   * Wrap this up because we need a consistent API for other sdkcompat versions and
   * OCWorkspaceManager does not exist from 2018.1
   */
  public class OCWorkspaceManagerWrapper {
    final OCWorkspaceManager manager;

    public OCWorkspaceManagerWrapper(OCWorkspaceManager manager) {
      this.manager = manager;
    }

    public OCWorkspace getWorkspace(Project project) {
      return manager.getWorkspace();
    }
  }
}
