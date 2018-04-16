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
package com.google.idea.testing.cidr;

import com.android.tools.ndk.NdkHelper;
import com.intellij.openapi.project.Project;
import com.jetbrains.cidr.lang.workspace.OCWorkspace;
import com.jetbrains.cidr.lang.workspace.OCWorkspaceManager;

/**
 * A stub {@link OCWorkspaceManager} to use for testing. Also allows toggling on C++ support (which
 * may have been disabled by other OCWorkspaceManagers.
 *
 * <p>Once the plugin API ships with a more official OCWorkspaceManager-for-testing, we may be able
 * to switch over to those classes. See: b/32420569
 */
public class StubOCWorkspaceManager extends OCWorkspaceManager {

  private final Project project;
  private final OCWorkspace workspace;

  public StubOCWorkspaceManager(Project project) {
    this.project = project;
    this.workspace = new StubOCWorkspace(project);
  }

  @Override
  public OCWorkspace getWorkspace() {
    return workspace;
  }

  /**
   * Enable C++ language support for testing (a previously registered OCWorkspace which may have
   * disabled language support).
   */
  public void enableCSupportForTesting() {
    NdkHelper.disableCppLanguageSupport(project, false);
  }
}
