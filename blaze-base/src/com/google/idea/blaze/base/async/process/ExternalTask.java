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
package com.google.idea.blaze.base.async.process;

import com.google.common.base.Joiner;
import com.google.common.base.Throwables;
import com.google.common.io.ByteStreams;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.BlazeScope;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.output.PrintOutput;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/**
 * Invokes an external process
 */
public class ExternalTask {
  private static final Logger LOG = Logger.getInstance(ExternalTask.class);

  static final OutputStream NULL_STREAM = ByteStreams.nullOutputStream();

  public static class Builder {
    @NotNull
    private final File workingDirectory;
    @NotNull
    private final List<String> command;
    @Nullable
    private BlazeContext context;
    @Nullable
    private OutputStream stdout;
    @Nullable
    private OutputStream stderr;
    boolean redirectErrorStream = false;

    private Builder(
      @NotNull WorkspaceRoot workspaceRoot,
      @NotNull List<String> command) {
      this(workspaceRoot.directory(), command);
    }

    private Builder(
      @NotNull File workingDirectory,
      @NotNull List<String> command) {
      this.workingDirectory = workingDirectory;
      this.command = command;
    }

    @NotNull
    public Builder context(@Nullable BlazeContext context) {
      this.context = context;
      return this;
    }

    @NotNull
    public Builder redirectStderr(boolean redirectStderr) {
      this.redirectErrorStream = redirectStderr;
      return this;
    }

    @NotNull
    public Builder stdout(@Nullable OutputStream stdout) {
      this.stdout = stdout;
      return this;
    }

    @NotNull
    public Builder stderr(@Nullable OutputStream stderr) {
      this.stderr = stderr;
      return this;
    }

    @NotNull
    public ExternalTask build() {
      return new ExternalTask(
        context,
        workingDirectory,
        command,
        stdout,
        stderr,
        redirectErrorStream
      );
    }
  }

  @NotNull
  private final File workingDirectory;

  @NotNull
  private final List<String> command;

  @Nullable
  private final BlazeContext parentContext;

  private final boolean redirectErrorStream;

  @NotNull
  private final OutputStream stdout;

  @NotNull
  private final OutputStream stderr;

  private ExternalTask(
    @Nullable BlazeContext context,
    @NotNull File workingDirectory,
    @NotNull List<String> command,
    @Nullable OutputStream stdout,
    @Nullable OutputStream stderr,
    boolean redirectErrorStream) {
    this.workingDirectory = workingDirectory;
    this.command = command;
    this.parentContext = context;
    this.redirectErrorStream = redirectErrorStream;
    this.stdout = stdout != null ? stdout : NULL_STREAM;
    this.stderr = stderr != null ? stderr : NULL_STREAM;
  }

  public int run(BlazeScope... scopes) {
    Integer returnValue = Scope.push(parentContext, context -> {
      for (BlazeScope scope : scopes) {
        context.push(scope);
      }
      try {
        return invokeCommand(context);
      } catch (ProcessCanceledException e) {
        // Logging a ProcessCanceledException is an IJ error - mark context canceled instead.
        context.setCancelled();
      }
      return -1;
    });
    return returnValue != null ? returnValue : -1;
  }

  private static void closeQuietly(OutputStream stream) {
    try {
      stream.close();
    } catch (IOException e) {
      Throwables.propagate(e);
    }
  }

  private int invokeCommand(BlazeContext context) {
    String executingTasksText = "Command: "
                                + Joiner.on(" ").join(command)
                                + SystemProperties.getLineSeparator()
                                + SystemProperties.getLineSeparator();

    context.output(new PrintOutput(executingTasksText));

    try {
      if (context.isEnding()) {
        return -1;
      }
      ProcessBuilder builder = new ProcessBuilder()
        .command(command)
        .redirectErrorStream(redirectErrorStream)
        .directory(workingDirectory);
      try {
        final Process process = builder.start();
        Thread shutdownHook = new Thread(process::destroy);
        try {
          Runtime.getRuntime().addShutdownHook(shutdownHook);
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
          int exitValue = process.exitValue();
          if (exitValue != 0) {
            context.setHasError();
          }
          return exitValue;
        }
        catch (InterruptedException e) {
          process.destroy();
          throw new ProcessCanceledException();
        }
        finally {
          try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
          } catch (IllegalStateException e) {
            // we can't remove a shutdown hook if we are shutting down, do nothing about it
          }
        }
      }
      catch (IOException e) {
        LOG.warn(e);
        IssueOutput.error(e.getMessage()).submit(context);
      }
    }
    finally {
      closeQuietly(stdout);
      closeQuietly(stderr);
    }
    return -1;
  }

  public static Builder builder(
    @NotNull File workingDirectory,
    @NotNull List<String> command) {
    return new Builder(workingDirectory, command);
  }

  public static Builder builder(
    @NotNull WorkspaceRoot workspaceRoot,
    @NotNull List<String> command) {
    return new Builder(workspaceRoot, command);
  }

  public static Builder builder(
    @NotNull WorkspaceRoot workspaceRoot,
    @NotNull BlazeCommand command) {
    return new Builder(workspaceRoot, command.toList());
  }
}
