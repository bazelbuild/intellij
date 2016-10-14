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
package com.google.idea.blaze.base.run.confighandler;

import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.util.Key;

/**
 * Supports the execution of {@link BlazeCommandRunConfiguration}s.
 *
 * <p>Provides rule-specific RunProfileState and before-run-tasks.
 */
public interface BlazeCommandRunConfigurationRunner {
  /** Used to store a runner to an {@link ExecutionEnvironment}. */
  Key<BlazeCommandRunConfigurationRunner> RUNNER_KEY = Key.create("blaze.run.config.runner");

  /** @return the RunProfileState corresponding to the given environment. */
  RunProfileState getRunProfileState(Executor executor, ExecutionEnvironment environment)
      throws ExecutionException;

  /**
   * Executes any required before run tasks.
   *
   * @return true if no task exists or the task was successfully completed. Otherwise returns false
   *     if the task either failed or was cancelled.
   */
  boolean executeBeforeRunTask(ExecutionEnvironment environment);
}
