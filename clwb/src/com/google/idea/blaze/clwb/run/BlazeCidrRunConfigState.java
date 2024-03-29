/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.google.idea.blaze.base.run.state.DebugPortState;
import com.google.idea.blaze.base.run.state.EnvironmentVariablesState;
import com.google.idea.blaze.base.run.state.RunConfigurationState;
import com.google.idea.blaze.base.settings.BuildSystemName;

/** A version of the common state allowing environment variables to be set when debugging. */
final class BlazeCidrRunConfigState extends BlazeCommandRunConfigurationCommonState {
  private static final int DEFAULT_DEBUG_PORT = 5006;

  private final EnvironmentVariablesState envVars = new EnvironmentVariablesState();
  private final DebugPortState debugPortState = new DebugPortState(DEFAULT_DEBUG_PORT);

  BlazeCidrRunConfigState(BuildSystemName buildSystemName) {
    super(buildSystemName);
  }

  @Override
  protected ImmutableList<RunConfigurationState> initializeStates() {
    return ImmutableList.of(command, blazeFlags, exeFlags, envVars, debugPortState, blazeBinary);
  }

  EnvironmentVariablesState getEnvVarsState() {
    return envVars;
  }

  DebugPortState getDebugPortState() {
    return debugPortState;
  }
}
