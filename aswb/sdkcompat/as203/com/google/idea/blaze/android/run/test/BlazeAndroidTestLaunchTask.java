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
package com.google.idea.blaze.android.run.test;

import com.android.tools.idea.run.tasks.LaunchContext;
import com.android.tools.idea.run.tasks.LaunchResult;
import com.google.idea.blaze.base.model.primitives.Label;
import com.intellij.openapi.project.Project;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * An Android application launcher that invokes `blaze test` on an android_test target, and sets up
 * process handling and debugging for the test run.
 */
class BlazeAndroidTestLaunchTask extends BlazeAndroidTestLaunchTaskBase {
  public BlazeAndroidTestLaunchTask(
      Project project,
      Label target,
      List<String> buildFlags,
      BlazeAndroidTestFilter testFilter,
      BlazeAndroidTestRunContext runContext,
      boolean debug) {
    super(project, target, buildFlags, testFilter, runContext, debug);
  }

  @Override
  public LaunchResult run(@NotNull LaunchContext launchContext) {
    return run(
        launchContext.getExecutor(),
        launchContext.getDevice(),
        launchContext.getLaunchStatus(),
        launchContext.getConsolePrinter());
  }
}
