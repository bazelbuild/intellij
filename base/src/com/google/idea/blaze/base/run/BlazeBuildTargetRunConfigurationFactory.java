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
package com.google.idea.blaze.base.run;

import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.run.producers.BlazeBuildFileRunConfigurationProducer;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.Project;

/**
 * A factory creating run configurations based on BUILD file targets. Runs last, as a fallback for
 * the case where no more specialized factory handles the target.
 */
public class BlazeBuildTargetRunConfigurationFactory extends BlazeRunConfigurationFactory {

  @Override
  public boolean handlesTarget(Project project, BlazeProjectData blazeProjectData, Label label) {
    return BlazeBuildFileRunConfigurationProducer.handlesTarget(project, label);
  }

  @Override
  protected ConfigurationFactory getConfigurationFactory() {
    return BlazeCommandRunConfigurationType.getInstance().getFactory();
  }

  @Override
  public void setupConfiguration(RunConfiguration configuration, Label target) {
    BlazeBuildFileRunConfigurationProducer.setupConfiguration(configuration, target);
  }
}
