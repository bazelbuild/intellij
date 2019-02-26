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

import com.google.common.base.Joiner;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
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
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.SystemProperties;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/** Invokes an external process. */
public interface ExternalTask {

  /** Run the task, attaching the given scopes to the task's {@link BlazeContext}. */
  int run(BlazeScope... scopes);

  /** A builder for an external task */
  class Builder {
    final ImmutableList.Builder<String> command = ImmutableList.builder();
    final File workingDirectory;
    final Map<String, String> environmentVariables = Maps.newHashMap();
    @Nullable BlazeContext context;
    @Nullable OutputStream stdout;
    @Nullable OutputStream stderr;
    @Nullable BlazeCommand blazeCommand;
    boolean redirectErrorStream = false;

    private Builder(WorkspaceRoot workspaceRoot) {
      this(workspaceRoot.directory());
    }

    private Builder(File workingDirectory) {
      this.workingDirectory = workingDirectory;
    }

    public Builder arg(String arg) {
      command.add(arg);
      return this;
    }

    public Builder args(String... args) {
      command.add(args);
      return this;
    }

    public Builder args(Collection<String> args) {
      command.addAll(args);
      return this;
    }

    public Builder args(Stream<String> args) {
      command.addAll(args.iterator());
      return this;
    }

    public Builder addBlazeCommand(BlazeCommand blazeCommand) {
      this.blazeCommand = blazeCommand;
      command.addAll(blazeCommand.toList());
      return this;
    }

    public Builder maybeArg(boolean b, String arg) {
      if (b) {
        command.add(arg);
      }
      return this;
    }

    public Builder context(@Nullable BlazeContext context) {
      this.context = context;
      return this;
    }

    public Builder redirectStderr(boolean redirectStderr) {
      this.redirectErrorStream = redirectStderr;
      return this;
    }

    public Builder stdout(@Nullable OutputStream stdout) {
      this.stdout = stdout;
      return this;
    }

    public Builder stderr(@Nullable OutputStream stderr) {
      this.stderr = stderr;
      return this;
    }

    public Builder environmentVar(String key, String value) {
      environmentVariables.put(key, value);
      return this;
    }

    public Builder environmentVars(Map<String, String> values) {
      environmentVariables.putAll(values);
      return this;
    }

    public ExternalTask build() {
      return ExternalTaskProvider.getInstance().build(this);
    }
  }

  /** The default implementation of {@link ExternalTask}. */
  class ExternalTaskImpl implements ExternalTask {
    private static final Logger logger = Logger.getInstance(ExternalTask.class);
    private static final OutputStream NULL_STREAM = ByteStreams.nullOutputStream();

    private final File workingDirectory;
    private final List<String> command;
    private final Map<String, String> environmentVariables;
    @Nullable private final BlazeContext parentContext;
    private final boolean redirectErrorStream;
    private final OutputStream stdout;
    private final OutputStream stderr;

    ExternalTaskImpl(
        @Nullable BlazeContext context,
        File workingDirectory,
        List<String> command,
        Map<String, String> environmentVariables,
        @Nullable OutputStream stdout,
        @Nullable OutputStream stderr,
        boolean redirectErrorStream) {
      this.workingDirectory = workingDirectory;
      this.command = command;
      this.environmentVariables = environmentVariables;
      this.parentContext = context;
      this.redirectErrorStream = redirectErrorStream;
      this.stdout = stdout != null ? stdout : NULL_STREAM;
      this.stderr = stderr != null ? stderr : NULL_STREAM;
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
                  return invokeCommand(context);
                } catch (ProcessCanceledException e) {
                  // Logging a ProcessCanceledException is an IJ error - mark context canceled
                  // instead
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

    /** See GeneralCommandLine#ParentEnvironmentType for an explanation of why we do this. */
    private static void initializeEnvironment(Map<String, String> envMap) {
      envMap.clear();
      envMap.putAll(EnvironmentUtil.getEnvironmentMap());
    }

    private int invokeCommand(BlazeContext context) {
      String executingTasksText =
          "Command: "
              + Joiner.on(" ").join(command)
              + SystemProperties.getLineSeparator();

      context.output(PrintOutput.log(executingTasksText));

      try {
        if (context.isEnding()) {
          return -1;
        }
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
            int exitValue = process.exitValue();
            if (exitValue != 0) {
              context.setHasError();
            }
            return exitValue;
          } catch (InterruptedException e) {
            process.destroy();
            throw new ProcessCanceledException();
          } finally {
            try {
              Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (IllegalStateException e) {
              // we can't remove a shutdown hook if we are shutting down, do nothing about it
            }
          }
        } catch (IOException e) {
          logger.warn(e);
          IssueOutput.error(e.getMessage()).submit(context);
        }
      } finally {
        closeQuietly(stdout);
        closeQuietly(stderr);
      }
      return -1;
    }
  }

  static Builder builder() {
    return new Builder(new File("/"));
  }

  static Builder builder(List<String> command) {
    return builder().args(command);
  }

  static Builder builder(File workingDirectory) {
    return new Builder(workingDirectory);
  }

  static Builder builder(WorkspaceRoot workspaceRoot) {
    return new Builder(workspaceRoot);
  }
}
