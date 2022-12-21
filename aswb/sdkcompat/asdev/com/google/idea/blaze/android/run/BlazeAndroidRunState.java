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
package com.google.idea.blaze.android.run;

import com.android.tools.idea.execution.common.processhandler.AndroidProcessHandler;
import com.android.tools.idea.run.ApkProvisionException;
import com.android.tools.idea.run.ApplicationIdProvider;
import com.android.tools.idea.run.DeviceFutures;
import com.android.tools.idea.run.LaunchOptions;
import com.android.tools.idea.run.LaunchTaskRunner;
import com.android.tools.idea.run.tasks.LaunchTasksProvider;
import com.android.tools.idea.stats.RunStats;
import com.google.common.annotations.VisibleForTesting;
import com.google.idea.blaze.android.run.runner.BlazeAndroidDeviceSelector.DeviceSession;
import com.google.idea.blaze.android.run.runner.BlazeAndroidRunContext;
import com.google.idea.blaze.base.run.smrunner.SmRunnerUtils;
import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.progress.ProgressManager;
import javax.annotation.Nullable;

/** State for android_binary and android_test runs. */
public final class BlazeAndroidRunState implements RunProfileState {
  private final ExecutionEnvironment env;
  private final String launchConfigName;
  private final DeviceSession deviceSession;
  private final BlazeAndroidRunContext runContext;
  private final LaunchOptions.Builder launchOptionsBuilder;

  public BlazeAndroidRunState(
      ExecutionEnvironment env,
      LaunchOptions.Builder launchOptionsBuilder,
      DeviceSession deviceSession,
      BlazeAndroidRunContext runContext) {
    this.env = env;
    this.launchConfigName = env.getRunProfile().getName();
    this.deviceSession = deviceSession;
    this.runContext = runContext;
    this.launchOptionsBuilder = launchOptionsBuilder;
  }

  @Nullable
  @Override
  public ExecutionResult execute(Executor executor, ProgramRunner runner)
      throws ExecutionException {
    DefaultExecutionResult result = executeInner(executor, runner);
    if (result == null) {
      return null;
    }
    return SmRunnerUtils.attachRerunFailedTestsAction(result);
  }

  @Nullable
  private DefaultExecutionResult executeInner(Executor executor, ProgramRunner<?> runner)
      throws ExecutionException {
    ApplicationIdProvider applicationIdProvider = runContext.getApplicationIdProvider();
    String applicationId;
    try {
      applicationId = applicationIdProvider.getPackageName();
    } catch (ApkProvisionException e) {
      throw new ExecutionException("Unable to obtain application id", e);
    }
    LaunchTasksProvider launchTasksProvider =
        runContext.getLaunchTasksProvider(launchOptionsBuilder);
    DeviceFutures deviceFutures = deviceSession.deviceFutures;
    assert deviceFutures != null;
    ProcessHandler processHandler = new AndroidProcessHandler(env.getProject(), applicationId);
    ConsoleView console =
        runContext.getConsoleProvider().createAndAttach(env.getProject(), processHandler, executor);
    LaunchTaskRunner task =
        new LaunchTaskRunner(
            env.getProject(),
            launchConfigName,
            applicationId,
            env.getExecutionTarget().getDisplayName(),
            env,
            processHandler,
            deviceFutures,
            launchTasksProvider,
            RunStats.from(env),
            console::printHyperlink);
    ProgressManager.getInstance().run(task);
    return new DefaultExecutionResult(console, processHandler);
  }

  @VisibleForTesting
  public BlazeAndroidRunContext getRunContext() {
    return runContext;
  }
}
