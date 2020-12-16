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


import com.android.tools.idea.profilers.ProfileRunExecutor;
import com.android.tools.idea.run.AndroidProgramRunner;
import com.android.tools.idea.run.AndroidSessionInfo;
import com.google.idea.blaze.android.run.BlazeAndroidRunConfigurationHandler;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.RunContentBuilder;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.fileEditor.FileDocumentManager;

/** Program runner for configurations from {@link BlazeAndroidBinaryRunConfigurationHandler}. */
public class BlazeAndroidBinaryProgramRunner extends AndroidProgramRunner {
  @Override
  public boolean canRun(String executorId, RunProfile profile) {
    BlazeAndroidRunConfigurationHandler handler =
        BlazeAndroidRunConfigurationHandler.getHandlerFrom(profile);
    if (!(handler instanceof BlazeAndroidBinaryRunConfigurationHandler)) {
      return false;
    }
    return (DefaultDebugExecutor.EXECUTOR_ID.equals(executorId)
        || DefaultRunExecutor.EXECUTOR_ID.equals(executorId)
        || ProfileRunExecutor.EXECUTOR_ID.equals(executorId));
  }

  @Override
  protected boolean canRunWithMultipleDevices(String executorId) {
    return false;
  }

  @Override
  protected RunContentDescriptor doExecute(
      final RunProfileState state, final ExecutionEnvironment env) throws ExecutionException {
    FileDocumentManager.getInstance().saveAllDocuments();
    ExecutionResult result = state.execute(env.getExecutor(), this);
    RunContentDescriptor descriptor =
        new RunContentBuilder(result, env).showRunContent(env.getContentToReuse());

    if (descriptor != null) {
      ProcessHandler processHandler = descriptor.getProcessHandler();
      assert processHandler != null;

      RunProfile runProfile = env.getRunProfile();
      RunConfiguration runConfiguration =
          (runProfile instanceof RunConfiguration) ? (RunConfiguration) runProfile : null;

      // The created AndroidSessionInfo is already added to userdata by #create.
      AndroidSessionInfo.create(
          processHandler,
          descriptor,
          runConfiguration,
          env.getExecutor().getId(),
          env.getExecutor().getActionName(),
          env.getExecutionTarget());
    }

    return descriptor;
  }

  @Override
  public String getRunnerId() {
    return "AndroidBinaryProgramRunner";
  }
}
