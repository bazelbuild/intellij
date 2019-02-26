/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.java.run.fastbuild;

import com.google.idea.blaze.base.logging.EventLoggingService;
import com.google.idea.blaze.base.run.ExecutorType;
import com.google.idea.blaze.java.run.BlazeJavaDebuggerRunner;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunContentDescriptor;
import org.jetbrains.annotations.NotNull;

/* A runner for {@link FastBuildDebugExecutor} executions. */
final class FastBuildDebuggerRunner extends BlazeJavaDebuggerRunner {

  @Override
  public String getRunnerId() {
    return "FastBuildDebuggerRunner";
  }

  @Override
  public boolean canRun(@NotNull String executorId, @NotNull RunProfile profile) {
    ExecutorType executorType = ExecutorType.fromExecutorId(executorId);
    if (!executorType.equals(ExecutorType.FAST_BUILD_DEBUG)) {
      return false;
    }
    return FastBuildConfigurationRunner.canRun(profile);
  }

  @Override
  protected RunContentDescriptor doExecute(RunProfileState state, ExecutionEnvironment env)
      throws ExecutionException {
    EventLoggingService.getInstance().logEvent(getClass(), "debugging-java-fastbuild");
    return super.doExecute(state, env);
  }
}
