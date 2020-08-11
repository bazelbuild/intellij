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
package com.google.idea.blaze.android.run.testrecorder;

import com.android.annotations.Nullable;
import com.android.ddmlib.IDevice;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gct.testrecorder.run.TestRecorderRunConfigurationProxy;
import com.google.idea.blaze.android.run.binary.BlazeAndroidBinaryRunConfigurationHandler;
import com.google.idea.blaze.android.run.binary.BlazeAndroidBinaryRunConfigurationState;
import com.google.idea.blaze.android.run.runner.BlazeAndroidRunConfigurationRunner;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.google.idea.blaze.base.sync.projectstructure.ModuleFinder;
import com.intellij.execution.configurations.LocatableConfigurationBase;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.module.Module;
import java.util.List;

/** {@link TestRecorderRunConfigurationProxy} for blaze. */
public class TestRecorderBlazeCommandRunConfigurationProxy
    implements TestRecorderRunConfigurationProxy {

  private final BlazeCommandRunConfiguration myBaseConfiguration;
  private final BlazeAndroidBinaryRunConfigurationHandler myBaseConfigurationHandler;

  public TestRecorderBlazeCommandRunConfigurationProxy(
      BlazeCommandRunConfiguration baseConfiguration) {
    myBaseConfiguration = baseConfiguration;
    myBaseConfigurationHandler =
        (BlazeAndroidBinaryRunConfigurationHandler) baseConfiguration.getHandler();
  }

  @Override
  public LocatableConfigurationBase getTestRecorderRunConfiguration() {
    return new TestRecorderBlazeCommandRunConfiguration(myBaseConfiguration);
  }

  @Override
  public Module getModule() {
    return ModuleFinder.getInstance(myBaseConfiguration.getProject())
        .findModuleByName(BlazeDataStorage.WORKSPACE_MODULE_NAME);
  }

  @Override
  public boolean isLaunchActivitySupported() {
    String mode = myBaseConfigurationHandler.getState().getMode();

    // Supported launch activities are Default and Specified.
    return BlazeAndroidBinaryRunConfigurationState.LAUNCH_DEFAULT_ACTIVITY.equals(mode)
        || BlazeAndroidBinaryRunConfigurationState.LAUNCH_SPECIFIC_ACTIVITY.equals(mode);
  }

  @Override
  public String getLaunchActivityClass() {
    BlazeAndroidBinaryRunConfigurationState state = myBaseConfigurationHandler.getState();

    if (BlazeAndroidBinaryRunConfigurationState.LAUNCH_SPECIFIC_ACTIVITY.equals(state.getMode())) {
      return state.getActivityClass();
    }

    return "";
  }

  @Nullable
  @Override
  public List<ListenableFuture<IDevice>> getDeviceFutures(ExecutionEnvironment environment) {
    return environment
        .getCopyableUserData(BlazeAndroidRunConfigurationRunner.DEVICE_SESSION_KEY)
        .deviceFutures
        .get();
  }
}
