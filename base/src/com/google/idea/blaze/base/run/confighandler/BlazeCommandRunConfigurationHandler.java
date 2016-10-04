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
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import javax.annotation.Nullable;
import javax.swing.Icon;
import org.jdom.Element;

/**
 * Supports the run configuration flow for {@link BlazeCommandRunConfiguration}s.
 *
 * <p>Provides rule-specific configuration state, editor, name, RunProfileState, and
 * before-run-tasks.
 */
public interface BlazeCommandRunConfigurationHandler {

  /**
   * Checks whether the handler settings are valid.
   *
   * @throws RuntimeConfigurationException for configuration errors the user should be warned about.
   */
  void checkConfiguration() throws RuntimeConfigurationException;

  /** Loads this handler's state from the external data. */
  void readExternal(Element element) throws InvalidDataException;

  /** Writes this handler's state to the element. */
  @SuppressWarnings("ThrowsUncheckedException")
  void writeExternal(Element element) throws WriteExternalException;

  /**
   * Creates a clone of this handler for the specified configuration.
   *
   * @return A new BlazeCommandRunConfigurationHandler with the same state as this one, except its
   *     configuration is the specified {@code configuration}.
   */
  BlazeCommandRunConfigurationHandler cloneFor(BlazeCommandRunConfiguration configuration);

  /** @return the RunProfileState corresponding to the given environment. */
  RunProfileState getState(Executor executor, ExecutionEnvironment environment)
      throws ExecutionException;

  /**
   * Executes any required before run tasks.
   *
   * @return true if no task exists or the task was successfully completed. Otherwise returns false
   *     if the task either failed or was cancelled.
   */
  boolean executeBeforeRunTask(ExecutionEnvironment environment);

  /**
   * @return The default name of the run configuration based on its settings and this handler's
   *     state.
   */
  @Nullable
  String suggestedName();

  /**
   * Allows overriding the default behavior of {@link
   * com.intellij.execution.configurations.LocatableConfiguration#isGeneratedName()}. Return {@code
   * hasGeneratedFlag} to keep the default behavior.
   *
   * @param hasGeneratedFlag Whether the configuration reports its name is generated.
   * @return Whether the run configuration's name should be treated as generated (allowing
   *     regenerating it when settings change).
   */
  boolean isGeneratedName(boolean hasGeneratedFlag);

  /**
   * @return The name of the Blaze command associated with this handler. May be null if no command
   *     is appropriate.
   */
  @Nullable
  String getCommandName();

  /** @return The name of this handler. Shown in the UI. */
  String getHandlerName();

  /**
   * Allows overriding the default behavior of {@link
   * com.intellij.execution.RunnerIconProvider#getExecutorIcon(RunConfiguration, Executor)}. Return
   * null to keep the default behavior.
   */
  @Nullable
  Icon getExecutorIcon(RunConfiguration configuration, Executor executor);

  /** @return A {@link BlazeCommandRunConfigurationHandlerEditor} for this handler. */
  BlazeCommandRunConfigurationHandlerEditor getHandlerEditor();
}
