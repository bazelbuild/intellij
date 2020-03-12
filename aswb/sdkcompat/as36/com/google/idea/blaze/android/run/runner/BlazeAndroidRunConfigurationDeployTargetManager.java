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
package com.google.idea.blaze.android.run.runner;

import com.android.tools.idea.run.LaunchCompatibility;
import com.android.tools.idea.run.TargetSelectionMode;
import com.android.tools.idea.run.editor.DeployTarget;
import com.android.tools.idea.run.editor.DeployTargetProvider;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.runners.ExecutionEnvironment;
import javax.annotation.Nullable;
import org.jetbrains.android.facet.AndroidFacet;

/** An indirection to provide a class compatible with #api3.6 and prior. */
public class BlazeAndroidRunConfigurationDeployTargetManager
    extends BlazeAndroidRunConfigurationDeployTargetManagerBase {
  public BlazeAndroidRunConfigurationDeployTargetManager(boolean isAndroidTest) {
    super(isAndroidTest);
  }

  @Override
  protected DeployTargetProvider getCurrentDeployTargetProvider() {
    DeployTargetProvider targetProvider =
        getDeployTargetProvider(TargetSelectionMode.DEVICE_AND_SNAPSHOT_COMBO_BOX.name());
    assert targetProvider != null;
    return targetProvider;
  }

  @Override
  @Nullable
  DeployTarget getDeployTarget(
      Executor executor, ExecutionEnvironment env, AndroidFacet facet, int runConfigId)
      throws ExecutionException {
    DeployTargetProvider currentTargetProvider = getCurrentDeployTargetProvider();

    DeployTarget deployTarget;
    if (currentTargetProvider.requiresRuntimePrompt()) {
      deployTarget =
          currentTargetProvider.showPrompt(
              executor,
              env,
              facet,
              getDeviceCount(),
              isAndroidTest,
              deployTargetStates,
              runConfigId,
              (device) -> LaunchCompatibility.YES);
      if (deployTarget == null) {
        return null;
      }
    } else {
      deployTarget = currentTargetProvider.getDeployTarget(env.getProject());
    }

    return deployTarget;
  }
}
