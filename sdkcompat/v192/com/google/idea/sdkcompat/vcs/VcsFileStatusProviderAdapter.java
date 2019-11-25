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
package com.google.idea.sdkcompat.vcs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vcs.impl.FileStatusManagerImpl;
import com.intellij.openapi.vcs.impl.VcsFileStatusProvider;

/** #api192: From 2019.3, constructor taking only Project is used */
public class VcsFileStatusProviderAdapter extends VcsFileStatusProvider {
  public VcsFileStatusProviderAdapter(Project project) {
    this(
        project,
        (FileStatusManagerImpl) FileStatusManager.getInstance(project),
        ProjectLevelVcsManager.getInstance(project),
        ChangeListManager.getInstance(project),
        VcsDirtyScopeManager.getInstance(project),
        VcsConfiguration.getInstance(project));
  }

  /** #api192: this super() not visible in parent in 2019.3+ */
  private VcsFileStatusProviderAdapter(
      Project project,
      FileStatusManagerImpl fileStatusManager,
      ProjectLevelVcsManager vcsManager,
      ChangeListManager changeListManager,
      VcsDirtyScopeManager dirtyScopeManager,
      VcsConfiguration configuration) {
    super(
        project,
        fileStatusManager,
        vcsManager,
        changeListManager,
        dirtyScopeManager,
        configuration);
  }
}
