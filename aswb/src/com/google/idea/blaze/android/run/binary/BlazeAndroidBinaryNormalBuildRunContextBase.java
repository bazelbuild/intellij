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

import static com.google.idea.blaze.android.run.runner.BlazeAndroidLaunchTasksProvider.NATIVE_DEBUGGING_ENABLED;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.gradle.util.DynamicAppUtils;
import com.android.tools.idea.run.ApkFileUnit;
import com.android.tools.idea.run.ApkInfo;
import com.android.tools.idea.run.ApkProvider;
import com.android.tools.idea.run.ApkProvisionException;
import com.android.tools.idea.run.ApplicationIdProvider;
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.ConsoleProvider;
import com.android.tools.idea.run.LaunchOptions;
import com.android.tools.idea.run.editor.ProfilerState;
import com.android.tools.idea.run.tasks.DeployTasksCompat;
import com.android.tools.idea.run.tasks.LaunchTask;
import com.android.tools.idea.run.tasks.LaunchTasksProvider;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.android.run.deployinfo.BlazeApkProviderService;
import com.google.idea.blaze.android.run.runner.ApkBuildStep;
import com.google.idea.blaze.android.run.runner.BlazeAndroidDeviceSelector;
import com.google.idea.blaze.android.run.runner.BlazeAndroidLaunchTasksProvider;
import com.google.idea.blaze.android.run.runner.BlazeAndroidRunContext;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.project.Project;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

/** Run context for android_binary. */
public abstract class BlazeAndroidBinaryNormalBuildRunContextBase
    implements BlazeAndroidRunContext {
  protected final Project project;
  protected final AndroidFacet facet;
  protected final RunConfiguration runConfiguration;
  protected final ExecutionEnvironment env;
  protected final BlazeAndroidBinaryRunConfigurationState configState;
  protected final ConsoleProvider consoleProvider;
  protected final ApkBuildStep buildStep;
  protected final ApkProvider apkProvider;
  protected final ApplicationIdProvider applicationIdProvider;
  private final String launchId;

  BlazeAndroidBinaryNormalBuildRunContextBase(
      Project project,
      AndroidFacet facet,
      RunConfiguration runConfiguration,
      ExecutionEnvironment env,
      BlazeAndroidBinaryRunConfigurationState configState,
      ApkBuildStep buildStep,
      String launchId) {
    this.project = project;
    this.facet = facet;
    this.runConfiguration = runConfiguration;
    this.env = env;
    this.configState = configState;
    this.consoleProvider = new BlazeAndroidBinaryConsoleProvider(project);
    this.buildStep = buildStep;
    this.apkProvider = BlazeApkProviderService.getInstance().getApkProvider(project, buildStep);
    this.applicationIdProvider = new BlazeAndroidBinaryApplicationIdProvider(buildStep);
    this.launchId = launchId;
  }

  @Override
  public BlazeAndroidDeviceSelector getDeviceSelector() {
    return new BlazeAndroidDeviceSelector.NormalDeviceSelector();
  }

  @Override
  public void augmentLaunchOptions(LaunchOptions.Builder options) {
    options.setDeploy(true).setOpenLogcatAutomatically(configState.showLogcatAutomatically());
    options.addExtraOptions(
        ImmutableMap.of(
            "android.profilers.state", // Not used after #api211
            configState.getProfilerState(),
            NATIVE_DEBUGGING_ENABLED,
            configState.getCommonState().isNativeDebuggingEnabled()));
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
  public ApkBuildStep getBuildStep() {
    return buildStep;
  }

  @Nullable
  @Override
  public Integer getUserId(IDevice device, ConsolePrinter consolePrinter)
      throws ExecutionException {
    return UserIdHelper.getUserIdFromConfigurationState(device, consolePrinter, configState);
  }

  @Override
  public LaunchTasksProvider getLaunchTasksProvider(LaunchOptions.Builder launchOptionsBuilder)
      throws ExecutionException {
    return new BlazeAndroidLaunchTasksProvider(
        project, this, applicationIdProvider, launchOptionsBuilder);
  }

  @Override
  public String getAmStartOptions() {
    return configState.getAmStartOptions();
  }

  // @Override  #api211
  public ProfilerState getProfileState() {
    return configState.getProfilerState();
  }

  @Nullable
  @Override
  public ImmutableList<LaunchTask> getDeployTasks(IDevice device, LaunchOptions launchOptions)
      throws ExecutionException {
    LaunchTask deployTask =
        DeployTasksCompat.getDeployTask(
            project, env, launchOptions, getApkInfoToInstall(device, launchOptions, apkProvider));
    return ImmutableList.of(new DeploymentTimingReporterTask(launchId, deployTask));
  }

  /** Returns a list of APKs excluding any APKs for features that are disabled. */
  public static List<ApkInfo> getApkInfoToInstall(
      IDevice device, LaunchOptions launchOptions, ApkProvider apkProvider)
      throws ExecutionException {
    Collection<ApkInfo> apks;
    try {
      apks = apkProvider.getApks(device);
    } catch (ApkProvisionException e) {
      throw new ExecutionException(e);
    }
    List<String> disabledFeatures = launchOptions.getDisabledDynamicFeatures();
    return apks.stream()
        .map(apk -> getApkInfoToInstall(apk, disabledFeatures))
        .collect(Collectors.toList());
  }

  @NotNull
  private static ApkInfo getApkInfoToInstall(ApkInfo apkInfo, List<String> disabledFeatures) {
    if (apkInfo.getFiles().size() > 1) {
      List<ApkFileUnit> filteredApks =
          apkInfo.getFiles().stream()
              .filter(feature -> DynamicAppUtils.isFeatureEnabled(disabledFeatures, feature))
              .collect(Collectors.toList());
      return new ApkInfo(filteredApks, apkInfo.getApplicationId());
    } else {
      return apkInfo;
    }
  }
}
