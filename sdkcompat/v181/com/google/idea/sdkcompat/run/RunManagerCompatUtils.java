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
package com.google.idea.sdkcompat.run;

import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import java.util.List;

/** SDK compatibility bridge for {@link RunManager}. */
public class RunManagerCompatUtils {

  /**
   * Try to remove the configuration from RunManager's list. Returns false if unsuccessful (for
   * example, because there is no 'remove' method for this plugin API).
   */
  public static boolean removeConfiguration(
      RunManager manager, RunnerAndConfigurationSettings settings) {
    manager.removeConfiguration(settings);
    return true;
  }

  /**
   * Get before-run tasks associated with this run configuration.
   *
   * <p>#api171 remove post 171.
   */
  @SuppressWarnings("rawtypes")
  public static List<BeforeRunTask> getBeforeRunTasks(RunConfiguration config) {
    return config.getBeforeRunTasks();
  }
}
