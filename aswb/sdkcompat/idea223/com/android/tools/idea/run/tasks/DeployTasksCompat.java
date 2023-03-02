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
import com.android.tools.idea.run.ApkInfo;
import com.android.tools.idea.run.LaunchOptions;
import com.android.tools.idea.run.util.SwapInfo;
import com.android.tools.idea.run.util.SwapInfo.SwapType;
import com.android.tools.idea.gradle.util.EmbeddedDistributionPaths;
import com.google.idea.blaze.android.run.BlazeAndroidDeploymentService;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import java.util.Collection;

/** Compat class for {@link DeployTask} */
public class DeployTasksCompat {
  private static final BoolExperiment updateCodeViaJvmti =
      new BoolExperiment("android.apply.changes", false);

  private DeployTasksCompat() {}

  public static LaunchTask createDeployTask(
      Project project, Collection<ApkInfo> packages, LaunchOptions launchOptions) {
    // We don't have a device information, fallback to the most conservative
    // install option.
    return new DeployTask(
        project,
        packages,
        launchOptions.getPmInstallOptions(/*device=*/ null),
        launchOptions.getInstallOnAllUsers(),
        launchOptions.getAlwaysInstallWithPm(),
        () -> EmbeddedDistributionPaths.getInstance().findEmbeddedInstaller());
  }

  public static LaunchTask getDeployTask(
      Project project,
      ExecutionEnvironment env,
      LaunchOptions launchOptions,
      Collection<ApkInfo> packages) {
    if (updateCodeViaJvmti.getValue()) {
      Computable<String> installPathProvider = () -> EmbeddedDistributionPaths.getInstance().findEmbeddedInstaller();
      // Set the appropriate action based on which deployment we're doing.
      SwapInfo swapInfo = env.getUserData(SwapInfo.SWAP_INFO_KEY);
      SwapInfo.SwapType swapType = swapInfo == null ? null : swapInfo.getType();
      if (swapType == SwapType.APPLY_CHANGES) {
        return new ApplyChangesTask(
            project,
            packages,
            DeploymentConfiguration.getInstance().APPLY_CHANGES_FALLBACK_TO_RUN,
            false,
            installPathProvider);
      } else if (swapType == SwapType.APPLY_CODE_CHANGES) {
        return new ApplyCodeChangesTask(
            project,
            packages,
            DeploymentConfiguration.getInstance().APPLY_CODE_CHANGES_FALLBACK_TO_RUN,
            false,
            installPathProvider);
      }
    }
    return BlazeAndroidDeploymentService.getInstance(project)
        .getDeployTask(packages, launchOptions);
  }
}
