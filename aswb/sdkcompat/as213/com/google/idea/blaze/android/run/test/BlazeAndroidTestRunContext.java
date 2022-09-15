/*
 * Copyright 2021 The Bazel Authors. All rights reserved.
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

import static com.google.idea.blaze.android.run.binary.BlazeAndroidBinaryNormalBuildRunContext.getApkInfoToInstall;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.run.LaunchOptions;
import com.android.tools.idea.run.editor.AndroidDebugger;
import com.android.tools.idea.run.editor.AndroidDebuggerState;
import com.android.tools.idea.run.tasks.ConnectDebuggerTask;
import com.android.tools.idea.run.tasks.LaunchTask;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.android.run.BlazeAndroidDeploymentService;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.facet.AndroidFacet;

/**
 * Run context for android_test.
 *
 * <p>#api203
 */
public class BlazeAndroidTestRunContext extends BlazeAndroidTestRunContextBase {
  /**
   * Indicates whether we should create deploy tasks when running a test using Blaze.
   *
   * <p>This is unnecessary, but we use an experiment just to be conservative. We can delete this
   * code after it is confirmed that this doesn't cause any issues. See b/246649171.
   */
  private static final BoolExperiment blazeTestForceDeploy =
      new BoolExperiment("aswb.blaze.test.force.deploy", false);

  BlazeAndroidTestRunContext(
      Project project,
      AndroidFacet facet,
      BlazeCommandRunConfiguration runConfiguration,
      ExecutionEnvironment env,
      BlazeAndroidTestRunConfigurationState configState,
      Label label,
      ImmutableList<String> blazeFlags,
      ImmutableList<String> exeFlags,
      String launchId) {
    super(
        project, facet, runConfiguration, env, configState, label, blazeFlags, exeFlags, launchId);
  }

  @Override
  public ImmutableList<LaunchTask> getDeployTasks(IDevice device, LaunchOptions launchOptions)
      throws ExecutionException {
    switch (configState.getLaunchMethod()) {
      case NON_BLAZE:
        return ImmutableList.of(
            BlazeAndroidDeploymentService.getInstance(project)
                .getDeployTask(
                    getApkInfoToInstall(device, launchOptions, apkProvider), launchOptions));
      case BLAZE_TEST:
        if (blazeTestForceDeploy.getValue()) {
          return ImmutableList.of(
              BlazeAndroidDeploymentService.getInstance(project)
                  .getDeployTask(
                      getApkInfoToInstall(device, launchOptions, apkProvider), launchOptions));
        } else {
          return ImmutableList.of();
        }
      case MOBILE_INSTALL:
        return ImmutableList.of();
    }
    throw new AssertionError();
  }

  @Override
  @SuppressWarnings({"unchecked", "rawtypes"}) // Raw type from upstream.
  public ConnectDebuggerTask getDebuggerTask(
      AndroidDebugger androidDebugger, AndroidDebuggerState androidDebuggerState)
      throws ExecutionException {
    switch (configState.getLaunchMethod()) {
      case BLAZE_TEST:
        return new ConnectBlazeTestDebuggerTask(env.getProject(), applicationIdProvider, this);
      case NON_BLAZE:
      case MOBILE_INSTALL:
        return androidDebugger.getConnectDebuggerTask(
            env, applicationIdProvider, facet, androidDebuggerState);
    }
    throw new AssertionError();
  }

  @Override
  public Executor getExecutor() {
    return env.getExecutor();
  }
}
