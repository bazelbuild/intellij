/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.run;

import com.android.tools.idea.run.ApkInfo;
import com.android.tools.idea.run.LaunchOptions;
import com.android.tools.idea.run.tasks.DeployApkTask;
import com.android.tools.idea.run.tasks.LaunchTask;
import com.intellij.openapi.project.Project;
import java.util.Collection;

/** Compat utilities for {@link DeployTask}. */
public class DeployTaskCompat {
  // #api3.5
  public static LaunchTask createDeployTask(
      Project project, LaunchOptions launchOptions, Collection<ApkInfo> apks) {
    return new DeployApkTask(project, launchOptions, apks);
  }
}
