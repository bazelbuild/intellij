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
import com.android.tools.idea.run.ApkInfo;
import com.android.tools.idea.run.ApkProvider;
import com.android.tools.idea.run.ApkProvisionException;
import com.android.tools.idea.run.ApplicationIdProvider;
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.ConsoleProvider;
import com.android.tools.idea.run.LaunchOptions;
import com.android.tools.idea.run.editor.AndroidDebugger;
import com.android.tools.idea.run.editor.AndroidDebuggerState;
import com.android.tools.idea.run.tasks.DebugConnectorTask;
import com.android.tools.idea.run.tasks.DeployApkTask;
import com.android.tools.idea.run.tasks.LaunchTask;
import com.android.tools.idea.run.tasks.LaunchTasksProvider;
import com.android.tools.idea.run.util.ProcessHandlerLaunchStatus;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.idea.blaze.android.run.binary.mobileinstall.BlazeApkBuildStepMobileInstall;
import com.google.idea.blaze.android.run.deployinfo.BlazeAndroidDeployInfo;
import com.google.idea.blaze.android.run.deployinfo.BlazeApkProvider;
import com.google.idea.blaze.android.run.runner.BlazeAndroidDeviceSelector;
import com.google.idea.blaze.android.run.runner.BlazeAndroidLaunchTasksProvider;
import com.google.idea.blaze.android.run.runner.BlazeAndroidRunConfigurationDebuggerManager;
import com.google.idea.blaze.android.run.runner.BlazeAndroidRunContext;
import com.google.idea.blaze.android.run.runner.BlazeApkBuildStep;
import com.google.idea.blaze.android.run.runner.BlazeApkBuildStepNormalBuild;
import com.google.idea.blaze.android.run.test.BlazeAndroidTestLaunchMethodsProvider.AndroidTestLaunchMethod;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.smrunner.BlazeTestUiSession;
import com.google.idea.blaze.base.run.smrunner.TestUiSessionProvider;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.project.Project;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import org.jetbrains.android.facet.AndroidFacet;

/** Run context for android_test. */
class BlazeAndroidTestRunContext implements BlazeAndroidRunContext {

  private final Project project;
  private final AndroidFacet facet;
  private final BlazeCommandRunConfiguration runConfiguration;
  private final ExecutionEnvironment env;
  private final BlazeAndroidTestRunConfigurationState configState;
  private final Label label;
  private final ImmutableList<String> blazeFlags;
  private final List<Runnable> launchTaskCompleteListeners = Lists.newArrayList();
  private final ConsoleProvider consoleProvider;
  private final BlazeApkBuildStep buildStep;
  private final ApplicationIdProvider applicationIdProvider;
  private final ApkProvider apkProvider;

  BlazeAndroidTestRunContext(
      Project project,
      AndroidFacet facet,
      BlazeCommandRunConfiguration runConfiguration,
      ExecutionEnvironment env,
      BlazeAndroidTestRunConfigurationState configState,
      Label label,
      ImmutableList<String> blazeFlags,
      ImmutableList<String> exeFlags) {
    this.project = project;
    this.facet = facet;
    this.runConfiguration = runConfiguration;
    this.env = env;
    this.label = label;
    this.configState = configState;
    this.buildStep =
        configState.getLaunchMethod().equals(AndroidTestLaunchMethod.MOBILE_INSTALL)
            ? new BlazeApkBuildStepMobileInstall(project, label, blazeFlags, exeFlags)
            : new BlazeApkBuildStepNormalBuild(project, label, blazeFlags);
    this.applicationIdProvider = new BlazeAndroidTestApplicationIdProvider(buildStep);
    this.apkProvider = new BlazeApkProvider(project, buildStep);

    BlazeTestUiSession testUiSession =
        canUseTestUi(env.getExecutor())
            ? TestUiSessionProvider.createForTarget(project, runConfiguration.getTarget())
            : null;
    if (testUiSession != null) {
      this.blazeFlags =
          ImmutableList.<String>builder()
              .addAll(testUiSession.getBlazeFlags())
              .addAll(blazeFlags)
              .build();
    } else {
      this.blazeFlags = blazeFlags;
    }
    this.consoleProvider =
        new AndroidTestConsoleProvider(project, runConfiguration, configState, testUiSession);
  }

  private static boolean canUseTestUi(Executor executor) {
    return !isDebugging(executor);
  }

  private static boolean isDebugging(Executor executor) {
    return executor instanceof DefaultDebugExecutor;
  }

  @Override
  public void augmentEnvironment(ExecutionEnvironment env) {}

  @Override
  public BlazeAndroidDeviceSelector getDeviceSelector() {
    return new BlazeAndroidDeviceSelector.NormalDeviceSelector();
  }

  @Override
  public void augmentLaunchOptions(LaunchOptions.Builder options) {
    options.setDeploy(!configState.getLaunchMethod().equals(AndroidTestLaunchMethod.BLAZE_TEST));
  }

  @Override
  public ConsoleProvider getConsoleProvider() {
    return consoleProvider;
  }

  @Override
  public ApplicationIdProvider getApplicationIdProvider() throws ExecutionException {
    return applicationIdProvider;
  }

  @Nullable
  @Override
  public BlazeApkBuildStep getBuildStep() {
    return buildStep;
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

  @Override
  public ImmutableList<LaunchTask> getDeployTasks(IDevice device, LaunchOptions launchOptions)
      throws ExecutionException {
    switch (configState.getLaunchMethod()) {
      case NON_BLAZE:
        // fall through
      case BLAZE_TEST:
        Collection<ApkInfo> apks;
        try {
          apks = apkProvider.getApks(device);
        } catch (ApkProvisionException e) {
          throw new ExecutionException(e);
        }
        return ImmutableList.of(new DeployApkTask(project, launchOptions, apks));
      case MOBILE_INSTALL:
        return ImmutableList.of();
    }
    throw new AssertionError();
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
