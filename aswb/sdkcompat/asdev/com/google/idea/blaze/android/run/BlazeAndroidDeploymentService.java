/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.run;

import com.android.tools.idea.run.ApkInfo;
import com.android.tools.idea.run.LaunchOptions;
import com.android.tools.idea.run.blaze.BlazeLaunchTask;
import com.android.tools.idea.run.tasks.DeployTask;
import com.android.tools.idea.run.tasks.DeployTasksCompat;
import com.intellij.openapi.project.Project;
import java.util.Collection;

/** A service that provides {@link DeployTask}. */
public interface BlazeAndroidDeploymentService {
  static BlazeAndroidDeploymentService getInstance(Project project) {
    return project.getService(BlazeAndroidDeploymentService.class);
  }

  /** Returns a {@link DeployTask} to deploy the given files and launch options. */
  BlazeLaunchTask getDeployTask(Collection<ApkInfo> packages, LaunchOptions launchOptions);

  /** A default implementation that uses {@link DeployTasksCompat#createDeployTask}. */
  class DefaultDeploymentService implements BlazeAndroidDeploymentService {
    private final Project project;

    public DefaultDeploymentService(Project project) {
      this.project = project;
    }

    @Override
    public BlazeLaunchTask getDeployTask(
        Collection<ApkInfo> packages, LaunchOptions launchOptions) {
      return DeployTasksCompat.createDeployTask(project, packages, launchOptions);
    }
  }
}
