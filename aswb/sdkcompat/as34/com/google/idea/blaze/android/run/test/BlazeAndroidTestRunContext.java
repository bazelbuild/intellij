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
package com.google.idea.blaze.android.run.test;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.run.ApkProvisionException;
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.LaunchOptions;
import com.android.tools.idea.run.editor.AndroidDebugger;
import com.android.tools.idea.run.editor.AndroidDebuggerState;
import com.android.tools.idea.run.tasks.DebugConnectorTask;
import com.android.tools.idea.run.tasks.LaunchTask;
import com.android.tools.idea.run.tasks.LaunchTasksProvider;
import com.android.tools.idea.run.util.ProcessHandlerLaunchStatus;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.android.run.deployinfo.BlazeAndroidDeployInfo;
import com.google.idea.blaze.android.run.runner.BlazeAndroidLaunchTasksProvider;
import com.google.idea.blaze.android.run.runner.BlazeAndroidRunConfigurationDebuggerManager;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.project.Project;
import java.util.Set;
import javax.annotation.Nullable;
import org.jetbrains.android.facet.AndroidFacet;

/** Run context for android_test. */
class BlazeAndroidTestRunContext extends BlazeAndroidTestRunContextBase {
  BlazeAndroidTestRunContext(
      Project project,
      AndroidFacet facet,
      BlazeCommandRunConfiguration runConfiguration,
      ExecutionEnvironment env,
      BlazeAndroidTestRunConfigurationState configState,
      Label label,
      ImmutableList<String> blazeFlags,
      ImmutableList<String> exeFlags) {
    super(project, facet, runConfiguration, env, configState, label, blazeFlags, exeFlags);
  }

  @Override
  public LaunchTasksProvider getLaunchTasksProvider(
      LaunchOptions.Builder launchOptionsBuilder,
      boolean isDebug,
      BlazeAndroidRunConfigurationDebuggerManager debuggerManager)
      throws ExecutionException {
    return new BlazeAndroidLaunchTasksProvider(
        project,
        this,
        applicationIdProvider,
        launchOptionsBuilder,
        isDebug,
        false,
        debuggerManager);
  }

  @Nullable
  @Override
  public LaunchTask getApplicationLaunchTask(
      LaunchOptions launchOptions,
      @Nullable Integer userId,
      AndroidDebugger androidDebugger,
      AndroidDebuggerState androidDebuggerState,
      ProcessHandlerLaunchStatus processHandlerLaunchStatus)
      throws ExecutionException {
    switch (configState.getLaunchMethod()) {
      case BLAZE_TEST:
        return new BlazeAndroidTestLaunchTask(
            project,
            label,
            blazeFlags,
            new BlazeAndroidTestFilter(
                configState.getTestingType(),
                configState.getClassName(),
                configState.getMethodName(),
                configState.getPackageName()),
            this,
            launchOptions.isDebug());
      case NON_BLAZE:
      case MOBILE_INSTALL:
        BlazeAndroidDeployInfo deployInfo;
        try {
          deployInfo = buildStep.getDeployInfo();
        } catch (ApkProvisionException e) {
          throw new ExecutionException(e);
        }
        return StockAndroidTestLaunchTask.getStockTestLaunchTask(
            configState,
            applicationIdProvider,
            launchOptions.isDebug(),
            deployInfo,
            processHandlerLaunchStatus);
    }
    throw new AssertionError();
  }

  @Override
  @SuppressWarnings("unchecked")
  public DebugConnectorTask getDebuggerTask(
      AndroidDebugger androidDebugger,
      AndroidDebuggerState androidDebuggerState,
      Set<String> packageIds,
      boolean monitorRemoteProcess)
      throws ExecutionException {
    switch (configState.getLaunchMethod()) {
      case BLAZE_TEST:
        return new ConnectBlazeTestDebuggerTask(
            env.getProject(), androidDebugger, packageIds, applicationIdProvider, this);
      case NON_BLAZE:
      case MOBILE_INSTALL:
        return androidDebugger.getConnectDebuggerTask(
            env,
            null,
            packageIds,
            facet,
            androidDebuggerState,
            runConfiguration.getType().getId(),
            monitorRemoteProcess);
    }
    throw new AssertionError();
  }

  void onLaunchTaskComplete() {
    for (Runnable runnable : launchTaskCompleteListeners) {
      runnable.run();
    }
  }

  void addLaunchTaskCompleteListener(Runnable runnable) {
    launchTaskCompleteListeners.add(runnable);
  }

  @Nullable
  @Override
  public Integer getUserId(IDevice device, ConsolePrinter consolePrinter) {
    return null;
  }
}
