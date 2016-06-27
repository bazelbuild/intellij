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
package com.google.idea.blaze.android.run;

import com.google.idea.blaze.android.run.runner.BlazeAndroidRunConfigurationRunner;
import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.BeforeRunTaskProvider;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import icons.BlazeIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Provides a before run task provider that immediately transfers control
 * to {@link BlazeAndroidRunConfigurationRunner}
 */
public final class BlazeBeforeRunTaskProvider extends BeforeRunTaskProvider<BlazeBeforeRunTaskProvider.Task> {
  @NotNull
  public static final Key<Task> ID = Key.create("Android.Blaze.BeforeRunTask");
  private static final String TASK_NAME = "Blaze before-run task";

  public static class Task extends BeforeRunTask<Task> {
    private Task() {
      super(ID);
      setEnabled(true);
    }
  }

  @NotNull
  private final Project project;

  public BlazeBeforeRunTaskProvider(@NotNull Project project) {
    this.project = project;
  }

  @Override
  public Key<Task> getId() {
    return ID;
  }

  @Nullable
  @Override
  public Icon getIcon() {
    return BlazeIcons.Blaze;
  }

  @Nullable
  @Override
  public Icon getTaskIcon(Task task) {
    return BlazeIcons.Blaze;
  }

  @Override
  public String getName() {
    return Blaze.guessBuildSystemName() + "before-run task";
  }

  @Override
  public String getDescription(Task task) {
    return Blaze.guessBuildSystemName() + "before-run task";
  }

  @Override
  public boolean isConfigurable() {
    return false;
  }

  @Nullable
  @Override
  public Task createTask(RunConfiguration runConfiguration) {
    if (runConfiguration instanceof BlazeAndroidRunConfiguration) {
      return new Task();
    }
    else {
      return null;
    }
  }

  @Override
  public boolean configureTask(RunConfiguration runConfiguration, Task task) {
    return false;
  }

  @Override
  public boolean canExecuteTask(RunConfiguration configuration, Task task) {
    return configuration instanceof BlazeAndroidRunConfiguration;
  }

  @Override
  public boolean executeTask(
    final DataContext dataContext,
    final RunConfiguration configuration,
    final ExecutionEnvironment env,
    Task task) {
    if (!canExecuteTask(configuration, task)) {
      return false;
    }

    final BlazeAndroidRunConfiguration blazeConfiguration = (BlazeAndroidRunConfiguration)configuration;
    return blazeConfiguration.getRunner().executeBuild(env);
  }
}
