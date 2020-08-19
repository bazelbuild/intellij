/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.run.robolectric;

import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.BlazeConfigurationNameBuilder;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationHandler;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationRunner;
import com.google.idea.blaze.base.run.state.RunConfigurationState;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BuildSystem;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.runners.ExecutionEnvironment;
import javax.annotation.Nullable;

/**
 * {@link com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationHandler} for
 * android robolectric test targets.
 */
public class BlazeAndroidRobolectricRunConfigurationHandler
    implements BlazeCommandRunConfigurationHandler {
  private final BuildSystem buildSystem;
  private final BlazeAndroidRobolectricRunConfigurationState state;

  public BlazeAndroidRobolectricRunConfigurationHandler(
      BlazeCommandRunConfiguration configuration) {
    this.buildSystem = Blaze.getBuildSystem(configuration.getProject());
    this.state = new BlazeAndroidRobolectricRunConfigurationState(buildSystem);
  }

  @Override
  public RunConfigurationState getState() {
    return state;
  }

  @Override
  public BlazeCommandRunConfigurationRunner createRunner(
      Executor executor, ExecutionEnvironment environment) {
    return new BlazeAndroidRobolectricRunConfigurationRunner();
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    state.validate(buildSystem);
  }

  @Override
  @Nullable
  public String suggestedName(BlazeCommandRunConfiguration configuration) {
    if (configuration.getTargets().isEmpty()) {
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
    return "Android Robolectric Test Handler";
  }

  private static class BlazeAndroidRobolectricRunConfigurationRunner
      implements BlazeCommandRunConfigurationRunner {
    @Override
    public RunProfileState getRunProfileState(Executor executor, ExecutionEnvironment environment) {
      return new BlazeAndroidRobolectricRunProfileState(environment);
    }

    @Override
    public boolean executeBeforeRunTask(ExecutionEnvironment environment) {
      return true;
    }
  }
}
