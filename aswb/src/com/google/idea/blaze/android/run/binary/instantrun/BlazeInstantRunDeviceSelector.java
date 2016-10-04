/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.run.binary.instantrun;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.fd.InstantRunManager;
import com.android.tools.idea.fd.InstantRunUtils;
import com.android.tools.idea.run.AndroidSessionInfo;
import com.android.tools.idea.run.DeviceFutures;
import com.google.idea.blaze.android.run.runner.BlazeAndroidDeviceSelector;
import com.google.idea.blaze.android.run.runner.BlazeAndroidRunConfigurationDeployTargetManager;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.project.Project;
import java.util.List;
import javax.annotation.Nullable;
import org.jetbrains.android.facet.AndroidFacet;

/** Tries to reuse devices from a previous session. */
public class BlazeInstantRunDeviceSelector implements BlazeAndroidDeviceSelector {
  NormalDeviceSelector normalDeviceSelector = new NormalDeviceSelector();

  @Override
  public DeviceSession getDevice(
      Project project,
      AndroidFacet facet,
      BlazeAndroidRunConfigurationDeployTargetManager deployTargetManager,
      Executor executor,
      ExecutionEnvironment env,
      AndroidSessionInfo info,
      boolean debug,
      int runConfigId)
      throws ExecutionException {
    DeviceFutures deviceFutures = null;
    if (info != null) {
      // if there is an existing previous session,
      // then see if we can detect devices to fast deploy to
      deviceFutures = getFastDeployDevices(executor, info);

      if (InstantRunUtils.isReRun(env)) {
        info.getProcessHandler().destroyProcess();
        info = null;
      }
    }

    if (deviceFutures != null) {
      return new DeviceSession(null, deviceFutures, info);
    }

    // Fall back to normal device selection
    return normalDeviceSelector.getDevice(
        project, facet, deployTargetManager, executor, env, info, debug, runConfigId);
  }

  @Nullable
  private static DeviceFutures getFastDeployDevices(Executor executor, AndroidSessionInfo info) {
    if (!info.getExecutorId().equals(executor.getId())) {
      String msg =
          String.format(
              "Cannot Instant Run since old executor (%1$s) doesn't match current executor (%2$s)",
              info.getExecutorId(), executor.getId());
      InstantRunManager.LOG.info(msg);
      return null;
    }

    List<IDevice> devices = info.getDevices();
    if (devices == null || devices.isEmpty()) {
      InstantRunManager.LOG.info(
          "Cannot Instant Run since we could not locate "
              + "the devices from the existing launch session");
      return null;
    }

    if (devices.size() > 1) {
      InstantRunManager.LOG.info(
          "Last run was on > 1 device, not reusing devices and prompting again");
      return null;
    }

    return DeviceFutures.forDevices(devices);
  }
}
