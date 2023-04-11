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
package com.google.idea.blaze.clwb.run.producers;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.execution.RunConfigurationProducerService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;

/** Suppresses certain non-Blaze configuration producers in Blaze projects. */
public class NonBlazeProducerSuppressor implements StartupActivity {

  private static final ImmutableList<String> PRODUCERS_TO_SUPPRESS =
      ImmutableList.of(
          "com.jetbrains.cidr.cpp.execution.testing.boost.CMakeBoostTestRunConfigurationProducer",
          "com.jetbrains.cidr.cpp.execution.testing.google.CMakeGoogleTestRunConfigurationProducer",
          "com.jetbrains.cidr.cpp.execution.testing.tcatch.CMakeCatchTestRunConfigurationProducer");

  @Override
  public void runActivity(Project project) {
    if (Blaze.isBlazeProject(project)) {
      suppressProducers(project);
    }
  }

  private static void suppressProducers(Project project) {
    RunConfigurationProducerService producerService =
        RunConfigurationProducerService.getInstance(project);
    producerService.getState().ignoredProducers.addAll(PRODUCERS_TO_SUPPRESS);
  }
}
