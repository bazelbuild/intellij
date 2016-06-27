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
package com.google.idea.blaze.base.experiments;

import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

class SystemPropertyExperimentLoader implements ExperimentLoader {
  private static final String BLAZE_EXPERIMENT_OVERRIDE = "blaze.experiment.";

  @Override
  public Map<String, String> getExperiments() {
    // Cache the current values of the experiments so that they don't change in the
    // current ExperimentScope.
    ImmutableMap.Builder<String, String> mapBuilder = ImmutableMap.builder();
    System.getProperties()
        .stringPropertyNames()
        .stream()
        .filter(name -> name.startsWith(BLAZE_EXPERIMENT_OVERRIDE))
        .forEach(
            name ->
                mapBuilder.put(
                    name.substring(BLAZE_EXPERIMENT_OVERRIDE.length()), System.getProperty(name)));
    return mapBuilder.build();
  }
}
