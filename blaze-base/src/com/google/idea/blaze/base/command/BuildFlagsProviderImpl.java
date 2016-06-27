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

import com.google.idea.blaze.base.experiments.BoolExperiment;
import com.google.idea.blaze.base.experiments.IntExperiment;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.settings.Blaze.BuildSystem;

import java.util.List;

import static com.google.idea.blaze.base.command.BlazeFlags.NO_CHECK_OUTPUTS;
import static com.google.idea.blaze.base.command.BlazeFlags.VERSION_WINDOW_FOR_DIRTY_NODE_GC;

/**
 * Flags added to blaze/bazel build commands.
 */
public class BuildFlagsProviderImpl implements BuildFlagsProvider {

  private static final BoolExperiment EXPERIMENT_USE_VERSION_WINDOW_FOR_DIRTY_NODE_GC =
    new BoolExperiment("ide_build_info.use_version_window_for_dirty_node_gc", false);
  private static final BoolExperiment EXPERIMENT_NO_EXPERIMENTAL_CHECK_OUTPUT_FILES =
    new BoolExperiment("build.noexperimental_check_output_files", false);
  private static final IntExperiment EXPERIMENT_MIN_PKG_COUNT_FOR_CT_NODE_EVICTION =
    new IntExperiment("min_pkg_count_for_ct_node_eviction", 0);

  // Avoids blaze state invalidation from non-overlapping transitive closures
  // This is caused by our search for the android_sdk
  private static final String MIN_PKG_COUNT_FOR_CT_NODE_EVICTION =
    "--min_pkg_count_for_ct_node_eviction=";

  private static String minPkgCountForCtNodeEviction(int value) {
    return MIN_PKG_COUNT_FOR_CT_NODE_EVICTION + value;
  }

  @Override
  public void addBuildFlags(BuildSystem buildSystem, ProjectViewSet projectViewSet, List<String> flags) {
    if (EXPERIMENT_USE_VERSION_WINDOW_FOR_DIRTY_NODE_GC.getValue()) {
      flags.add(VERSION_WINDOW_FOR_DIRTY_NODE_GC);
    }
    if (EXPERIMENT_NO_EXPERIMENTAL_CHECK_OUTPUT_FILES.getValue()) {
      flags.add(NO_CHECK_OUTPUTS);
    }
    int minPkgCountForCtNodeEviction = EXPERIMENT_MIN_PKG_COUNT_FOR_CT_NODE_EVICTION.getValue();
    if (minPkgCountForCtNodeEviction > 0) {
      flags.add(minPkgCountForCtNodeEviction(minPkgCountForCtNodeEviction));
    }
  }


}
