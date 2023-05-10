/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.tools.idea.run.DeviceFutures;
import com.android.tools.idea.run.configuration.execution.AndroidConfigurationExecutor;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;

/** Implementation of {@code AndroidConfigurationExecutor} specific for Blaze project. */
public class BlazeWrapperForAndroidConfigurationExecutor implements AndroidConfigurationExecutor {
  private final AndroidConfigurationExecutor delegateExecutor;

  BlazeWrapperForAndroidConfigurationExecutor(@NotNull AndroidConfigurationExecutor executor) {
    delegateExecutor = executor;
  }

  @NotNull
  @Override
  public RunConfiguration getConfiguration() {
    return delegateExecutor.getConfiguration();
  }

  @NotNull
  @Override
  public DeviceFutures getDeviceFutures() {
    return delegateExecutor.getDeviceFutures();
  }

  @NotNull
  @Override
  public RunContentDescriptor run(@NotNull ProgressIndicator indicator) throws ExecutionException {
    return delegateExecutor.run(indicator);
  }

  @NotNull
  @Override
  public RunContentDescriptor debug(@NotNull ProgressIndicator indicator)
      throws ExecutionException {
    return delegateExecutor.debug(indicator);
  }

  @NotNull
  @Override
  public RunContentDescriptor applyChanges(@NotNull ProgressIndicator indicator) {
    throw new RuntimeException("Apply code changes is not supported for blaze");
  }

  @NotNull
  @Override
  public RunContentDescriptor applyCodeChanges(@NotNull ProgressIndicator indicator) {
    throw new RuntimeException("Apply changes is not supported for blaze");
  }
}
