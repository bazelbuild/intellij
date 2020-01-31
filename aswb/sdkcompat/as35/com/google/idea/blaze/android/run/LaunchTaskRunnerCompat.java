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

import com.android.tools.idea.run.DeviceFutures;
import com.android.tools.idea.run.LaunchInfo;
import com.android.tools.idea.run.LaunchTaskRunner;
import com.android.tools.idea.run.tasks.LaunchTasksProvider;
import com.android.tools.idea.stats.RunStats;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.project.Project;
import java.util.function.BiConsumer;

/** Compat utilities for {@link LaunchTaskRunner}. */
public class LaunchTaskRunnerCompat {
  // #api3.5
  public static LaunchTaskRunner create(
      Project project,
      String launchConfigName,
      String applicationId,
      LaunchInfo launchInfo,
      ProcessHandler processHandler,
      DeviceFutures deviceFutures,
      LaunchTasksProvider launchTasksProvider,
      RunStats from,
      BiConsumer<String, HyperlinkInfo> consoleConsumer) {
    return new LaunchTaskRunner(
        project,
        launchConfigName,
        null,
        launchInfo,
        processHandler,
        deviceFutures,
        launchTasksProvider,
        from,
        consoleConsumer);
  }
}
