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
package com.google.idea.blaze.base.run.coverage;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.ExecutorType;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BuildSystem;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.WrappingRunConfiguration;
import com.intellij.execution.runners.ExecutionEnvironment;
import java.io.File;
import javax.annotation.Nullable;

/** Helper methods for coverage integration. */
public class CoverageUtils {

  public static boolean coverageEnabled(ExecutionEnvironment env) {
    return coverageEnabled(env.getExecutor().getId(), env.getRunProfile());
  }

  public static boolean coverageEnabled(String executorId, RunProfile profile) {
    return ExecutorType.fromExecutorId(executorId) == ExecutorType.COVERAGE
        && isApplicableTo(profile);
  }

  public static boolean isApplicableTo(RunProfile runProfile) {
    BlazeCommandRunConfiguration config = toBlazeConfig(runProfile);
    if (config == null) {
      return false;
    }
    if (Blaze.getBuildSystem(config.getProject()) != BuildSystem.Blaze) {
      // temporarily disable coverage for Bazel, until we properly interface with its API and output
      // file locations
      return false;
    }
    BlazeCommandRunConfigurationCommonState handlerState =
        config.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
    if (handlerState == null) {
      return false;
    }
    BlazeCommandName command = handlerState.getCommandState().getCommand();
    return BlazeCommandName.TEST.equals(command) || BlazeCommandName.COVERAGE.equals(command);
  }

  @Nullable
  private static BlazeCommandRunConfiguration toBlazeConfig(RunProfile profile) {
    if (profile instanceof WrappingRunConfiguration) {
      profile = ((WrappingRunConfiguration) profile).getPeer();
    }
    return profile instanceof BlazeCommandRunConfiguration
        ? (BlazeCommandRunConfiguration) profile
        : null;
  }

  private static final ImmutableList<String> BLAZE_FLAGS =
      ImmutableList.of("--combined_report=lcov");

  public static ImmutableList<String> getBlazeFlags() {
    return BLAZE_FLAGS;
  }

  public static File getOutputFile(WorkspaceRoot root) {
    return new File(root.directory(), "blaze-out/_coverage/_coverage_report.dat");
  }
}
