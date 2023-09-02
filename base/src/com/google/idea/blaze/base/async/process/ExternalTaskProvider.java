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
package com.google.idea.blaze.base.async.process;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.idea.blaze.base.async.process.ExternalTask.Builder;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.openapi.application.ApplicationManager;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Constructs an {@link ExternalTask} from a builder instance. This indirection exists to allow easy
 * redirection in blaze-invoking integration tests.
 */
@VisibleForTesting
public interface ExternalTaskProvider {

  static ExternalTaskProvider getInstance() {
    return ApplicationManager.getApplication().getService(ExternalTaskProvider.class);
  }

  ExternalTask build(ExternalTask.Builder builder);

  /** Default implementation returning an {@link ExternalTaskImpl}. */
  class Impl implements ExternalTaskProvider {
    @Override
    public ExternalTask build(Builder builder) {
      BlazeContext parentContext = builder.context;
      List<String> command = resolveCustomBinary(builder.command.build());
      return new ExternalTaskImpl(
          parentContext,
          builder.workingDirectory,
          command,
          builder.environmentVariables,
          builder.stdout,
          builder.stderr,
          builder.redirectErrorStream,
          builder.ignoreExitCode);
    }
  }

  // Allow adding a custom system path to lookup executables in.
  @Deprecated @VisibleForTesting
  String CUSTOM_PATH_SYSTEM_PROPERTY = "blaze.external.task.env.path";

  @VisibleForTesting
  @Nullable
  static File getCustomBinary(String potentialCommandName) {
    String customPath = System.getProperty(CUSTOM_PATH_SYSTEM_PROPERTY);
    if (Strings.isNullOrEmpty(customPath)) {
      return null;
    }
    return PathEnvironmentVariableUtil.findInPath(
        potentialCommandName, customPath, /* filter= */ null);
  }

  @VisibleForTesting
  static List<String> resolveCustomBinary(List<String> command) {
    if (command.isEmpty()) {
      return command;
    }
    List<String> actualCommand = new ArrayList<>(command);
    String binary = actualCommand.get(0);
    File binaryOverride =
        BinaryPathRemapper.remapBinary(binary).orElseGet(() -> getCustomBinary(binary));
    if (binaryOverride != null) {
      actualCommand.set(0, binaryOverride.getAbsolutePath());
    }
    return actualCommand;
  }
}
