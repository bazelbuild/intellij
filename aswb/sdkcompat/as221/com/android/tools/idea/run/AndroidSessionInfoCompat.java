/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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
package com.android.tools.idea.run;

import com.android.ddmlib.Client;
import com.intellij.debugger.engine.RemoteDebugProcessHandler;
import com.intellij.execution.ExecutionTarget;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.RunContentDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Compat class for AndroidSessionInfo */
public class AndroidSessionInfoCompat {

  private AndroidSessionInfoCompat() {}

  public static AndroidSessionInfo create(
      @NotNull ProcessHandler processHandler,
      @NotNull RunContentDescriptor descriptor,
      @Nullable RunConfiguration runConfiguration,
      @NotNull String executorId,
      @NotNull String executorActionName,
      @NotNull ExecutionTarget executionTarget) {
    return AndroidSessionInfo.create(processHandler, runConfiguration, executorId, executionTarget);
  }

  @Nullable
  public static RunContentDescriptor getDescriptor(AndroidSessionInfo session) {
    return null;
  }

  public static void putAndroidDebugClient(RemoteDebugProcessHandler handler, Client client) {}
}
