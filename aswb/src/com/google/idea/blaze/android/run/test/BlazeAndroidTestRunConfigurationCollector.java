/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.run.test;

import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.logging.EventLoggingService;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.BlazeCommandRunConfigurationType;
import com.google.idea.blaze.base.run.state.RunConfigurationState;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/** Log launch method of test run configuration for a project */
public class BlazeAndroidTestRunConfigurationCollector implements ProjectComponent {
  private final Project project;

  public BlazeAndroidTestRunConfigurationCollector(Project project) {
    this.project = project;
  }

  public static BlazeAndroidTestRunConfigurationCollector getInstance(Project project) {
    return project.getComponent(BlazeAndroidTestRunConfigurationCollector.class);
  }

  public void logLaunchMethod(String launchMethod) {
    EventLoggingService.getInstance()
        .logEvent(
            BlazeAndroidTestRunConfigurationCollector.class,
            "BlazeAndroidTestRun",
            ImmutableMap.of("launchMethod", launchMethod));
  }

  @Override
  public void projectOpened() {
    Map<String, Integer> map = new HashMap<>();
    for (RunnerAndConfigurationSettings runnerAndConfigurationSettings :
        RunManager.getInstance(project)
            .getConfigurationSettingsList(BlazeCommandRunConfigurationType.getInstance())) {
      RunConfigurationState runConfigurationState =
          ((BlazeCommandRunConfiguration) runnerAndConfigurationSettings.getConfiguration())
              .getHandler()
              .getState();
      if (runConfigurationState instanceof BlazeAndroidTestRunConfigurationState) {
        String androidTestLaunchMethodName =
            ((BlazeAndroidTestRunConfigurationState) runConfigurationState)
                .getLaunchMethod()
                .name();
        map.put(androidTestLaunchMethodName, map.getOrDefault(androidTestLaunchMethodName, 0) + 1);
      }
    }
    if (!map.isEmpty()) {
      Map<String, String> result =
          map.entrySet().stream()
              .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue().toString()));
      EventLoggingService.getInstance()
          .logEvent(
              BlazeAndroidTestRunConfigurationCollector.class, "BlazeAndroidProjectOpened", result);
    }
  }
}
