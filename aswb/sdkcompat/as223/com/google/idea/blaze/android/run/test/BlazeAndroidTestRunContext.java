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

import static com.android.tools.idea.run.tasks.DefaultConnectDebuggerTaskKt.getBaseDebuggerTask;
import static com.google.idea.blaze.android.run.binary.BlazeAndroidBinaryNormalBuildRunContextBase.getApkInfoToInstall;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.execution.common.debug.AndroidDebugger;
import com.android.tools.idea.execution.common.debug.AndroidDebuggerState;
import com.android.tools.idea.run.ApkProvider;
import com.android.tools.idea.run.ApkProvisionException;
import com.android.tools.idea.run.ApplicationIdProvider;
import com.android.tools.idea.run.ConsoleProvider;
import com.android.tools.idea.run.LaunchOptions;
import com.android.tools.idea.run.blaze.BlazeLaunchTask;
import com.android.tools.idea.run.blaze.BlazeLaunchTasksProvider;
import com.android.tools.idea.run.editor.ProfilerState;
import com.android.tools.idea.run.tasks.ConnectDebuggerTask;
import com.android.tools.idea.run.tasks.DeployTasksCompat;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.idea.blaze.android.run.deployinfo.BlazeAndroidDeployInfo;
import com.google.idea.blaze.android.run.deployinfo.BlazeApkProviderService;
import com.google.idea.blaze.android.run.runner.ApkBuildStep;
import com.google.idea.blaze.android.run.runner.BlazeAndroidDeviceSelector;
import com.google.idea.blaze.android.run.runner.BlazeAndroidLaunchTasksProvider;
import com.google.idea.blaze.android.run.runner.BlazeAndroidRunContext;
import com.google.idea.blaze.android.run.test.BlazeAndroidTestLaunchMethodsProvider.AndroidTestLaunchMethod;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.smrunner.BlazeTestUiSession;
import com.google.idea.blaze.base.run.testlogs.BlazeTestResultHolder;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.project.Project;
import java.util.List;
import javax.annotation.Nullable;
import org.jetbrains.android.facet.AndroidFacet;

/** Run context for android_test. */
public class BlazeAndroidTestRunContext implements BlazeAndroidRunContext {
  protected final Project project;
  protected final AndroidFacet facet;
  protected final BlazeCommandRunConfiguration runConfiguration;
  protected final ExecutionEnvironment env;
  protected final BlazeAndroidTestRunConfigurationState configState;
  protected final Label label;
  protected final ImmutableList<String> blazeFlags;
  protected final List<Runnable> launchTaskCompleteListeners = Lists.newArrayList();
  protected final ConsoleProvider consoleProvider;
  protected final ApkBuildStep buildStep;
  protected final ApplicationIdProvider applicationIdProvider;
  protected final ApkProvider apkProvider;
  private final BlazeTestResultHolder testResultsHolder = new BlazeTestResultHolder();

  public BlazeAndroidTestRunContext(
      Project project,
      AndroidFacet facet,
      BlazeCommandRunConfiguration runConfiguration,
      ExecutionEnvironment env,
      BlazeAndroidTestRunConfigurationState configState,
      Label label,
      ImmutableList<String> blazeFlags,
      ApkBuildStep buildStep) {
    this.project = project;
    this.facet = facet;
    this.runConfiguration = runConfiguration;
    this.env = env;
    this.label = label;
    this.configState = configState;
    this.buildStep = buildStep;
    this.blazeFlags = blazeFlags;
    switch (configState.getLaunchMethod()) {
      case MOBILE_INSTALL:
      case NON_BLAZE:
        consoleProvider = new AitIdeTestConsoleProvider(runConfiguration, configState);
        break;
      case BLAZE_TEST:
        BlazeTestUiSession session =
            BlazeTestUiSession.create(ImmutableList.of(), testResultsHolder);
        this.consoleProvider = new AitBlazeTestConsoleProvider(project, runConfiguration, session);
        break;
      default:
        throw new IllegalStateException(
            "Unsupported launch method " + configState.getLaunchMethod());
    }
    applicationIdProvider = new BlazeAndroidTestApplicationIdProvider(buildStep);
    apkProvider = BlazeApkProviderService.getInstance().getApkProvider(project, buildStep);
  }

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
  public ApkBuildStep getBuildStep() {
    return buildStep;
  }

  @Override
  public ProfilerState getProfileState() {
    return null;
  }

  @Override
  public BlazeLaunchTasksProvider getLaunchTasksProvider(LaunchOptions.Builder launchOptionsBuilder)
      throws ExecutionException {
    return new BlazeAndroidLaunchTasksProvider(
        project, this, applicationIdProvider, launchOptionsBuilder);
  }

  @Override
  public ImmutableList<BlazeLaunchTask> getDeployTasks(IDevice device, LaunchOptions launchOptions)
      throws ExecutionException {
    if (configState.getLaunchMethod() != AndroidTestLaunchMethod.NON_BLAZE) {
      return ImmutableList.of();
    }
    return ImmutableList.of(
        DeployTasksCompat.createDeployTask(
            project, getApkInfoToInstall(device, launchOptions, apkProvider), launchOptions));
  }

  @Override
  @Nullable
  public BlazeLaunchTask getApplicationLaunchTask(
      LaunchOptions launchOptions, @Nullable Integer userId, String contributorsAmStartOptions)
      throws ExecutionException {
    switch (configState.getLaunchMethod()) {
      case BLAZE_TEST:
        BlazeAndroidTestFilter testFilter =
            new BlazeAndroidTestFilter(
                configState.getTestingType(),
                configState.getClassName(),
                configState.getMethodName(),
                configState.getPackageName());
        return new BlazeAndroidTestLaunchTask(
            project,
            label,
            blazeFlags,
            testFilter,
            this,
            launchOptions.isDebug(),
            testResultsHolder);
      case NON_BLAZE:
      case MOBILE_INSTALL:
        BlazeAndroidDeployInfo deployInfo;
        try {
          deployInfo = buildStep.getDeployInfo();
        } catch (ApkProvisionException e) {
          throw new ExecutionException(e);
        }
        return StockAndroidTestLaunchTask.getStockTestLaunchTask(
            configState, applicationIdProvider, launchOptions.isDebug(), deployInfo, project);
    }
    throw new AssertionError();
  }

  @Override
  @SuppressWarnings({"unchecked", "rawtypes"}) // Raw type from upstream.
  public ConnectDebuggerTask getDebuggerTask(
      AndroidDebugger androidDebugger, AndroidDebuggerState androidDebuggerState) {
    switch (configState.getLaunchMethod()) {
      case BLAZE_TEST:
        return new ConnectBlazeTestDebuggerTask(this, androidDebugger, androidDebuggerState);
      case NON_BLAZE:
      case MOBILE_INSTALL:
        return getBaseDebuggerTask(androidDebugger, androidDebuggerState, env, facet, 30);
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

  @Override
  public Executor getExecutor() {
    return env.getExecutor();
  }

  @Nullable
  @Override
  public Integer getUserId(IDevice device) {
    return null;
  }

  @Override
  public String getAmStartOptions() {
    return "";
  }
}
