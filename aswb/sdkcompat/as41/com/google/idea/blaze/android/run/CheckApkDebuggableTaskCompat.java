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
package com.google.idea.blaze.android.run;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.tasks.LaunchResult;
import com.android.tools.idea.run.util.LaunchStatus;
import com.google.idea.blaze.android.run.deployinfo.BlazeAndroidDeployInfo;
import com.google.idea.blaze.android.run.runner.CheckApkDebuggableTask;
import com.intellij.execution.Executor;
import org.jetbrains.annotations.NotNull;

/** Checks APKs to see if they are debuggable and warn the user if they aren't. */
public final class CheckApkDebuggableTaskCompat extends CheckApkDebuggableTask {
  private final BlazeAndroidDeployInfo deployInfo;

  public CheckApkDebuggableTaskCompat(BlazeAndroidDeployInfo deployInfo) {
    this.deployInfo = deployInfo;
  }

  @Override
  public LaunchResult run(
      @NotNull Executor executor,
      @NotNull IDevice device,
      @NotNull LaunchStatus launchStatus,
      @NotNull ConsolePrinter consolePrinter) {
    CheckApkDebuggableTask.checkApkDebuggableTaskDelegate(deployInfo, device, consolePrinter);
    return LaunchResult.success(); // Don't block deployment.
  }
}
