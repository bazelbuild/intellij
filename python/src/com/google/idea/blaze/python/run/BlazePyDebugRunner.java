/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.python.run;

import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.logging.EventLoggingService;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.google.idea.blaze.python.run.BlazePyRunConfigurationRunner.BlazePyDummyRunProfileState;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.xdebugger.XDebugSession;
import com.jetbrains.python.debugger.PyDebugProcess;
import com.jetbrains.python.debugger.PyDebugRunner;
import com.jetbrains.python.run.PythonCommandLineState;
import com.jetbrains.python.run.PythonScriptCommandLineState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import java.net.ServerSocket;

/** Blaze plugin specific {@link PyDebugRunner}. */
public class BlazePyDebugRunner extends PyDebugRunner {

  @Override
  public String getRunnerId() {
    return "BlazePyDebugRunner";
  }

  @Override
  public boolean canRun(String executorId, RunProfile profile) {
    if (!DefaultDebugExecutor.EXECUTOR_ID.equals(executorId)
        || !(profile instanceof BlazeCommandRunConfiguration)) {
      return false;
    }
    BlazeCommandRunConfiguration config = (BlazeCommandRunConfiguration) profile;
    BlazeCommandRunConfigurationCommonState handlerState =
        config.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
    BlazeCommandName command =
        handlerState != null ? handlerState.getCommandState().getCommand() : null;
    return PyDebugUtils.canUsePyDebugger(config.getTargetKind())
        && (BlazeCommandName.TEST.equals(command) || BlazeCommandName.RUN.equals(command));
  }

  @Override
  protected @NotNull PyDebugProcess createDebugProcess(
          @NotNull XDebugSession xDebugSession,
          ServerSocket serverSocket,
          ExecutionResult executionResult,
          PythonCommandLineState pythonCommandLineState) {
    PyDebugProcess process =
        super.createDebugProcess(
            xDebugSession, serverSocket, executionResult, pythonCommandLineState);
    process.setPositionConverter(new BlazePyPositionConverter());
    return process;
  }

  @Override
  protected @NotNull Promise<@Nullable RunContentDescriptor> execute(@NotNull ExecutionEnvironment environment, @NotNull RunProfileState state) throws ExecutionException {
    if (!(state instanceof BlazePyDummyRunProfileState)) {
      return Promises.resolvedPromise();
    }
    return super.execute(environment, ((BlazePyDummyRunProfileState) state).toNativeState(environment));
  }
}
