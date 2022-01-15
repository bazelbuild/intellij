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
package com.google.idea.blaze.android.run.binary;

import static com.google.idea.blaze.android.run.runner.BlazeAndroidLaunchTasksProvider.NATIVE_DEBUGGING_ENABLED;

import com.android.tools.idea.run.LaunchOptions;
import com.android.tools.idea.run.editor.AndroidDebugger;
import com.android.tools.idea.run.editor.AndroidDebuggerState;
import com.android.tools.idea.run.editor.ProfilerState;
import com.android.tools.idea.run.tasks.ConnectDebuggerTask;
import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.android.run.runner.ApkBuildStep;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.project.Project;
import javax.annotation.Nullable;
import org.jetbrains.android.facet.AndroidFacet;

/** Compat for #api212 */
public class BlazeAndroidBinaryNormalBuildRunContext
    extends BlazeAndroidBinaryNormalBuildRunContextBase {
  BlazeAndroidBinaryNormalBuildRunContext(
      Project project,
      AndroidFacet facet,
      RunConfiguration runConfiguration,
      ExecutionEnvironment env,
      BlazeAndroidBinaryRunConfigurationState configState,
      ApkBuildStep buildStep,
      String launchId) {
    super(project, facet, runConfiguration, env, configState, buildStep, launchId);
  }

  @Override
  public void augmentLaunchOptions(LaunchOptions.Builder options) {
    options.setDeploy(true).setOpenLogcatAutomatically(configState.showLogcatAutomatically());
    options.addExtraOptions(
        ImmutableMap.of(
            NATIVE_DEBUGGING_ENABLED, configState.getCommonState().isNativeDebuggingEnabled()));
  }

  @Nullable
  @Override
  @SuppressWarnings("unchecked")
  public ConnectDebuggerTask getDebuggerTask(
      AndroidDebugger androidDebugger, AndroidDebuggerState androidDebuggerState)
      throws ExecutionException {
    return androidDebugger.getConnectDebuggerTask(
        env,
        null,
        applicationIdProvider,
        facet,
        androidDebuggerState,
        runConfiguration.getType().getId());
  }

  @Override
  public Executor getExecutor() {
    return env.getExecutor();
  }

  @Override
  public ProfilerState getProfileState() {
    return configState.getProfilerState();
  }
}
