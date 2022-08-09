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
import com.android.tools.idea.run.ApkProvider;
import com.android.tools.idea.run.ApkProvisionException;
import com.android.tools.idea.run.ApplicationIdProvider;
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.ConsoleProvider;
import com.android.tools.idea.run.LaunchOptions;
import com.android.tools.idea.run.editor.AndroidDebugger;
import com.android.tools.idea.run.editor.AndroidDebuggerState;
import com.android.tools.idea.run.editor.ProfilerState;
import com.android.tools.idea.run.tasks.LaunchTask;
import com.android.tools.idea.run.tasks.LaunchTasksProvider;
import com.android.tools.idea.run.util.LaunchStatus;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.idea.blaze.android.run.binary.mobileinstall.MobileInstallBuildStep;
import com.google.idea.blaze.android.run.deployinfo.BlazeAndroidDeployInfo;
import com.google.idea.blaze.android.run.deployinfo.BlazeApkProviderService;
import com.google.idea.blaze.android.run.runner.ApkBuildStep;
import com.google.idea.blaze.android.run.runner.BlazeAndroidDeviceSelector;
import com.google.idea.blaze.android.run.runner.BlazeAndroidLaunchTasksProvider;
import com.google.idea.blaze.android.run.runner.BlazeAndroidRunContext;
import com.google.idea.blaze.android.run.runner.BlazeInstrumentationTestApkBuildStep;
import com.google.idea.blaze.android.run.runner.FullApkBuildStep;
import com.google.idea.blaze.android.run.test.BlazeAndroidTestLaunchMethodsProvider.AndroidTestLaunchMethod;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.smrunner.BlazeTestUiSession;
import com.google.idea.blaze.base.run.smrunner.TestUiSessionProvider;
import com.google.idea.blaze.java.AndroidBlazeRules;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.project.Project;
import java.util.List;
import javax.annotation.Nullable;
import org.jetbrains.android.facet.AndroidFacet;

/** Run context for android_test. */
abstract class BlazeAndroidTestRunContextBase implements BlazeAndroidRunContext {
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

  BlazeAndroidTestRunContextBase(
      Project project,
      AndroidFacet facet,
      BlazeCommandRunConfiguration runConfiguration,
      ExecutionEnvironment env,
      BlazeAndroidTestRunConfigurationState configState,
      Label label,
      ImmutableList<String> blazeFlags,
      ImmutableList<String> exeFlags,
      String launchId) {
    this.project = project;
    this.facet = facet;
    this.runConfiguration = runConfiguration;
    this.env = env;
    this.label = label;
    this.configState = configState;

    if (configState.getLaunchMethod().equals(AndroidTestLaunchMethod.MOBILE_INSTALL)) {
      this.buildStep = new MobileInstallBuildStep(project, label, blazeFlags, exeFlags, launchId);
    } else if (runConfiguration.getTargetKind()
        == AndroidBlazeRules.RuleTypes.ANDROID_INSTRUMENTATION_TEST.getKind()) {
      // android_instrumentation_test builds both test and app target APKs.
      this.buildStep = new BlazeInstrumentationTestApkBuildStep(project, label, blazeFlags);
    } else {
      this.buildStep = new FullApkBuildStep(project, label, blazeFlags);
    }

    this.applicationIdProvider = new BlazeAndroidTestApplicationIdProvider(buildStep);
    this.apkProvider = BlazeApkProviderService.getInstance().getApkProvider(project, buildStep);

    BlazeTestUiSession testUiSession =
        canUseTestUi(env.getExecutor())
            ? TestUiSessionProvider.getInstance(env.getProject())
                .getTestUiSession(ImmutableList.of(label))
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

  // @Override #api211
  public ProfilerState getProfileState() {
    return null;
  }

  @Override
  public LaunchTasksProvider getLaunchTasksProvider(LaunchOptions.Builder launchOptionsBuilder)
      throws ExecutionException {
    return new BlazeAndroidLaunchTasksProvider(
        project, this, applicationIdProvider, launchOptionsBuilder);
  }

  // @Override #api212
  @Nullable
  public LaunchTask getApplicationLaunchTask(
      LaunchOptions launchOptions,
      @Nullable Integer userId,
      String contributorsAmStartOptions,
      LaunchStatus launchStatus)
      throws ExecutionException {
    return getApplicationLaunchTask(
        launchOptions, userId, contributorsAmStartOptions, null, null, launchStatus);
  }

  @SuppressWarnings({"rawtypes"}) // Raw type from upstream.
  @Nullable
  public LaunchTask getApplicationLaunchTask(
      LaunchOptions launchOptions,
      @Nullable Integer userId,
      String contributorsAmStartOptions,
      AndroidDebugger androidDebugger,
      AndroidDebuggerState androidDebuggerState,
      LaunchStatus launchStatus)
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
            configState, applicationIdProvider, launchOptions.isDebug(), deployInfo, launchStatus);
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

  @Override
  public String getAmStartOptions() {
    return "";
  }
}
