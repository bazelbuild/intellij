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

import com.android.tools.idea.run.AndroidSessionInfo;
import com.intellij.execution.ExecutionTarget;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.project.Project;
import javax.annotation.Nullable;

/** Compat utilities for {@link AndroidSessionInfo}. */
public class AndroidSessionInfoCompat {
  // #api3.5
  public static AndroidSessionInfo create(
      ProcessHandler processHandler,
      RunContentDescriptor descriptor,
      int uniqueId,
      RunConfiguration runConfiguration,
      ExecutionEnvironment env) {
    return new AndroidSessionInfo(
        processHandler,
        descriptor,
        uniqueId,
        env.getExecutor().getId(),
        env.getExecutor().getActionName(),
        env.getExecutionTarget());
  }

  // #api3.5
  public static AndroidSessionInfo findOldSession(
      Project project,
      @Nullable Executor executor,
      RunConfiguration runConfiguration,
      ExecutionTarget executionTarget) {
    return AndroidSessionInfo.findOldSession(
        project, executor, runConfiguration.getUniqueID(), executionTarget);
  }
}
