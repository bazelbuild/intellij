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
package com.google.idea.blaze.base.command;

import static com.google.idea.blaze.base.command.BlazeFlags.NO_CHECK_OUTPUTS;
import static com.google.idea.blaze.base.command.BlazeFlags.VERSION_WINDOW_FOR_DIRTY_NODE_GC;

import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.settings.Blaze.BuildSystem;
import com.google.idea.common.experiments.BoolExperiment;
import java.util.List;

/** Flags added to blaze/bazel build commands. */
public class BuildFlagsProviderImpl implements BuildFlagsProvider {

  private static final BoolExperiment experimentUseVersionWindowForDirtyNodeGc =
      new BoolExperiment("ide_build_info.use_version_window_for_dirty_node_gc", false);
  private static final BoolExperiment experimentNoExperimentalCheckOutputFiles =
      new BoolExperiment("build.noexperimental_check_output_files", false);

  @Override
  public void addBuildFlags(
      BuildSystem buildSystem, ProjectViewSet projectViewSet, List<String> flags) {
    if (experimentUseVersionWindowForDirtyNodeGc.getValue()) {
      flags.add(VERSION_WINDOW_FOR_DIRTY_NODE_GC);
    }
    if (experimentNoExperimentalCheckOutputFiles.getValue()) {
      flags.add(NO_CHECK_OUTPUTS);
    }
    flags.add("--curses=no");
    flags.add("--color=no");
  }
}
