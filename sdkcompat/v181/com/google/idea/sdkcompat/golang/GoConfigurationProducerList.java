/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.sdkcompat.golang;

import com.google.common.collect.ImmutableList;
import com.intellij.execution.actions.RunConfigurationProducer;

/** List of go-related configuration producers for a given plugin version. */
public class GoConfigurationProducerList {

  /**
   * Returns a list of run configuration producers to suppress for Blaze projects.
   *
   * <p>These classes must all be accessible from the Blaze plugin's classpath (e.g. they shouldn't
   * belong to any plugins not listed as dependencies of the Blaze plugin).
   */
  public static final ImmutableList<Class<? extends RunConfigurationProducer<?>>>
      PRODUCERS_TO_SUPPRESS =
          ImmutableList.of(
              com.goide.execution.application.GoApplicationRunConfigurationProducer.class,
              com.goide.execution.testing.frameworks.gotest.GotestRunConfigurationProducer.class,
              com.goide.execution.testing.frameworks.gobench.GobenchRunConfigurationProducer.class,
              com.goide.execution.testing.frameworks.gocheck.GocheckRunConfigurationProducer.class);
}
