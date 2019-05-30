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
package com.google.idea.blaze.android.run;

import com.android.tools.idea.deploy.DeploymentConfiguration;
import com.android.tools.idea.run.tasks.ApplyChangesTask;
import com.android.tools.idea.run.tasks.ApplyCodeChangesTask;
import com.android.tools.idea.run.util.SwapInfo;
import com.google.common.collect.ImmutableMap;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.List;

/** Compat for apply changes related tasks. #api as3.4 */
public class ApplyChangesCompat {
  /**
   * Creates a new {@link ApplyChangesTask}.
   *
   * <p>#api3.4
   */
  public static ApplyChangesTask newApplyChangesTask(
      Project project, ImmutableMap<String, List<File>> packages) {
    return new ApplyChangesTask(
        project, packages, DeploymentConfiguration.getInstance().APPLY_CHANGES_FALLBACK_TO_RUN);
  }

  /**
   * Creates a new {@link ApplyCodeChangesTask}.
   *
   * <p>#api3.4
   */
  public static ApplyCodeChangesTask newApplyCodeChangesTask(
      Project project, ImmutableMap<String, List<File>> packages) {
    return new ApplyCodeChangesTask(
        project,
        packages,
        DeploymentConfiguration.getInstance().APPLY_CODE_CHANGES_FALLBACK_TO_RUN);
  }

  /**
   * Returns whether the env requests the apply changes action.
   *
   * <p>#api3.5
   */
  public static boolean isApplyChanges(ExecutionEnvironment env) {
    SwapInfo swapInfo = env.getUserData(SwapInfo.SWAP_INFO_KEY);
    return swapInfo != null && swapInfo.getType() == SwapInfo.SwapType.APPLY_CHANGES;
  }

  /**
   * Returns whether the env requests the apply code changes action.
   *
   * <p>#api3.5
   */
  public static boolean isApplyCodeChanges(ExecutionEnvironment env) {
    SwapInfo swapInfo = env.getUserData(SwapInfo.SWAP_INFO_KEY);
    return swapInfo != null && swapInfo.getType() == SwapInfo.SwapType.APPLY_CODE_CHANGES;
  }
}
