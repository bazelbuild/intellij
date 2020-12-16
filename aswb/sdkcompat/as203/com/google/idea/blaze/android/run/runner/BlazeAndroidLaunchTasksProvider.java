/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.run.runner;

import static com.android.tools.idea.profilers.AndroidProfilerLaunchTaskContributor.isProfilerLaunch;

import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.tools.deployer.ApkVerifierTracker;
import com.android.tools.idea.profilers.AndroidProfilerLaunchTaskContributor;
import com.android.tools.idea.run.AndroidLaunchTasksProvider;
import com.android.tools.idea.run.ApkProvisionException;
import com.android.tools.idea.run.ApplicationIdProvider;
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.LaunchOptions;
import com.android.tools.idea.run.editor.AndroidDebugger;
import com.android.tools.idea.run.editor.AndroidDebuggerState;
import com.android.tools.idea.run.tasks.ClearLogcatTask;
import com.android.tools.idea.run.tasks.DebugConnectorTask;
import com.android.tools.idea.run.tasks.DismissKeyguardTask;
import com.android.tools.idea.run.tasks.LaunchTask;
import com.android.tools.idea.run.tasks.LaunchTasksProvider;
import com.android.tools.idea.run.tasks.ShowLogcatTask;
import com.android.tools.idea.run.util.LaunchStatus;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.idea.blaze.android.run.binary.UserIdHelper;
import com.intellij.execution.ExecutionException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Normal launch tasks provider. #api4.1 */
public class BlazeAndroidLaunchTasksProvider implements LaunchTasksProvider {
  public static final String NATIVE_DEBUGGING_ENABLED = "NATIVE_DEBUGGING_ENABLED";
  private static final Logger LOG = Logger.getInstance(BlazeAndroidLaunchTasksProvider.class);

  private final Project project;
  private final BlazeAndroidRunContext runContext;
  private final ApplicationIdProvider applicationIdProvider;
  private final LaunchOptions.Builder launchOptionsBuilder;

  public BlazeAndroidLaunchTasksProvider(
      Project project,
      BlazeAndroidRunContext runContext,
      ApplicationIdProvider applicationIdProvider,
      LaunchOptions.Builder launchOptionsBuilder) {
    this.project = project;
    this.runContext = runContext;
    this.applicationIdProvider = applicationIdProvider;
    this.launchOptionsBuilder = launchOptionsBuilder;
  }

