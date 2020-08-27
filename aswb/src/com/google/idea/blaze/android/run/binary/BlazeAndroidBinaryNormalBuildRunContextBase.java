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
package com.google.idea.blaze.android.run.binary;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.deploy.DeploymentConfiguration;
import com.android.tools.idea.gradle.util.DynamicAppUtils;
import com.android.tools.idea.run.ApkFileUnit;
import com.android.tools.idea.run.ApkInfo;
import com.android.tools.idea.run.ApkProvider;
import com.android.tools.idea.run.ApkProvisionException;
import com.android.tools.idea.run.ApplicationIdProvider;
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.ConsoleProvider;
import com.android.tools.idea.run.LaunchOptions;
import com.android.tools.idea.run.activity.DefaultStartActivityFlagsProvider;
import com.android.tools.idea.run.activity.StartActivityFlagsProvider;
import com.android.tools.idea.run.editor.AndroidDebugger;
import com.android.tools.idea.run.editor.AndroidDebuggerState;
import com.android.tools.idea.run.editor.ProfilerState;
import com.android.tools.idea.run.tasks.ApplyChangesTask;
import com.android.tools.idea.run.tasks.ApplyCodeChangesTask;
import com.android.tools.idea.run.tasks.DeployTasksCompat;
import com.android.tools.idea.run.tasks.LaunchTask;
import com.android.tools.idea.run.tasks.LaunchTasksProvider;
import com.android.tools.idea.run.util.LaunchStatus;
import com.android.tools.idea.run.util.SwapInfo;
import com.android.tools.idea.run.util.SwapInfo.SwapType;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.android.run.deployinfo.BlazeAndroidDeployInfo;
import com.google.idea.blaze.android.run.deployinfo.BlazeApkProviderService;
import com.google.idea.blaze.android.run.runner.BlazeAndroidDeviceSelector;
import com.google.idea.blaze.android.run.runner.BlazeAndroidLaunchTasksProvider;
import com.google.idea.blaze.android.run.runner.BlazeAndroidRunConfigurationDebuggerManager;
import com.google.idea.blaze.android.run.runner.BlazeAndroidRunContext;
import com.google.idea.blaze.android.run.runner.BlazeApkBuildStep;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

