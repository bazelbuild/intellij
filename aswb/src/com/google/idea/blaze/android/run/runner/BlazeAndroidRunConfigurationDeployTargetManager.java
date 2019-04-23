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
package com.google.idea.blaze.android.run.runner;

import com.android.tools.idea.run.DeviceCount;
import com.android.tools.idea.run.LaunchCompatibility;
import com.android.tools.idea.run.TargetSelectionMode;
import com.android.tools.idea.run.ValidationError;
import com.android.tools.idea.run.editor.DeployTarget;
import com.android.tools.idea.run.editor.DeployTargetProvider;
import com.android.tools.idea.run.editor.DeployTargetState;
import com.google.idea.blaze.android.run.state.DeployTargetSettingsState;
import com.intellij.execution.Executor;
import com.intellij.execution.runners.ExecutionEnvironment;
import java.util.List;
import javax.annotation.Nullable;
import org.jetbrains.android.facet.AndroidFacet;

/** Manages deploy target state for run configurations. */
public class BlazeAndroidRunConfigurationDeployTargetManager {
  private static final String TARGET_SELECTION_MODE = TargetSelectionMode.SHOW_DIALOG.name();

  private final boolean isAndroidTest;
  private final List<DeployTargetProvider> deployTargetProviders;
  private final DeployTargetSettingsState deployTargetSettings;

  public BlazeAndroidRunConfigurationDeployTargetManager(
      boolean isAndroidTest,
      List<DeployTargetProvider> deployTargetProviders,
      DeployTargetSettingsState deployTargetSettings) {
    this.isAndroidTest = isAndroidTest;
    this.deployTargetProviders = deployTargetProviders;
    this.deployTargetSettings = deployTargetSettings;
  }

  public List<ValidationError> validate(AndroidFacet facet) {
    return getCurrentDeployTargetState().validate(facet);
  }

  @Nullable
  DeployTarget getDeployTarget(
      Executor executor, ExecutionEnvironment env, AndroidFacet facet, int runConfigId) {
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
              deployTargetSettings.getTargetStatesByProviderId(),
              runConfigId,
              (device) -> LaunchCompatibility.YES);
      if (deployTarget == null) {
        return null;
      }
    } else {
      deployTarget = currentTargetProvider.getDeployTarget();
    }

    return deployTarget;
  }

  DeployTargetState getCurrentDeployTargetState() {
    DeployTargetProvider<? extends DeployTargetState> currentTargetProvider =
        getCurrentDeployTargetProvider();
    return deployTargetSettings.getTargetStateByProviderId(currentTargetProvider.getId());
  }

  // TODO(salguarnieri) Currently the target selection mode is always SHOW_DIALOG.
  // This code is here for future use.
  // If this code still isn't used after ASwB supports native, then we should delete this logic.
  private DeployTargetProvider<? extends DeployTargetState> getCurrentDeployTargetProvider() {
    DeployTargetProvider<? extends DeployTargetState> target =
        getDeployTargetProvider(TARGET_SELECTION_MODE);
    if (target == null) {
      target = getDeployTargetProvider(TargetSelectionMode.SHOW_DIALOG.name());
    }

    assert target != null;
    return target;
  }

  @Nullable
  private DeployTargetProvider<? extends DeployTargetState> getDeployTargetProvider(String id) {
    // Need to suppress unchecked conversion warnings due to third party code
    // not returning DeployTargetProviders with properly attached generic arguments.
    // @see DeployTargetProvider#getProviders()
    for (@SuppressWarnings("unchecked")
    DeployTargetProvider<? extends DeployTargetState> target : deployTargetProviders) {
      if (target.getId().equals(id)) {
        return target;
      }
    }
    return null;
  }

  DeviceCount getDeviceCount() {
    return DeviceCount.SINGLE;
  }
}
