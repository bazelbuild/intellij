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

import com.google.idea.common.experiments.BoolExperiment;

/**
 * The experiments that turn on/off the new (v2) and old (v1) Blaze Consoles. The IDE restart is
 * needed for the new experiment values to take effect.
 */
public final class BlazeConsoleExperimentManager {

  // The values of experiments are being read once on startup. This because dynamic switching
  // between v1 and v2 consoles is not supported.
  private static final boolean V1_ENABLED = new BoolExperiment("blazeconsole.v1", true).getValue();
  private static final boolean V2_ENABLED = new BoolExperiment("blazeconsole.v2", false).getValue();

  private BlazeConsoleExperimentManager() {}

  public static boolean isBlazeConsoleV1Enabled() {
    return V1_ENABLED || !V2_ENABLED;
  }

  public static boolean isBlazeConsoleV2Enabled() {
    return V2_ENABLED;
  }
}