/** Run context for android_binary. */
public abstract class BlazeAndroidBinaryNormalBuildRunContextBase
    implements BlazeAndroidRunContext {
  private static final BoolExperiment updateCodeViaJvmti =
      new BoolExperiment("android.apply.changes", false);

  protected final Project project;
  protected final AndroidFacet facet;
  protected final RunConfiguration runConfiguration;
  protected final ExecutionEnvironment env;
  protected final BlazeAndroidBinaryRunConfigurationState configState;
  protected final ConsoleProvider consoleProvider;
  protected final BlazeApkBuildStep buildStep;
  protected final ApkProvider apkProvider;
  protected final ApplicationIdProvider applicationIdProvider;

  BlazeAndroidBinaryNormalBuildRunContextBase(
      Project project,
      AndroidFacet facet,
      RunConfiguration runConfiguration,
      ExecutionEnvironment env,
      BlazeAndroidBinaryRunConfigurationState configState,
      BlazeApkBuildStep buildStep) {
    this.project = project;
    this.facet = facet;
    this.runConfiguration = runConfiguration;
    this.env = env;
    this.configState = configState;
    this.consoleProvider = new BlazeAndroidBinaryConsoleProvider(project);
    this.buildStep = buildStep;
    this.apkProvider = BlazeApkProviderService.getInstance().getApkProvider(project, buildStep);
    this.applicationIdProvider = new BlazeAndroidBinaryApplicationIdProvider(buildStep);
  }

  @Override
  public BlazeAndroidDeviceSelector getDeviceSelector() {
    return new BlazeAndroidDeviceSelector.NormalDeviceSelector();
  }

  @Override
  public void augmentLaunchOptions(LaunchOptions.Builder options) {
    options.setDeploy(true).setOpenLogcatAutomatically(configState.showLogcatAutomatically());
    options.addExtraOptions(
        ImmutableMap.of(ProfilerState.ANDROID_PROFILER_STATE_ID, configState.getProfilerState()));
  }

  @Override
  public ConsoleProvider getConsoleProvider() {
    return consoleProvider;
  }

  @Override
  public ApplicationIdProvider getApplicationIdProvider() throws ExecutionException {
    return applicationIdProvider;
  }

  @Override
  public BlazeApkBuildStep getBuildStep() {
    return buildStep;
  }

  @Nullable
  @Override
  public ImmutableList<LaunchTask> getDeployTasks(IDevice device, LaunchOptions launchOptions)
      throws ExecutionException {
    ImmutableMap<String, List<File>> filesToInstall =
        getFilesToInstall(device, launchOptions, apkProvider);
    return ImmutableList.of(getDeployTask(launchOptions, filesToInstall));
  }

  private LaunchTask getDeployTask(
      LaunchOptions launchOptions, ImmutableMap<String, List<File>> filesToInstall) {
    if (updateCodeViaJvmti.getValue()) {
      // Set the appropriate action based on which deployment we're doing.
      SwapInfo swapInfo = env.getUserData(SwapInfo.SWAP_INFO_KEY);
      SwapInfo.SwapType swapType = swapInfo == null ? null : swapInfo.getType();
      if (swapType == SwapType.APPLY_CHANGES) {
        return new ApplyChangesTask(
            project,
            filesToInstall,
            DeploymentConfiguration.getInstance().APPLY_CODE_CHANGES_FALLBACK_TO_RUN);
      } else if (swapType == SwapType.APPLY_CODE_CHANGES) {
        return new ApplyCodeChangesTask(
            project,
            filesToInstall,
            DeploymentConfiguration.getInstance().APPLY_CODE_CHANGES_FALLBACK_TO_RUN);
      }
    }
    return DeployTasksCompat.createDeployTask(project, filesToInstall, launchOptions);
  }

  @Nullable
  @Override
  public Integer getUserId(IDevice device, ConsolePrinter consolePrinter)
      throws ExecutionException {
    return UserIdHelper.getUserIdFromConfigurationState(device, consolePrinter, configState);
  }

  /**
   * Returns a map from applicationId to the list of files to install for that applicationId,
   * excluding any files for features that are disabled.
   */
  public static ImmutableMap<String, List<File>> getFilesToInstall(
      IDevice device, LaunchOptions launchOptions, ApkProvider apkProvider)
      throws ExecutionException {
    Collection<ApkInfo> apks;
    try {
      apks = apkProvider.getApks(device);
    } catch (ApkProvisionException e) {
      throw new ExecutionException(e);
    }
    ImmutableMap.Builder<String, List<File>> filesToInstall = ImmutableMap.builder();
    List<String> disabledFeatures = launchOptions.getDisabledDynamicFeatures();
    for (ApkInfo apkInfo : apks) {
      filesToInstall.put(apkInfo.getApplicationId(), getFilesToInstall(apkInfo, disabledFeatures));
    }
    return filesToInstall.build();
  }

  @NotNull
  private static List<File> getFilesToInstall(ApkInfo apkInfo, List<String> disabledFeatures) {
    if (apkInfo.getFiles().size() > 1) {
      return apkInfo.getFiles().stream()
          .filter(feature -> DynamicAppUtils.isFeatureEnabled(disabledFeatures, feature))
          .map(ApkFileUnit::getApkFile)
          .collect(Collectors.toList());
    } else {
      return ImmutableList.of(apkInfo.getFile());
    }
  }

  @Override
  public LaunchTasksProvider getLaunchTasksProvider(
      LaunchOptions.Builder launchOptionsBuilder,
      boolean isDebug,
      BlazeAndroidRunConfigurationDebuggerManager debuggerManager)
      throws ExecutionException {
    return new BlazeAndroidLaunchTasksProvider(
        project, this, applicationIdProvider, launchOptionsBuilder, isDebug, debuggerManager);
  }

  @Override
  public LaunchTask getApplicationLaunchTask(
      LaunchOptions launchOptions,
      @Nullable Integer userId,
      @NotNull String contributorsAmStartOptions,
      AndroidDebugger androidDebugger,
      AndroidDebuggerState androidDebuggerState,
      LaunchStatus launchStatus)
      throws ExecutionException {
    String extraFlags = UserIdHelper.getFlagsFromUserId(userId);
    if (!contributorsAmStartOptions.isEmpty()) {
      extraFlags += (extraFlags.isEmpty() ? "" : " ") + contributorsAmStartOptions;
    }

    final StartActivityFlagsProvider startActivityFlagsProvider =
        new DefaultStartActivityFlagsProvider(
            androidDebugger, androidDebuggerState, project, launchOptions.isDebug(), extraFlags);

    BlazeAndroidDeployInfo deployInfo;
    try {
      deployInfo = buildStep.getDeployInfo();
    } catch (ApkProvisionException e) {
      throw new ExecutionException(e);
    }

    return BlazeAndroidBinaryApplicationLaunchTaskProvider.getApplicationLaunchTask(
        applicationIdProvider,
        deployInfo.getMergedManifest(),
        configState,
        startActivityFlagsProvider,
        launchStatus);
  }

  @Override
  public String getAmStartOptions() {
    return configState.getAmStartOptions();
  }

  @VisibleForTesting
  public Collection<ApkInfo> getApkInfo(IDevice device) throws ApkProvisionException {
    return apkProvider.getApks(device);
  }
}
