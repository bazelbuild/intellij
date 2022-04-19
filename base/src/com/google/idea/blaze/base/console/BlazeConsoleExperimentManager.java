/*
 * Copyright 2021 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.console;

import com.google.common.primitives.Ints;
import com.google.idea.common.experiments.FeatureRolloutExperiment;
import com.google.idea.common.experiments.IntExperiment;
import com.google.idea.common.util.MorePlatformUtils;

/**
 * The experiments that turn on/off the new (v2) and old (v1) Blaze Consoles. The IDE restart is
 * needed for the new experiment values to take effect. The experiment also allows users to set a
 * limit on how much task history is to be displayed in the new (v2) Blaze console's tree-view.
 */
public final class BlazeConsoleExperimentManager {

  // The experiment is read once on startup, as the tool window is only created
  // once during the lifetime of the IDE
  private static final boolean V2_ENABLED =
      new FeatureRolloutExperiment("blazeconsole.v2.rollout").isEnabled();

  private BlazeConsoleExperimentManager() {}

  public static boolean isBlazeConsoleV1Enabled() {
    return !V2_ENABLED;
  }

  public static boolean isBlazeConsoleV2Enabled() {
    return V2_ENABLED;
  }

  public static int getTasksHistorySize() {
    int historySize =
        new IntExperiment(
                "blazeconsole.v2.history.size", MorePlatformUtils.isAndroidStudio() ? 2 : 10)
            .getValue();
    return Ints.constrainToRange(historySize, 2, 10);
  }
}
