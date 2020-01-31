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

import com.android.tools.idea.run.AndroidProcessHandler;
import com.android.tools.idea.run.ApkProvisionException;
import com.android.tools.idea.run.ApplicationIdProvider;
import com.android.tools.idea.run.DeviceFutures;
import com.android.tools.idea.run.LaunchInfo;
import com.android.tools.idea.run.LaunchOptions;
import com.android.tools.idea.run.LaunchOptionsProvider;
import com.android.tools.idea.run.LaunchTaskRunner;
import com.android.tools.idea.run.tasks.LaunchTasksProvider;
import com.android.tools.idea.run.ui.ApplyChangesAction;
import com.android.tools.idea.run.ui.CodeSwapAction;
import com.android.tools.idea.stats.RunStats;
import com.google.common.base.MoreObjects;
import com.google.idea.blaze.android.run.runner.BlazeAndroidDeviceSelector;
import com.google.idea.blaze.android.run.runner.BlazeAndroidDeviceSelector.DeviceSession;
import com.google.idea.blaze.android.run.runner.BlazeAndroidRunConfigurationDebuggerManager;
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
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Key;
import javax.annotation.Nullable;

/** State for android_binary and android_test runs. */
public final class BlazeAndroidRunState implements RunProfileState {
  private static final Key<ConsoleView> CONSOLE_VIEW_KEY =
      new Key<>("android.run.state.consoleview");

  private final Module module;
  private final ExecutionEnvironment env;
  private final String launchConfigName;
  private final BlazeAndroidDeviceSelector.DeviceSession deviceSession;
  private final BlazeAndroidRunContext runContext;
  private final LaunchOptions.Builder launchOptionsBuilder;
  private final boolean isDebug;
  private final BlazeAndroidRunConfigurationDebuggerManager debuggerManager;

  public BlazeAndroidRunState(
      Module module,
      ExecutionEnvironment env,
      LaunchOptions.Builder launchOptionsBuilder,
      boolean isDebug,
      DeviceSession deviceSession,
      BlazeAndroidRunContext runContext,
      BlazeAndroidRunConfigurationDebuggerManager debuggerManager) {
    this.module = module;
    this.env = env;
    this.launchConfigName = env.getRunProfile().getName();
    this.deviceSession = deviceSession;
    this.runContext = runContext;
    this.launchOptionsBuilder = launchOptionsBuilder;
    this.isDebug = isDebug;
    this.debuggerManager = debuggerManager;
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
    ProcessHandler processHandler;
    ConsoleView console;

    ApplicationIdProvider applicationIdProvider = runContext.getApplicationIdProvider();

    String applicationId;
    try {
      applicationId = applicationIdProvider.getPackageName();
    } catch (ApkProvisionException e) {
      throw new ExecutionException("Unable to obtain application id", e);
    }

    if (executor instanceof LaunchOptionsProvider) {
      launchOptionsBuilder.addExtraOptions(((LaunchOptionsProvider) executor).getLaunchOptions());
    }

    LaunchTasksProvider launchTasksProvider =
        runContext.getLaunchTasksProvider(launchOptionsBuilder, isDebug, debuggerManager);

    DeviceFutures deviceFutures = deviceSession.deviceFutures;
    assert deviceFutures != null;
    ProcessHandler previousSessionProcessHandler =
        deviceSession.sessionInfo != null ? deviceSession.sessionInfo.getProcessHandler() : null;

    boolean isSwap =
        MoreObjects.firstNonNull(env.getCopyableUserData(CodeSwapAction.KEY), Boolean.FALSE)
            || MoreObjects.firstNonNull(
                env.getCopyableUserData(ApplyChangesAction.KEY), Boolean.FALSE);
    if (!isSwap) {
      // In the case of cold swap, there is an existing process that is connected,
      // but we are going to launch a new one.
      // Detach the previous process handler so that we don't end up with
      // 2 run tabs for the same launch (the existing one and the new one).
      if (previousSessionProcessHandler != null) {
        RunContentManager manager = RunContentManager.getInstance(env.getProject());
        RunContentDescriptor descriptor =
            manager.findContentDescriptor(executor, previousSessionProcessHandler);
        if (descriptor != null) {
          manager.removeRunContent(executor, descriptor);
        }
        previousSessionProcessHandler.detachProcess();
      }

      processHandler =
          new AndroidProcessHandler.Builder(env.getProject())
              .setApplicationId(applicationId)
              .monitorRemoteProcesses(launchTasksProvider.monitorRemoteProcess())
              .build();
      console =
          runContext
              .getConsoleProvider()
              .createAndAttach(module.getProject(), processHandler, executor);
      // Stash the console. When we swap, we need the console, as that has the method to print a
      // hyperlink.
      // (If we only need normal text output, we can call ProcessHandler#notifyTextAvailable
      // instead.)
      processHandler.putCopyableUserData(CONSOLE_VIEW_KEY, console);
    } else {
      assert previousSessionProcessHandler != null
          : "No process handler from previous session, yet current tasks don't create one";
      processHandler = previousSessionProcessHandler;
      console = processHandler.getCopyableUserData(CONSOLE_VIEW_KEY);
      assert console != null;
    }

    LaunchInfo launchInfo = new LaunchInfo(executor, runner, env, runContext.getConsoleProvider());

    LaunchTaskRunner task =
        LaunchTaskRunnerCompat.create(
            module.getProject(),
            launchConfigName,
            applicationId,
            launchInfo,
            processHandler,
            deviceSession.deviceFutures,
            launchTasksProvider,
            RunStats.from(env),
            console::printHyperlink);
    ProgressManager.getInstance().run(task);

    return console == null ? null : new DefaultExecutionResult(console, processHandler);
  }
}
