/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
import com.google.idea.blaze.android.run.runner.BlazeAndroidRunConfigurationRunner;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.intellij.execution.runners.ExecutionEnvironment;
import java.util.List;

/** Compat class for implementing TestRecorderBlazeCommandRunConfigurationProxy. */
public class TestRecorderBlazeCommandRunConfigurationProxy
    extends TestRecorderBlazeCommandRunConfigurationProxyBase {

  public TestRecorderBlazeCommandRunConfigurationProxy(
      BlazeCommandRunConfiguration baseConfiguration) {
    super(baseConfiguration);
  }

  @Override
  @Nullable
  public List<ListenableFuture<IDevice>> getDeviceFutures(ExecutionEnvironment environment) {
    return environment
        .getCopyableUserData(BlazeAndroidRunConfigurationRunner.DEVICE_SESSION_KEY)
        .deviceFutures
        .get();
  }
}