  @NotNull
  @Override
  public List<LaunchTask> getTasks(
      @NotNull IDevice device,
      @NotNull LaunchStatus launchStatus,
      @NotNull ConsolePrinter consolePrinter)
      throws ExecutionException {
    final List<LaunchTask> launchTasks = Lists.newArrayList();

    String packageName;
    try {
      packageName = applicationIdProvider.getPackageName();
    } catch (ApkProvisionException e) {
      LOG.error(e);
      launchStatus.terminateLaunch("Unable to determine application id: " + e, true);
      return ImmutableList.of();
    }

    Integer userId = runContext.getUserId(device, consolePrinter);
    String userIdFlags = UserIdHelper.getFlagsFromUserId(userId);
    String skipVerification =
        ApkVerifierTracker.getSkipVerificationInstallationFlag(device, packageName);
    String pmInstallOption;
    if (skipVerification != null) {
      pmInstallOption = userIdFlags + " " + skipVerification;
    } else {
      pmInstallOption = userIdFlags;
    }
    launchOptionsBuilder.setPmInstallOptions(d -> pmInstallOption);

    LaunchOptions launchOptions = launchOptionsBuilder.build();

    // NOTE: Task for opening the profiler tool-window should come before deployment
    // to ensure the tool-window opens correctly. This is required because starting
    // the profiler session requires the tool-window to be open.
    if (isProfilerLaunch(launchOptions)) {
      launchTasks.add(new BlazeAndroidOpenProfilerWindowTask(project));
    }

    if (launchOptions.isClearLogcatBeforeStart()) {
      launchTasks.add(new ClearLogcatTask(project));
    }

    launchTasks.add(new DismissKeyguardTask());

    if (launchOptions.isDeploy()) {
      ImmutableList<LaunchTask> deployTasks = runContext.getDeployTasks(device, launchOptions);
      launchTasks.addAll(deployTasks);
    }
    if (launchStatus.isLaunchTerminated()) {
      return ImmutableList.copyOf(launchTasks);
    }

    try {
      if (launchOptions.isDebug()) {
        launchTasks.add(new CheckApkDebuggableTask(runContext.getBuildStep().getDeployInfo()));
      }

      ImmutableList.Builder<String> amStartOptions = ImmutableList.builder();
      amStartOptions.add(runContext.getAmStartOptions());
      if (isProfilerLaunch(launchOptions)) {
        amStartOptions.add(
            AndroidProfilerLaunchTaskContributor.getAmStartOptions(
                project, packageName, launchOptions, device));
        launchTasks.add(
            new AndroidProfilerLaunchTaskContributor.AndroidProfilerToolWindowLaunchTask(
                project, launchOptions, packageName));
      }

      // Do not get debugger state directly from the debugger itself.
      // See BlazeAndroidDebuggerService#getDebuggerState for an explanation.
      BlazeAndroidDebuggerService debuggerService =
          BlazeAndroidDebuggerService.getInstance(project);
      AndroidDebugger debugger =
          debuggerService.getDebugger(isNativeDebuggingEnabled(launchOptions));
      AndroidDebuggerState debuggerState = debuggerService.getDebuggerState(debugger);
      LaunchTask appLaunchTask =
          runContext.getApplicationLaunchTask(
              launchOptions,
              userId,
              String.join(" ", amStartOptions.build()),
              debugger,
              debuggerState,
              launchStatus);
      if (appLaunchTask != null) {
        launchTasks.add(appLaunchTask);
      }
    } catch (ApkProvisionException e) {
      LOG.error(e);
      launchStatus.terminateLaunch("Unable to determine application id: " + e, true);
      return ImmutableList.of();
    } catch (ExecutionException e) {
      launchStatus.terminateLaunch(e.getMessage(), true);
      return ImmutableList.of();
    }

    if (!launchOptions.isDebug() && launchOptions.isOpenLogcatAutomatically()) {
      launchTasks.add(new ShowLogcatTask(project, packageName));
    }

    return ImmutableList.copyOf(launchTasks);
  }

  @Nullable
  @Override
  public DebugConnectorTask getConnectDebuggerTask(
      @NotNull LaunchStatus launchStatus, @Nullable AndroidVersion version) {
    LaunchOptions launchOptions = launchOptionsBuilder.build();
    if (!launchOptions.isDebug()) {
      return null;
    }
    Set<String> packageIds = Sets.newHashSet();
    try {
      String packageName = applicationIdProvider.getPackageName();
      packageIds.add(packageName);
    } catch (ApkProvisionException e) {
      Logger.getInstance(AndroidLaunchTasksProvider.class).error(e);
    }

    try {
      String packageName = applicationIdProvider.getTestPackageName();
      if (packageName != null) {
        packageIds.add(packageName);
      }
    } catch (ApkProvisionException e) {
      // not as severe as failing to obtain package id for main application
      Logger.getInstance(AndroidLaunchTasksProvider.class)
          .warn(
              "Unable to obtain test package name, will not connect debugger "
                  + "if tests don't instantiate main application");
    }

    // Do not get debugger state directly from the debugger itself.
    // See BlazeAndroidDebuggerService#getDebuggerState for an explanation.
    BlazeAndroidDebuggerService debuggerService = BlazeAndroidDebuggerService.getInstance(project);
    AndroidDebugger debugger = debuggerService.getDebugger(isNativeDebuggingEnabled(launchOptions));
    AndroidDebuggerState debuggerState = debuggerService.getDebuggerState(debugger);
    if (debugger == null || debuggerState == null) {
      return null;
    }

    try {
      return runContext.getDebuggerTask(debugger, debuggerState, packageIds);
    } catch (ExecutionException e) {
      launchStatus.terminateLaunch(e.getMessage(), true);
      return null;
    }
  }

  @Override
  public String getLaunchTypeDisplayName() {
    return "Launch";
  }

  private boolean isNativeDebuggingEnabled(LaunchOptions launchOptions) {
    Object flag = launchOptions.getExtraOption(NATIVE_DEBUGGING_ENABLED);
    return flag instanceof Boolean && (Boolean) flag;
  }
}
