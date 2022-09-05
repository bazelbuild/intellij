/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.sync;

import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.command.BlazeInvocationContext;
import com.google.idea.blaze.base.command.BuildFlagsProvider;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.sections.SyncFlagsSection;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.PrintOutput;
import com.google.idea.blaze.base.scope.output.SummaryOutput;
import com.google.idea.blaze.base.scope.output.SummaryOutput.Prefix;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.project.Project;
import java.util.List;

/** Provides additional flags for bazel/blaze build and sync commands. */
public class BlazeAndroidSyncBuildFlagsProvider implements BuildFlagsProvider {

  // Enables auto-setting fat_apk_cpu=x86_64 flag for sync
  private static final BoolExperiment forceFatApkCpuExperiment =
      new BoolExperiment("blaze.sync.flags.enableForceFatApkCpuExperiment", true);

  @Override
  public void addBuildFlags(
      Project project,
      ProjectViewSet projectViewSet,
      BlazeCommandName command,
      BlazeInvocationContext context,
      List<String> flags) {}

  @Override
  public void addSyncFlags(
      Project project,
      ProjectViewSet projectViewSet,
      BlazeCommandName command,
      BlazeContext context,
      BlazeInvocationContext invocationContext,
      List<String> flags) {
    List<String> syncOnlyFlags =
        BlazeFlags.expandBuildFlags(projectViewSet.listItems(SyncFlagsSection.KEY));
    if (forceFatApkCpuExperiment.getValue()
        && syncOnlyFlags.stream().noneMatch(flag -> flag.contains("fat_apk_cpu"))) {
      String message =
          "Forcing fat_apk_cpu flag to a single cpu architecture (x86_64) for sync. Refer"
              + " to go/auto-set-fat-apk-cpu-to-single-cpu for more details.";
      // Print to console only for "build" command. This method is invoked in a number of other
      // contexts such as collecting the flags for "blaze info", and we don't want to spam the
      // console on all of those invocations.
      if (BlazeCommandName.BUILD.equals(command)) {
        // Print to both summary and print outputs (i.e. main and subtask window of blaze console)
        context.output(SummaryOutput.output(Prefix.INFO, message).dedupe());
        context.output(PrintOutput.log(message));
      }
      flags.add("--fat_apk_cpu=x86_64");
    }
  }
}
