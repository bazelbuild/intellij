/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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

import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.common.experiments.BoolExperiment;
import com.google.idea.sdkcompat.run.RunManagerCompatUtils;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.Project;
import java.util.List;

class CorruptedRunConfigurationRemover extends AbstractProjectComponent {

  private static final BoolExperiment deleteCorruptedConfigs =
      new BoolExperiment("delete.corrupted.blaze.run.configurations", false);

  protected CorruptedRunConfigurationRemover(Project project) {
    super(project);
  }

  @Override
  public void projectOpened() {
    if (!Blaze.isBlazeProject(myProject) || !deleteCorruptedConfigs.getValue()) {
      return;
    }
    RunManager manager = RunManager.getInstance(myProject);
    List<RunnerAndConfigurationSettings> blazeConfigs =
        manager.getConfigurationSettingsList(BlazeCommandRunConfigurationType.getInstance());
    for (RunnerAndConfigurationSettings config : blazeConfigs) {
      if (isCorrupted(config.getConfiguration())) {
        RunManagerCompatUtils.removeConfiguration(manager, config);
      }
    }
  }

  private static boolean isCorrupted(RunConfiguration config) {
    return config instanceof BlazeCommandRunConfiguration
        && ((BlazeCommandRunConfiguration) config).isCorrupted();
  }
}
