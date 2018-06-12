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
import java.util.Objects;
import java.util.stream.Collectors;
import org.jdom.Element;

class DefaultRunConfigurationRemover extends AbstractProjectComponent {

  private static final BoolExperiment deleteDuplicatedDefaultConfigs =
      new BoolExperiment("delete.duplicated.default.blaze.run.configurations", true);

  protected DefaultRunConfigurationRemover(Project project) {
    super(project);
  }

  @Override
  public void projectOpened() {
    if (!Blaze.isBlazeProject(myProject) || !deleteDuplicatedDefaultConfigs.getValue()) {
      return;
    }
    RunManager manager = RunManager.getInstance(myProject);
    List<RunnerAndConfigurationSettings> defaults =
        manager
            .getConfigurationSettingsList(BlazeCommandRunConfigurationType.getInstance())
            .stream()
            .filter(r -> isDefault(r.getConfiguration()))
            .collect(Collectors.toList());
    if (defaults.size() <= 1) {
      return;
    }
    // if there's more than one template configuration, the state has been corrupted; remove them
    // all
    defaults.forEach(c -> RunManagerCompatUtils.removeConfiguration(manager, c));
  }

  private static boolean isDefault(RunConfiguration config) {
    Element element = new Element("dummy");
    config.writeExternal(element);

    String isDefault = element.getAttributeValue("default");
    return Objects.equals(isDefault, "true");
  }
}
