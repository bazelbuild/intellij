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
package com.google.idea.blaze.clwb.run;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.buildview.BazelBuildService;
import com.google.idea.blaze.base.command.BlazeInvocationContext;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.ExecutorType;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationRunner;
import com.google.idea.blaze.base.util.SaveUtil;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.RunCanceledByUserException;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.openapi.util.registry.Registry;
import com.jetbrains.cidr.execution.CidrCommandLineState;

import java.nio.file.Path;
import java.io.File;
import java.util.concurrent.CancellationException;

/**
 * CLion-specific handler for {@link BlazeCommandRunConfiguration}s.
 */
public class BlazeCidrRunConfigurationRunner implements BlazeCommandRunConfigurationRunner {

  private final BlazeCommandRunConfiguration configuration;

  /**
   * Calculated during the before-run task, and made available to the debugger.
   */
  File executableToDebug = null;

  BlazeCidrRunConfigurationRunner(BlazeCommandRunConfiguration configuration) {
    this.configuration = configuration;
  }

  @Override
  public RunProfileState getRunProfileState(Executor executor, ExecutionEnvironment env) {
    return new CidrCommandLineState(env, new BlazeCidrLauncher(configuration, this, env));
  }

  @Override
  public boolean executeBeforeRunTask(ExecutionEnvironment env) {
    executableToDebug = null;
    if (!isDebugging(env)) {
      return true;
    }
    try {
      final var executable = getExecutableToDebug(env);
      if (executable != null) {
        executableToDebug = executable.toFile();
        return true;
      }
    } catch (ExecutionException e) {
      ExecutionUtil.handleExecutionError(
          env.getProject(), env.getExecutor().getToolWindowId(), env.getRunProfile(), e);
    }
    return false;
  }

  private static boolean isDebugging(ExecutionEnvironment environment) {
    Executor executor = environment.getExecutor();
    return executor instanceof DefaultDebugExecutor;
  }

  private static Label getSingleTarget(BlazeCommandRunConfiguration config)
      throws ExecutionException {
    ImmutableList<? extends TargetExpression> targets = config.getTargets();
    if (targets.size() != 1 || !(targets.get(0) instanceof Label)) {
      throw new ExecutionException("Invalid configuration: doesn't have a single target label");
    }
    return (Label) targets.get(0);
  }

  /**
   * Builds blaze C/C++ target in debug mode, and returns the output build artifact.
   *
   * @throws ExecutionException if no unique output artifact was found.
   */
  private Path getExecutableToDebug(ExecutionEnvironment env) throws ExecutionException {
    SaveUtil.saveAllFiles();

    final var flagsBuilder = BazelDebugFlagsBuilder.fromDefaults(
        RunConfigurationUtils.getDebuggerKind(configuration),
        RunConfigurationUtils.getCompilerKind(configuration)
    );

    if (!Registry.is("bazel.clwb.debug.extraflags.disabled")) {
      flagsBuilder.withBuildFlags(WorkspaceRoot.fromProject(env.getProject()).toString());
    }

    Label target = getSingleTarget(configuration);

    final var executableFuture = BazelBuildService.buildForRunConfig(
        env.getProject(),
        configuration,
        BlazeInvocationContext.runConfigContext(
            ExecutorType.fromExecutor(env.getExecutor()), configuration.getType(), true),
        ImmutableList.of(),
        flagsBuilder.build(),
        target
    );

    try {
      return executableFuture.get();
    } catch (InterruptedException | CancellationException e) {
      throw new RunCanceledByUserException();
    } catch (java.util.concurrent.ExecutionException e) {
      throw new ExecutionException(String.format("Failed to get output artifacts when building %s", target), e);
    }
  }
}
