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
package com.google.idea.blaze.java.run;

import static com.google.common.base.Preconditions.checkState;

import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.ExecutorType;
import com.intellij.execution.configurations.CommandLineState;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.configurations.RemoteState;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.WrappingRunConfiguration;
import com.intellij.execution.runners.ExecutionEnvironment;

/**
 * A {@code RunProfileState} to go with {@link BlazeJavaRunConfigState}, providing debugging
 * support.
 */
abstract class BlazeJavaDebuggableRunProfileState extends CommandLineState implements RemoteState {

  private static final String DEBUG_HOST_NAME = "localhost";

  private final BlazeCommandRunConfiguration cfg;
  private final ExecutorType executorType;

  BlazeJavaDebuggableRunProfileState(ExecutionEnvironment environment) {
    super(environment);
    this.cfg = getConfiguration(environment);
    this.executorType = ExecutorType.fromExecutor(environment.getExecutor());
  }

  private static BlazeCommandRunConfiguration getConfiguration(ExecutionEnvironment environment) {
    RunProfile runProfile = environment.getRunProfile();
    if (runProfile instanceof WrappingRunConfiguration) {
      runProfile = ((WrappingRunConfiguration) runProfile).getPeer();
    }
    return (BlazeCommandRunConfiguration) runProfile;
  }

  BlazeCommandRunConfiguration getConfiguration() {
    return cfg;
  }

  ExecutorType getExecutorType() {
    return executorType;
  }

  @Override
  public RemoteConnection getRemoteConnection() {
    if (executorType != ExecutorType.DEBUG) {
      return null;
    }
    BlazeJavaRunConfigState state = cfg.getHandlerStateIfType(BlazeJavaRunConfigState.class);
    checkState(state != null);
    return new RemoteConnection(
        /* useSockets */ true,
        DEBUG_HOST_NAME,
        Integer.toString(state.getDebugPortState().port),
        /* serverMode */ false);
  }
}
