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
package com.google.idea.blaze.android.run.test;

import com.android.tools.idea.run.AndroidSessionInfo;
import com.google.idea.blaze.android.run.BlazeAndroidRunConfigurationHandler;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.DefaultProgramRunner;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunContentDescriptor;

/** Program runner for configurations from {@link BlazeAndroidTestRunConfigurationHandler}. */
public class BlazeAndroidTestProgramRunner extends DefaultProgramRunner {
  @Override
  public boolean canRun(String executorId, RunProfile profile) {
    BlazeAndroidRunConfigurationHandler handler =
        BlazeAndroidRunConfigurationHandler.getHandlerFrom(profile);
    if (!(handler instanceof BlazeAndroidTestRunConfigurationHandler)) {
      return false;
    }
    if (!(profile instanceof BlazeCommandRunConfiguration)) {
      return false;
    }
    return DefaultRunExecutor.EXECUTOR_ID.equals(executorId)
        || DefaultDebugExecutor.EXECUTOR_ID.equals(executorId);
  }

  @Override
  protected RunContentDescriptor doExecute(
      final RunProfileState state, final ExecutionEnvironment env) throws ExecutionException {
    RunContentDescriptor descriptor = super.doExecute(state, env);
    if (descriptor != null) {
      ProcessHandler processHandler = descriptor.getProcessHandler();
      assert processHandler != null;

      RunProfile runProfile = env.getRunProfile();
      RunConfiguration runConfiguration =
          (runProfile instanceof RunConfiguration) ? (RunConfiguration) runProfile : null;
      AndroidSessionInfo sessionInfo =
          AndroidSessionInfo.create(
              processHandler,
              descriptor,
              runConfiguration,
              env.getExecutor().getId(),
              env.getExecutor().getActionName(),
              env.getExecutionTarget());
      processHandler.putUserData(AndroidSessionInfo.KEY, sessionInfo);
    }

    return descriptor;
  }

  @Override
  public String getRunnerId() {
    return "AndroidTestProgramRunner";
  }
}
