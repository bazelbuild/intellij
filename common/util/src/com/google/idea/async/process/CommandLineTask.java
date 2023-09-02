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
package com.google.idea.async.process;

import com.google.common.base.Joiner;
import com.google.common.io.ByteStreams;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.execution.ParametersListUtil;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/** A way to run simple processes representing command line utilities.. */
public class CommandLineTask {
  private static final Logger logger = Logger.getInstance(CommandLineTask.class);
  private static final OutputStream NULL_STREAM = ByteStreams.nullOutputStream();

  private final File workingDirectory;
  protected final List<String> command;
  private final Map<String, String> environmentVariables;
  private final boolean redirectErrorStream;
  private final OutputStream stdout;
  private final OutputStream stderr;

  public CommandLineTask(
      File workingDirectory,
      List<String> command,
      Map<String, String> environmentVariables,
      @Nullable OutputStream stdout,
      @Nullable OutputStream stderr,
      boolean redirectErrorStream) {
    this.workingDirectory = workingDirectory;
    this.command = command;
    this.environmentVariables = environmentVariables;
    this.redirectErrorStream = redirectErrorStream;
    this.stdout = stdout != null ? stdout : NULL_STREAM;
    this.stderr = stderr != null ? stderr : NULL_STREAM;
  }

  @Override
  public String toString() {
    return Joiner.on(' ').join(command);
  }

  private static void closeQuietly(OutputStream stream) {
    try {
      stream.close();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  // See GeneralCommandLine#ParentEnvironmentType for an explanation of why we do this.
  private static void initializeEnvironment(Map<String, String> envMap) {
    envMap.clear();
    envMap.putAll(EnvironmentUtil.getEnvironmentMap());
  }

  protected int invokeCommand() throws IOException, InterruptedException {
    String logCommand = ParametersListUtil.join(command);
    if (logCommand.length() > 2000) {
      logCommand = logCommand.substring(0, 2000) + " <truncated>";
    }
    logger.info(String.format("Running task:\n  %s\n  with PWD: %s", logCommand, workingDirectory));

    try {
      ProcessBuilder builder =
          new ProcessBuilder()
              .command(command)
              .redirectErrorStream(redirectErrorStream)
              .directory(workingDirectory);

      Map<String, String> env = builder.environment();
      initializeEnvironment(env);
      for (Map.Entry<String, String> entry : environmentVariables.entrySet()) {
        env.put(entry.getKey(), entry.getValue());
      }
      env.put("PWD", workingDirectory.getPath());

      try {
        final Process process = builder.start();
        Thread shutdownHook = new Thread(process::destroy);
        try {
          Runtime.getRuntime().addShutdownHook(shutdownHook);
          // These tasks are non-interactive, so close the stream connected to the process's
          // input.
          process.getOutputStream().close();
          Thread stdoutThread = ProcessUtil.forwardAsync(process.getInputStream(), stdout);
          Thread stderrThread = null;
          if (!redirectErrorStream) {
            stderrThread = ProcessUtil.forwardAsync(process.getErrorStream(), stderr);
          }
          process.waitFor();
          stdoutThread.join();
          if (!redirectErrorStream) {
            stderrThread.join();
          }
          return process.exitValue();
        } catch (InterruptedException e) {
          process.destroy();
          throw e;
        } finally {
          try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
          } catch (IllegalStateException e) {
            // we can't remove a shutdown hook if we are shutting down, do nothing about it
          }
        }
      } catch (IOException e) {
        logger.warn(e);
        throw e;
      }
    } finally {
      closeQuietly(stdout);
      closeQuietly(stderr);
    }
  }
}
