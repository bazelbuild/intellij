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
package com.google.idea.blaze.base.run.state;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.execution.BlazeParametersListUtil;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.project.Project;
import java.util.ArrayList;
import javax.annotation.Nullable;
import org.jdom.Element;

/**
 * Shared state common to several {@link
 * com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationHandler} types.
 */
public class BlazeCommandRunConfigurationCommonState extends RunConfigurationCompositeState {
  public static final DataKey<String[]> USER_EXE_FLAG = DataKey.create("blaze-user-exe-flag");

  private static final String LEGACY_USER_FLAG_TAG = "blaze-user-flag";
  private static final String TEST_FILTER_FLAG_PREFIX = BlazeFlags.TEST_FILTER + "=";
  private static final String TEST_ARG_FLAG_PREFIX = BlazeFlags.TEST_ARG + "=";

  protected final BlazeCommandState command;
  protected final TestFilterState testFilter;
  protected final RunConfigurationFlagsState exeFlags;
  protected final EnvironmentVariablesState envVars;

  /**
   * Flags found in legacy {@code <blaze-user-flag>} XML elements that didn't migrate into a known
   * state (i.e. weren't {@code --test_filter=...}). Captured at read time so the migration notifier
   * can surface them to the user.
   */
  private ImmutableList<String> legacyUserFlags = ImmutableList.of();

  public BlazeCommandRunConfigurationCommonState(BuildSystemName buildSystemName) {
    command = new BlazeCommandState();
    testFilter = new TestFilterState();
    exeFlags = new RunConfigurationFlagsState(USER_EXE_FLAG, "Executable flags:");
    envVars = new EnvironmentVariablesState();
  }

  @Override
  protected ImmutableList<RunConfigurationState> initializeStates() {
    return ImmutableList.of(command, testFilter, exeFlags, envVars);
  }

  public TestFilterState getTestFilterState() {
    return testFilter;
  }

  /** @return The list of executable flags the user specified manually. */
  public RunConfigurationFlagsState getExeFlagsState() {
    return exeFlags;
  }

  /** @return The environment variables the user specified manually. */
  public EnvironmentVariablesState getUserEnvVarsState() {
    return envVars;
  }

  public BlazeCommandState getCommandState() {
    return command;
  }

  /** Returns the {@code --test_filter=<encoded>} flag for inclusion on a Bazel command line. */
  @Nullable
  public String getTestFilterFlag() {
    return testFilter.getTestFilterFlag();
  }

  /**
   * The actual test filter value intended to be passed directly to external processes or
   * environment variables. Unlike {@link #getTestFilterFlag()}, this has no shell escaping or
   * quoting.
   */
  @Nullable
  public String getTestFilterForExternalProcesses() {
    return testFilter.getTestFilter();
  }

  /**
   * Legacy {@code <blaze-user-flag>} entries that couldn't be migrated to a typed state. Cleared
   * by the migration notifier after the user moves them to the project view file.
   */
  public ImmutableList<String> getLegacyUserFlags() {
    return legacyUserFlags;
  }

  public void clearLegacyUserFlags() {
    legacyUserFlags = ImmutableList.of();
  }

  @Override
  protected void migrate(Element element) {
    final var legacy = new ArrayList<>(element.getChildren(LEGACY_USER_FLAG_TAG));
    if (legacy.isEmpty()) {
      return;
    }

    final var leftovers = new ArrayList<String>();
    final var testArgs = new ArrayList<String>();

    String filter = null;
    for (final var child : legacy) {
      final var raw = child.getTextTrim();
      if (raw == null || raw.isEmpty()) {
        continue;
      }

      if (raw.startsWith(TEST_FILTER_FLAG_PREFIX)) {
        // last-wins, matching Bazel's repeated-flag semantics
        filter = BlazeParametersListUtil.decodeTestFilterFlag(raw);
      } else if (raw.startsWith(TEST_ARG_FLAG_PREFIX)) {
        // multiple uses are accumulated
        testArgs.add(raw.substring(TEST_ARG_FLAG_PREFIX.length()));
      } else {
        leftovers.add(raw);
      }
    }

    if (filter != null) {
      testFilter.setTestFilter(filter);
    }
    if (!testArgs.isEmpty()) {
      exeFlags.setRawFlags(testArgs);
    }

    legacyUserFlags = ImmutableList.copyOf(leftovers);
    element.removeChildren(LEGACY_USER_FLAG_TAG);
  }

  public void validate(BuildSystemName buildSystemName) throws RuntimeConfigurationException {
    if (getCommandState().getCommand() == null) {
      throw new RuntimeConfigurationError("You must specify a command.");
    }
  }

  @Override
  public RunConfigurationStateEditor getEditor(Project project) {
    return new BlazeCommandRunConfigurationCommonStateEditor(project, getStates());
  }
}