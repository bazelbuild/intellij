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
package com.google.idea.blaze.base.run.state;

import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import java.io.File;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Shared state common to several {@link
 * com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationHandler} types.
 */
public final class BlazeCommandRunConfigurationCommonState extends RunConfigurationCompositeState {
  private static final String USER_BLAZE_FLAG_TAG = "blaze-user-flag";
  private static final String USER_EXE_FLAG_TAG = "blaze-user-exe-flag";

  private final BlazeCommandState command;
  private final RunConfigurationFlagsState blazeFlags;
  private final RunConfigurationFlagsState exeFlags;
  private final BlazeBinaryState blazeBinary;

  public BlazeCommandRunConfigurationCommonState(String buildSystemName) {
    command = new BlazeCommandState();
    blazeFlags = new RunConfigurationFlagsState(USER_BLAZE_FLAG_TAG, buildSystemName + " flags:");
    exeFlags = new RunConfigurationFlagsState(USER_EXE_FLAG_TAG, "Executable flags:");
    blazeBinary = new BlazeBinaryState();
    addStates(command, blazeFlags, exeFlags, blazeBinary);
  }

  @Nullable
  public BlazeCommandName getCommand() {
    return command.getCommand();
  }

  /** @return The list of blaze flags that the user specified manually. */
  public List<String> getBlazeFlags() {
    return blazeFlags.getFlags();
  }

  /** @return The list of executable flags the user specified manually. */
  public List<String> getExeFlags() {
    return exeFlags.getFlags();
  }

  @Nullable
  public String getBlazeBinary() {
    return blazeBinary.getBlazeBinary();
  }

  public void setCommand(@Nullable BlazeCommandName command) {
    this.command.setCommand(command);
  }

  public void setBlazeFlags(List<String> flags) {
    this.blazeFlags.setFlags(flags);
  }

  public void setExeFlags(List<String> flags) {
    this.exeFlags.setFlags(flags);
  }

  public void setBlazeBinary(@Nullable String blazeBinary) {
    this.blazeBinary.setBlazeBinary(blazeBinary);
  }

  /** Searches through all blaze flags for the first one beginning with '--test_filter' */
  @Nullable
  public String getTestFilterFlag() {
    for (String flag : getBlazeFlags()) {
      if (flag.startsWith(BlazeFlags.TEST_FILTER)) {
        return flag;
      }
    }
    return null;
  }

  public void validate(String buildSystemName) throws RuntimeConfigurationException {
    if (getCommand() == null) {
      throw new RuntimeConfigurationError("You must specify a command.");
    }
    String blazeBinaryString = getBlazeBinary();
    if (blazeBinaryString != null && !(new File(blazeBinaryString).exists())) {
      throw new RuntimeConfigurationError(buildSystemName + " binary does not exist");
    }
  }
}
