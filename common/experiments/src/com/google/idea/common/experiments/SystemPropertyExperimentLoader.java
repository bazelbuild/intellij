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
package com.google.idea.common.experiments;

import java.util.Map;
import java.util.stream.Collectors;

final class SystemPropertyExperimentLoader extends HashingExperimentLoader {
  private static final String BLAZE_EXPERIMENT_OVERRIDE = "blaze.experiment.";

  @Override
  public Map<String, String> getUnhashedExperiments() {
    return System.getProperties()
        .stringPropertyNames()
        .stream()
        .filter(name -> name.startsWith(BLAZE_EXPERIMENT_OVERRIDE))
        .collect(
            Collectors.toMap(
                name -> name.substring(BLAZE_EXPERIMENT_OVERRIDE.length()), System::getProperty));
  }

  @Override
  public void initialize() {
    // Nothing to do.
  }
}
