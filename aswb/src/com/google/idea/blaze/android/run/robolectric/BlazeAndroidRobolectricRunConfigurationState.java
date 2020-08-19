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

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.google.idea.blaze.base.run.state.BlazeCommandState;
import com.google.idea.blaze.base.run.state.DebugPortState;
import com.google.idea.blaze.base.run.state.RunConfigurationState;
import com.google.idea.blaze.base.settings.BuildSystem;

/** An android robolectric test specific state. */
public class BlazeAndroidRobolectricRunConfigurationState
    extends BlazeCommandRunConfigurationCommonState {
  private static final int DEFAULT_DEBUG_PORT = 5005;

  private final DebugPortState debugPortState;
  private final BlazeCommandState testCommandState;

  public BlazeAndroidRobolectricRunConfigurationState(BuildSystem buildSystem) {
    super(buildSystem);
    debugPortState = new DebugPortState(DEFAULT_DEBUG_PORT);
    testCommandState = new BlazeCommandState();
    testCommandState.setCommand(BlazeCommandName.TEST);
  }

  /** Returns only the fields relevant to an robolectric test. */
  @Override
  protected ImmutableList<RunConfigurationState> initializeStates() {
    return ImmutableList.of(blazeFlags, debugPortState, blazeBinary);
  }

  @Override
  public BlazeCommandState getCommandState() {
    return testCommandState;
  }

  public DebugPortState getDebugPortState() {
    return debugPortState;
  }
}
