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

import com.android.tools.idea.fd.InstantRunUtils;
import com.android.tools.idea.run.AndroidSessionInfo;
import com.google.idea.blaze.android.run.BlazeAndroidRunConfigurationHandler;
import com.google.idea.blaze.android.run.binary.AndroidBinaryLaunchMethodsUtils.AndroidBinaryLaunchMethod;
import com.google.idea.blaze.android.run.binary.mobileinstall.IncrementalInstallDebugExecutor;
import com.google.idea.blaze.android.run.binary.mobileinstall.IncrementalInstallRunExecutor;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.DefaultProgramRunner;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunContentDescriptor;

/** Program runner for configurations from {@link BlazeAndroidBinaryRunConfigurationHandler}. */
public class BlazeAndroidBinaryProgramRunner extends DefaultProgramRunner {
  @Override
  public boolean canRun(String executorId, RunProfile profile) {
    BlazeAndroidRunConfigurationHandler handler =
        BlazeAndroidRunConfigurationHandler.getHandlerFrom(profile);
    if (!(handler instanceof BlazeAndroidBinaryRunConfigurationHandler)) {
      return false;
    }
    // In practice, the stock runner will probably handle all non-incremental-install configs.
    if (DefaultDebugExecutor.EXECUTOR_ID.equals(executorId)
        || DefaultRunExecutor.EXECUTOR_ID.equals(executorId)) {
      return true;
    }
    // Otherwise, the configuration must be a Blaze incremental install configuration running with
    // an incremental install executor.
    AndroidBinaryLaunchMethod launchMethod =
        ((BlazeAndroidBinaryRunConfigurationHandler) handler).getState().getLaunchMethod();
    return (AndroidBinaryLaunchMethod.MOBILE_INSTALL.equals(launchMethod)
            || AndroidBinaryLaunchMethod.MOBILE_INSTALL_V2.equals(launchMethod))
        && (IncrementalInstallDebugExecutor.EXECUTOR_ID.equals(executorId)
            || IncrementalInstallRunExecutor.EXECUTOR_ID.equals(executorId));
  }

  @Override
  protected RunContentDescriptor doExecute(
      final RunProfileState state, final ExecutionEnvironment env) throws ExecutionException {
    RunContentDescriptor descriptor = super.doExecute(state, env);
    if (descriptor != null) {
      ProcessHandler processHandler = descriptor.getProcessHandler();
      assert processHandler != null;

      RunProfile runProfile = env.getRunProfile();
      int uniqueId =
          (runProfile instanceof RunConfigurationBase)
              ? ((RunConfigurationBase) runProfile).getUniqueID()
              : -1;
      AndroidSessionInfo sessionInfo =
          new AndroidSessionInfo(
              processHandler,
              descriptor,
              uniqueId,
              env.getExecutor().getId(),
              env.getExecutor().getActionName(),
              InstantRunUtils.isInstantRunEnabled(env));
      processHandler.putUserData(AndroidSessionInfo.KEY, sessionInfo);
    }

    return descriptor;
  }

  @Override
  public String getRunnerId() {
    return "AndroidBinaryProgramRunner";
  }
}
