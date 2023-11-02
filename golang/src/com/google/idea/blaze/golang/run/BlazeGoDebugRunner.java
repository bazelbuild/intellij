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
package com.google.idea.blaze.golang.run;

import com.goide.dlv.DlvDebugProcess;
import com.goide.dlv.DlvDisconnectOption;
import com.goide.dlv.DlvRemoteVmConnection;
import com.goide.execution.GoBuildingRunner;
import com.goide.execution.application.GoApplicationRunningState;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.logging.EventLoggingService;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.RuleType;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.google.idea.blaze.golang.run.BlazeGoRunConfigurationRunner.BlazeGoDummyDebugProfileState;
import com.google.idea.sdkcompat.go.GoSdkCompat;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugProcessStarter;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;
import org.jetbrains.debugger.connection.RemoteVmConnection;

/** Blaze plugin specific {@link com.goide.execution.GoBuildingRunner}. */
public class BlazeGoDebugRunner extends GoBuildingRunner {
  @Override
  public String getRunnerId() {
    return "BlazeGoDebugRunner";
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
    Kind kind = config.getTargetKind();
    return kind != null
        && kind.hasLanguage(LanguageClass.GO)
        && (kind.getRuleType().equals(RuleType.BINARY) || kind.getRuleType().equals(RuleType.TEST))
        && (BlazeCommandName.TEST.equals(command) || BlazeCommandName.RUN.equals(command));
  }

  @Override
  protected Promise<RunContentDescriptor> execute(
      ExecutionEnvironment environment, RunProfileState state) throws ExecutionException {
    if (!(state instanceof BlazeGoDummyDebugProfileState)) {
      return Promises.resolvedPromise();
    }
    return Promises.resolvedPromise(doExecute(environment, (BlazeGoDummyDebugProfileState) state));
  }

  protected RunContentDescriptor doExecute(
      ExecutionEnvironment environment, BlazeGoDummyDebugProfileState blazeState)
      throws ExecutionException {
    EventLoggingService.getInstance().logEvent(getClass(), "debugging-go");
    GoApplicationRunningState goState = blazeState.toNativeState(environment);
    ProgressManager.getInstance().runProcessWithProgressSynchronously(
            () -> ReadAction.run(() -> GoSdkCompat.prepareState(goState)),
            "Preparing Go Application Running State",
            false,
            environment.getProject());
    ExecutionResult executionResult = goState.execute(environment.getExecutor(), this);
    return XDebuggerManager.getInstance(environment.getProject())
        .startSession(
            environment,
            new XDebugProcessStarter() {
              @Override
              public XDebugProcess start(XDebugSession session) {
                RemoteVmConnection<?> connection =
                    new DlvRemoteVmConnection(DlvDisconnectOption.KILL);
                XDebugProcess process =
                    new DlvDebugProcess(session, connection, executionResult, /* remote= */ true);
                connection.open(goState.getDebugAddress());
                return process;
              }
            })
        .getRunContentDescriptor();
  }
}
