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
package com.google.idea.blaze.base.run.confighandler;

import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.ExecutorType;
import com.google.idea.blaze.base.run.PendingRunConfigurationContext;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.google.idea.blaze.base.run.state.RunConfigurationState;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BuildSystem;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.runners.BaseProgramRunner;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.application.ApplicationManager;
import javax.annotation.Nullable;

class PendingTargetRunConfigurationHandler implements BlazeCommandRunConfigurationHandler {

  private final BuildSystem buildSystem;
  private final BlazeCommandRunConfigurationCommonState state;

  PendingTargetRunConfigurationHandler(BlazeCommandRunConfiguration config) {
    this.buildSystem = Blaze.getBuildSystem(config.getProject());
    this.state = new BlazeCommandRunConfigurationCommonState(buildSystem);
  }

  @Override
  public RunConfigurationState getState() {
    return state;
  }

  @Nullable
  @Override
  public BlazeCommandRunConfigurationRunner createRunner(
      Executor executor, ExecutionEnvironment environment) {
    return new PendingTargetRunner();
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    state.validate(buildSystem);
  }

  @Nullable
  @Override
  public String suggestedName(BlazeCommandRunConfiguration configuration) {
    return null;
  }

  @Nullable
  @Override
  public BlazeCommandName getCommandName() {
    return state.getCommandState().getCommand();
  }

  @Override
  public String getHandlerName() {
    return "Pending target handler";
  }

  private static boolean hasPendingTarget(BlazeCommandRunConfiguration config) {
    PendingRunConfigurationContext pendingContext = config.getPendingContext();
    return pendingContext != null && !pendingContext.getFuture().isDone();
  }

  static class PendingTargetProgramRunner extends BaseProgramRunner<RunnerSettings> {
    @Override
    public String getRunnerId() {
      return "PendingTargetProgramRunner";
    }

    @Override
    public boolean canRun(String executorId, RunProfile profile) {
      BlazeCommandRunConfiguration config =
          BlazeCommandRunConfigurationRunner.getBlazeConfig(profile);
      if (config == null) {
        return false;
      }
      // for now, try enabling every known executor type. At runtime, we'll error if it turns out to
      // not be appropriate (e.g. fast-run for non-java)
      ExecutorType type = ExecutorType.fromExecutorId(executorId);
      return !type.equals(ExecutorType.UNKNOWN) && hasPendingTarget(config);
    }

    @Override
    protected void execute(
        ExecutionEnvironment env, @Nullable Callback callback, RunProfileState state)
        throws ExecutionException {
      if (!(state instanceof DummyRunProfileState)) {
        reRunConfiguration(env);
        return;
      }
      ApplicationManager.getApplication()
          .executeOnPooledThread(
              () -> {
                try {
                  waitForFuture(env);
                  reRunConfiguration(env);
                } catch (ExecutionException e) {
                  ExecutionUtil.handleExecutionError(env, e);
                }
              });
    }
  }

  private static void reRunConfiguration(ExecutionEnvironment env) throws ExecutionException {
    BlazeCommandRunConfiguration config = BlazeCommandRunConfigurationRunner.getConfiguration(env);
    RunnerAndConfigurationSettings settings = getSettings(config);
    if (settings == null) {
      throw new ExecutionException(
          "Can't find runner settings for blaze run configuration " + config.getName());
    }
    // TODO(brendandouglas): check the executor type and inform the user if it's not applicable to
    // this target
    RunManager.getInstance(env.getProject()).setSelectedConfiguration(settings);
    ExecutionUtil.runConfiguration(settings, env.getExecutor());
  }

  @Nullable
  private static RunnerAndConfigurationSettings getSettings(BlazeCommandRunConfiguration config) {
    // #api181: replace with 'RunManager.getInstance(config.getProject()).findSettings(config)'
    return RunManager.getInstance(config.getProject()).getAllSettings().stream()
        .filter(s -> config.equals(s.getConfiguration()))
        .findFirst()
        .orElse(null);
  }

  /**
   * A placeholder {@link RunProfileState}. This is bypassed entirely by PendingTargetProgramRunner.
   */
  private static class DummyRunProfileState implements RunProfileState {
    @Override
    public ExecutionResult execute(Executor executor, ProgramRunner runner) {
      throw new RuntimeException("Unexpected code path");
    }
  }

  private static class PendingTargetRunner implements BlazeCommandRunConfigurationRunner {
    @Override
    public RunProfileState getRunProfileState(Executor executor, ExecutionEnvironment environment) {
      return new DummyRunProfileState();
    }

    @Override
    public boolean executeBeforeRunTask(ExecutionEnvironment env) {
      // if we got here, a different ProgramRunner has accepted a pending blaze run config, despite
      // PendingTargetProgramRunner being first in the list
      throw new RuntimeException(
          String.format(
              "Unexpected code path: program runner %s, executor: %s",
              env.getRunner().getClass(), env.getExecutor().getId()));
    }
  }

  /** Pop up a progress dialog, and wait until the blaze target future is done. */
  private static void waitForFuture(ExecutionEnvironment env) throws ExecutionException {
    BlazeCommandRunConfiguration config = BlazeCommandRunConfigurationRunner.getConfiguration(env);
    PendingRunConfigurationContext pendingContext = config.getPendingContext();
    if (pendingContext == null) {
      return;
    }
    PendingRunConfigurationContext.waitForFutureUnderProgressDialog(
        env.getProject(), pendingContext);
  }
}
