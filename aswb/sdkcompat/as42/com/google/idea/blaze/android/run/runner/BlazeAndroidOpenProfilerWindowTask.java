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
package com.google.idea.blaze.android.run.runner;

import com.android.tools.idea.profilers.AndroidProfilerProgramRunner;
import com.android.tools.idea.run.tasks.LaunchContext;
import com.android.tools.idea.run.tasks.LaunchResult;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/** Opens the profiler tool window. #api41 */
public class BlazeAndroidOpenProfilerWindowTask extends BlazeAndroidOpenProfilerWindowTaskBase {
  public BlazeAndroidOpenProfilerWindowTask(Project project) {
    super(project);
  }

  @Override
  public LaunchResult run(@NotNull LaunchContext launchContext) {
    ApplicationManager.getApplication()
        .invokeLater(() -> AndroidProfilerProgramRunner.createProfilerToolWindow(project, null));
    return LaunchResult.success();
  }
}
