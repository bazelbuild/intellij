/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.java.run;

import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.BlazeConfigurationNameBuilder;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationHandler;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationRunner;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.Blaze.BuildSystem;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import javax.annotation.Nullable;
import javax.swing.Icon;

/** Java-specific handler for {@link BlazeCommandRunConfiguration}s. */
public final class BlazeJavaRunConfigurationHandler implements BlazeCommandRunConfigurationHandler {

  private final String buildSystemName;
  private final BlazeCommandRunConfigurationCommonState state;

  public BlazeJavaRunConfigurationHandler(BlazeCommandRunConfiguration configuration) {
    BuildSystem buildSystem = Blaze.getBuildSystem(configuration.getProject());
    this.buildSystemName = buildSystem.getName();
    this.state = new BlazeCommandRunConfigurationCommonState(buildSystem);
  }

  @Override
  public BlazeCommandRunConfigurationCommonState getState() {
    return state;
  }

  @Override
  public BlazeCommandRunConfigurationRunner createRunner(
      Executor executor, ExecutionEnvironment environment) {
    return new BlazeJavaRunConfigurationRunner();
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    state.validate(buildSystemName);
  }

  @Override
  @Nullable
  public String suggestedName(BlazeCommandRunConfiguration configuration) {
    if (configuration.getTarget() == null) {
      return null;
    }
    return new BlazeConfigurationNameBuilder(configuration).build();
  }

  @Override
  @Nullable
  public String getCommandName() {
    BlazeCommandName command = state.getCommandState().getCommand();
    return command != null ? command.toString() : null;
  }

  @Override
  public String getHandlerName() {
    return "Java Handler";
  }

  @Override
  @Nullable
  public Icon getExecutorIcon(RunConfiguration configuration, Executor executor) {
    return null;
  }

  private static class BlazeJavaRunConfigurationRunner
      implements BlazeCommandRunConfigurationRunner {
    @Override
    public RunProfileState getRunProfileState(Executor executor, ExecutionEnvironment environment) {
      return new BlazeJavaRunProfileState(environment, executor instanceof DefaultDebugExecutor);
    }

    @Override
    public boolean executeBeforeRunTask(ExecutionEnvironment environment) {
      return true;
    }
  }
}
