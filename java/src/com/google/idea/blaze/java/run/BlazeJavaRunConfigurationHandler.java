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
package com.google.idea.blaze.java.run;

import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.BlazeConfigurationNameBuilder;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandGenericRunConfigurationRunner.BlazeCommandRunProfileState;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationHandler;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationRunner;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BuildSystem;
import com.google.idea.blaze.java.fastbuild.FastBuildService;
import com.google.idea.blaze.java.run.hotswap.ClassFileManifestBuilder;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.openapi.diagnostic.Logger;
import javax.annotation.Nullable;
import javax.swing.Icon;

/** Java-specific handler for {@link BlazeCommandRunConfiguration}s. */
public final class BlazeJavaRunConfigurationHandler implements BlazeCommandRunConfigurationHandler {

  private static final Logger logger = Logger.getInstance(BlazeJavaRunConfigurationHandler.class);

  private final BuildSystem buildSystem;
  private final BlazeJavaRunConfigState state;

  public BlazeJavaRunConfigurationHandler(BlazeCommandRunConfiguration configuration) {
    this.buildSystem = Blaze.getBuildSystem(configuration.getProject());
    this.state =
        new BlazeJavaRunConfigState(
            buildSystem, configuration.getProject(), configuration.getTargetKind());
  }

  @Override
  public BlazeJavaRunConfigState getState() {
    return state;
  }

  @Override
  public BlazeCommandRunConfigurationRunner createRunner(
      Executor executor, ExecutionEnvironment environment) {
    if (FastBuildService.enabled.getValue() && state.getFastBuildState().useFastBuild()) {
      return new FastBuildConfigurationRunner();
    }
    return new BlazeJavaRunConfigurationRunner();
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    state.validate(buildSystem);
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
  public BlazeCommandName getCommandName() {
    return state.getCommandState().getCommand();
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
    public RunProfileState getRunProfileState(Executor executor, ExecutionEnvironment env) {
      if (!BlazeCommandRunConfigurationRunner.isDebugging(env)
          || BlazeCommandRunConfigurationRunner.getBlazeCommand(env) == BlazeCommandName.BUILD) {
        return new BlazeCommandRunProfileState(env);
      }
      ClassFileManifestBuilder.initState(env);
      return new BlazeJavaRunProfileState(env);
    }

    @Override
    public boolean executeBeforeRunTask(ExecutionEnvironment env) {
      try {
        ClassFileManifestBuilder.buildManifest(env, null);
        return true;
      } catch (ExecutionException e) {
        ExecutionUtil.handleExecutionError(
            env.getProject(), env.getExecutor().getToolWindowId(), env.getRunProfile(), e);
        logger.info(e);
      }
      return false;
    }
  }
}
