/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.io.ByteStreams;
import com.google.idea.async.process.CommandLineTask;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.BlazeScope;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.common.PrintOutput;
import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.execution.ParametersListUtil;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nullable;

/** The default implementation of {@link ExternalTask}. */
public class ExternalTaskImpl implements ExternalTask {
  private static final OutputStream NULL_STREAM = ByteStreams.nullOutputStream();

  private final File workingDirectory;
  private final List<String> command;
  private final Map<String, String> environmentVariables;
  @Nullable private final BlazeContext parentContext;
  private final boolean redirectErrorStream;
  private final OutputStream stdout;
  private final OutputStream stderr;
  private final boolean ignoreExitCode;
  private final byte[] input;
  private final Project project;

  public ExternalTaskImpl(
      @Nullable BlazeContext context,
      File workingDirectory,
      List<String> command,
      Map<String, String> environmentVariables,
      @Nullable OutputStream stdout,
      @Nullable OutputStream stderr,
      boolean redirectErrorStream,
      boolean ignoreExitCode,
      @Nullable byte[] input,
      Project project) {
    this.workingDirectory = workingDirectory;
    this.command = command;
    this.environmentVariables = environmentVariables;
    this.parentContext = context;
    this.redirectErrorStream = redirectErrorStream;
    this.stdout = stdout != null ? stdout : NULL_STREAM;
    this.stderr = stderr != null ? stderr : NULL_STREAM;
    this.ignoreExitCode = ignoreExitCode;
    this.input = input;
    this.project = project;
  }

  @Override
  public String toString() {
    return Joiner.on(' ').join(resolveCustomBinary(command));
  }

  @Override
  public int run(BlazeScope... scopes) {
    Integer returnValue =
        Scope.push(
            parentContext,
            context -> {
              for (BlazeScope scope : scopes) {
                context.push(scope);
              }
              try {
                outputCommand(context, command);
                if (context.isEnding()) {
                  return -1;
                }
                int exitValue =
                    CommandLineTask.builder(workingDirectory)
                        .args(resolveCustomBinary(command))
                        .redirectStderr(redirectErrorStream)
                        .stderr(stderr)
                        .stdout(stdout)
                        .stdin(input)
                        .environmentVars(environmentVariables)
                        .build()
                        .run();
                if (!ignoreExitCode && exitValue != 0) {
                  context.setHasError();
                }
                return exitValue;
              } catch (IOException e) {
                outputError(context, e);
                return -1;
              } catch (InterruptedException | TimeoutException e) {
                context.setCancelled();
              }
              return -1;
            });
    return returnValue != null ? returnValue : -1;
  }

  private static void outputError(BlazeContext context, IOException e) {
    IssueOutput.error(e.getMessage()).submit(context);
  }

  private static void outputCommand(BlazeContext context, List<String> command) {
    String logMessage = "Command: " + ParametersListUtil.join(command);

    context.output(
        PrintOutput.log(
            StringUtil.shortenTextWithEllipsis(
                logMessage, /* maxLength= */ 1000, /* suffixLength= */ 0)));
  }

  // Allow adding a custom system path to lookup executables in.
  @Deprecated @VisibleForTesting
  static final String CUSTOM_PATH_SYSTEM_PROPERTY = "blaze.external.task.env.path";

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
