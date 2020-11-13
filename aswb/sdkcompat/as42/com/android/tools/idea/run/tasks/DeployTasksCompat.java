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

import com.android.tools.idea.deploy.DeploymentConfiguration;
import com.android.tools.idea.run.LaunchOptions;
import com.android.tools.idea.run.util.SwapInfo;
import com.android.tools.idea.run.util.SwapInfo.SwapType;
import com.google.common.collect.ImmutableMap;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.List;

/** Compat class for {@link DeployTask} */
public class DeployTasksCompat {
  private static final BoolExperiment updateCodeViaJvmti =
      new BoolExperiment("android.apply.changes", false);

  private DeployTasksCompat() {}

  // #api4.0 : Constructor signature changed in 4.1
  public static LaunchTask createDeployTask(
      Project project,
      ImmutableMap<String, List<File>> filesToInstall,
      LaunchOptions launchOptions) {
    // We don't have a device information, fallback to the most conservative
    // install option.
    return new DeployTask(
        project,
        filesToInstall,
        launchOptions.getPmInstallOptions(/*device=*/ null),
        launchOptions.getInstallOnAllUsers(),
        launchOptions.getAlwaysInstallWithPm());
  }

  public static LaunchTask getDeployTask(
      Project project,
      ExecutionEnvironment env,
      LaunchOptions launchOptions,
      ImmutableMap<String, List<File>> filesToInstall) {
    if (updateCodeViaJvmti.getValue()) {
      // Set the appropriate action based on which deployment we're doing.
      SwapInfo swapInfo = env.getUserData(SwapInfo.SWAP_INFO_KEY);
      SwapInfo.SwapType swapType = swapInfo == null ? null : swapInfo.getType();
      if (swapType == SwapType.APPLY_CHANGES) {
        return new ApplyChangesTask(
            project,
            filesToInstall,
            DeploymentConfiguration.getInstance().APPLY_CHANGES_FALLBACK_TO_RUN,
            false);
      } else if (swapType == SwapType.APPLY_CODE_CHANGES) {
        return new ApplyCodeChangesTask(
            project,
            filesToInstall,
            DeploymentConfiguration.getInstance().APPLY_CODE_CHANGES_FALLBACK_TO_RUN,
            false);
      }
    }
    return createDeployTask(project, filesToInstall, launchOptions);
  }
}
