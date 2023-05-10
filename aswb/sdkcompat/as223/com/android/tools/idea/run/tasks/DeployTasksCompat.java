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
package com.android.tools.idea.run.tasks;

import com.android.tools.idea.run.ApkInfo;
import com.android.tools.idea.run.LaunchOptions;
import com.android.tools.idea.run.blaze.BlazeLaunchTask;
import com.android.tools.idea.run.blaze.BlazeLaunchTaskWrapper;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.project.Project;
import java.util.Collection;

/** Compat class for {@link DeployTask} */
public class DeployTasksCompat {
  private static final BoolExperiment updateCodeViaJvmti =
      new BoolExperiment("android.apply.changes", false);

  private DeployTasksCompat() {}

  public static BlazeLaunchTask createDeployTask(
      Project project, Collection<ApkInfo> packages, LaunchOptions launchOptions) {
    // We don't have a device information, fallback to the most conservative
    // install option.
    return new BlazeLaunchTaskWrapper(
        new DeployTask(
            project,
            packages,
            launchOptions.getPmInstallOptions(/* device= */ null),
            launchOptions.getInstallOnAllUsers(),
            launchOptions.getAlwaysInstallWithPm()));
  }
}

