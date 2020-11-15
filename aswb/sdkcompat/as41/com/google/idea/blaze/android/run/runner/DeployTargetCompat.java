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
package com.google.idea.blaze.android.run.runner;

import com.android.tools.idea.run.DeviceCount;
import com.android.tools.idea.run.DeviceFutures;
import com.android.tools.idea.run.deployment.DeviceAndSnapshotComboBoxTargetProvider.State;
import com.android.tools.idea.run.editor.DeployTarget;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ExecutionEnvironment;
import org.jetbrains.android.facet.AndroidFacet;

/** Compat for {@link DeployTarget}. #api4.1 */
public class DeployTargetCompat {
  private DeployTargetCompat() {}

  public static DeviceFutures getDevices(
      DeployTarget deployTarget,
      AndroidFacet facet,
      DeviceCount single,
      boolean debug,
      int runConfigId) {
    return deployTarget.getDevices(new State(), facet, DeviceCount.SINGLE, debug, runConfigId);
  }

  public static RunProfileState getRunProfileState(
      DeployTarget deployTarget, Executor executor, ExecutionEnvironment env)
      throws ExecutionException {
    return deployTarget.getRunProfileState(executor, env, new State());
  }
}
