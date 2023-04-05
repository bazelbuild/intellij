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

import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.execution.RunConfigurationProducerService;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.jetbrains.cidr.cpp.execution.testing.boost.CMakeBoostTestRunConfigurationProducer;
import com.jetbrains.cidr.cpp.execution.testing.google.CMakeGoogleTestRunConfigurationProducer;
import com.jetbrains.cidr.cpp.execution.testing.tcatch.CMakeCatchTestRunConfigurationProducer;
import org.jetbrains.annotations.NotNull;

/** Suppresses certain non-Blaze configuration producers in Blaze projects. */
public abstract class RunConfigurationProducerSuppressor implements StartupActivity.Background {

  @NotNull
  protected abstract Class<? extends RunConfigurationProducer<?>> producerType();

  @Override
  public void runActivity(@NotNull Project project) {
    if (Blaze.isBlazeProject(project)) {
      RunConfigurationProducerService producerService =
              RunConfigurationProducerService.getInstance(project);
      producerService.addIgnoredProducer(producerType());
    }
  }

  public static class Boost extends RunConfigurationProducerSuppressor {
    @Override
    protected Class<? extends RunConfigurationProducer<?>> producerType() {
      return CMakeBoostTestRunConfigurationProducer.class;
    }
  }

  public static class Catch extends RunConfigurationProducerSuppressor {
    @Override
    protected Class<? extends RunConfigurationProducer<?>> producerType() {
      return CMakeCatchTestRunConfigurationProducer.class;
    }
  }

  public static class Google extends RunConfigurationProducerSuppressor {
    @Override
    protected Class<? extends RunConfigurationProducer<?>> producerType() {
      return CMakeGoogleTestRunConfigurationProducer.class;
    }
  }
}
