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

import static com.android.tools.idea.run.ui.ApplyChangesAction.APPLY_CHANGES;
import static com.android.tools.idea.run.ui.CodeSwapAction.CODE_SWAP;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.run.ApkInfo;
import com.android.tools.idea.run.ApkProvisionException;
import com.android.tools.idea.run.ApplicationIdProvider;
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.ConsoleProvider;
import com.android.tools.idea.run.LaunchOptions;
import com.android.tools.idea.run.activity.DefaultStartActivityFlagsProvider;
import com.android.tools.idea.run.activity.StartActivityFlagsProvider;
import com.android.tools.idea.run.editor.AndroidDebugger;
import com.android.tools.idea.run.editor.AndroidDebuggerState;
import com.android.tools.idea.run.tasks.DebugConnectorTask;
import com.android.tools.idea.run.tasks.DeployApkTask;
import com.android.tools.idea.run.tasks.LaunchTask;
import com.android.tools.idea.run.tasks.LaunchTasksProvider;
import com.android.tools.idea.run.tasks.UnifiedDeployTask;
import com.android.tools.idea.run.util.ProcessHandlerLaunchStatus;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.android.run.deployinfo.BlazeAndroidDeployInfo;
import com.google.idea.blaze.android.run.deployinfo.BlazeApkProvider;
import com.google.idea.blaze.android.run.runner.BlazeAndroidDeviceSelector;
import com.google.idea.blaze.android.run.runner.BlazeAndroidLaunchTasksProvider;
import com.google.idea.blaze.android.run.runner.BlazeAndroidRunConfigurationDebuggerManager;
import com.google.idea.blaze.android.run.runner.BlazeAndroidRunContext;
import com.google.idea.blaze.android.run.runner.BlazeApkBuildStep;
import com.google.idea.blaze.android.run.runner.BlazeApkBuildStepNormalBuild;
import com.google.idea.blaze.base.model.primitives.Label;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.project.Project;
import java.util.Collection;
import java.util.Set;
import javax.annotation.Nullable;
import org.jetbrains.android.facet.AndroidFacet;

/** Run context for android_binary. */
class BlazeAndroidBinaryNormalBuildRunContext implements BlazeAndroidRunContext {

  private final Project project;
  private final AndroidFacet facet;
  private final RunConfiguration runConfiguration;
  private final ExecutionEnvironment env;
  private final BlazeAndroidBinaryRunConfigurationState configState;
  private final ConsoleProvider consoleProvider;
  private final BlazeApkBuildStepNormalBuild buildStep;
  private final BlazeApkProvider apkProvider;
  private final ApplicationIdProvider applicationIdProvider;

  BlazeAndroidBinaryNormalBuildRunContext(
      Project project,
      AndroidFacet facet,
      RunConfiguration runConfiguration,
      ExecutionEnvironment env,
      BlazeAndroidBinaryRunConfigurationState configState,
      Label label,
      ImmutableList<String> blazeFlags) {
    this.project = project;
    this.facet = facet;
    this.runConfiguration = runConfiguration;
    this.env = env;
    this.configState = configState;
    this.consoleProvider = new BlazeAndroidBinaryConsoleProvider(project);
    this.buildStep = new BlazeApkBuildStepNormalBuild(project, label, blazeFlags);
    this.apkProvider = new BlazeApkProvider(project, buildStep);
    this.applicationIdProvider = new BlazeAndroidBinaryApplicationIdProvider(buildStep);
  }

  @Override
  public void augmentEnvironment(ExecutionEnvironment env) {}

  @Override
  public BlazeAndroidDeviceSelector getDeviceSelector() {
    return new BlazeAndroidDeviceSelector.NormalDeviceSelector();
  }

  @Override
  public void augmentLaunchOptions(LaunchOptions.Builder options) {
    options.setDeploy(true).setOpenLogcatAutomatically(configState.showLogcatAutomatically());
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

  @Override
  public LaunchTasksProvider getLaunchTasksProvider(
      LaunchOptions.Builder launchOptionsBuilder,
      boolean isDebug,
      BlazeAndroidRunConfigurationDebuggerManager debuggerManager)
      throws ExecutionException {
    return new BlazeAndroidLaunchTasksProvider(
        project, this, applicationIdProvider, launchOptionsBuilder, isDebug, true, debuggerManager);
  }

  @Nullable
  @Override
  public ImmutableList<LaunchTask> getDeployTasks(IDevice device, LaunchOptions launchOptions)
      throws ExecutionException {
    Collection<ApkInfo> apks;
    try {
      apks = apkProvider.getApks(device);
    } catch (ApkProvisionException e) {
      throw new ExecutionException(e);
    }

    if (StudioFlags.JVMTI_REFRESH.get()) {
      UnifiedDeployTask.DeployType type = UnifiedDeployTask.DeployType.INSTALL;
      if (Boolean.TRUE.equals(env.getCopyableUserData(APPLY_CHANGES))) {
        type = UnifiedDeployTask.DeployType.FULL_SWAP;
      } else if (Boolean.TRUE.equals(env.getCopyableUserData(CODE_SWAP))) {
        type = UnifiedDeployTask.DeployType.CODE_SWAP;
      }
      return ImmutableList.of(new UnifiedDeployTask(project, apks, type));
    } else {
      return ImmutableList.of(new DeployApkTask(project, launchOptions, apks));
    }
  }

  @Override
  public LaunchTask getApplicationLaunchTask(
      LaunchOptions launchOptions,
      @Nullable Integer userId,
      AndroidDebugger androidDebugger,
      AndroidDebuggerState androidDebuggerState,
      ProcessHandlerLaunchStatus processHandlerLaunchStatus)
      throws ExecutionException {
    final StartActivityFlagsProvider startActivityFlagsProvider =
        new DefaultStartActivityFlagsProvider(
            androidDebugger,
            androidDebuggerState,
            project,
            launchOptions.isDebug(),
            UserIdHelper.getFlagsFromUserId(userId));

    BlazeAndroidDeployInfo deployInfo;
    try {
      deployInfo = buildStep.getDeployInfo();
    } catch (ApkProvisionException e) {
      throw new ExecutionException(e);
    }

    return BlazeAndroidBinaryApplicationLaunchTaskProvider.getApplicationLaunchTask(
        project,
        applicationIdProvider,
        deployInfo.getMergedManifestFile(),
        configState,
        startActivityFlagsProvider,
        processHandlerLaunchStatus);
  }

  @Nullable
  @Override
  @SuppressWarnings("unchecked")
  public DebugConnectorTask getDebuggerTask(
      AndroidDebugger androidDebugger,
      AndroidDebuggerState androidDebuggerState,
      Set<String> packageIds,
      boolean monitorRemoteProcess)
      throws ExecutionException {
    return androidDebugger.getConnectDebuggerTask(
        env,
        null,
        packageIds,
        facet,
        androidDebuggerState,
        runConfiguration.getType().getId(),
        monitorRemoteProcess);
  }

  @Nullable
  @Override
  public Integer getUserId(IDevice device, ConsolePrinter consolePrinter)
      throws ExecutionException {
    return UserIdHelper.getUserIdFromConfigurationState(device, consolePrinter, configState);
  }
}
