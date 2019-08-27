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

import com.android.ddmlib.IDevice;
import com.android.tools.idea.profilers.AndroidProfilerProgramRunner;
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.tasks.LaunchTask;
import com.android.tools.idea.run.tasks.LaunchTaskDurations;
import com.android.tools.idea.run.util.LaunchStatus;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;

/** Opens the profiler tool window. */
public class BlazeAndroidOpenProfilerWindowTask implements LaunchTask {
  private static final String ID = "OPEN_PROFILER_TOOLWINDOW";
  private final Project project;

  public BlazeAndroidOpenProfilerWindowTask(Project project) {
    this.project = project;
  }

  @Override
  public String getDescription() {
    return "Open the Profiler Tool Window";
  }

  @Override
  public int getDuration() {
    return LaunchTaskDurations.LAUNCH_ACTIVITY;
  }

  @Override
  public boolean perform(IDevice device, LaunchStatus launchStatus, ConsolePrinter printer) {
    ApplicationManager.getApplication()
        .invokeLater(() -> AndroidProfilerProgramRunner.createProfilerToolWindow(project, null));
    return true;
  }

  @Override
  public String getId() {
    return ID;
  }
}
