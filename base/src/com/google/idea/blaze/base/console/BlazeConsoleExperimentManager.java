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

/** The experiments that turn on/off the new (v2) and old (v1) Blaze Consoles. */
public final class BlazeConsoleExperimentManager {

  private static final BoolExperiment v1Enabled = new BoolExperiment("blazeconsole.v1", true);
  private static final BoolExperiment v2Enabled = new BoolExperiment("blazeconsole.v2", false);

  private BlazeConsoleExperimentManager() {}

  public static boolean isBlazeConsoleV1Enabled() {
    return v1Enabled.getValue() || !v2Enabled.getValue();
  }

  public static boolean isBlazeConsoleV2Enabled() {
    return v2Enabled.getValue();
  }
}
