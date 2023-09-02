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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.BlazeScope;
import java.io.File;
import java.io.OutputStream;
import java.nio.file.Path;
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
    @VisibleForTesting public final ImmutableList.Builder<String> command = ImmutableList.builder();
    File workingDirectory;
    final Map<String, String> environmentVariables = Maps.newHashMap();
    @VisibleForTesting @Nullable public BlazeContext context;
    @VisibleForTesting @Nullable public OutputStream stdout;
    @VisibleForTesting @Nullable public OutputStream stderr;
    @Nullable BlazeCommand blazeCommand;
    boolean redirectErrorStream = false;
    boolean ignoreExitCode = false;

    private Builder(WorkspaceRoot workspaceRoot) {
      this(workspaceRoot.directory());
    }

    private Builder(File workingDirectory) {
      this.workingDirectory = workingDirectory;
    }

    @CanIgnoreReturnValue
    public Builder arg(String arg) {
      command.add(arg);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder args(String... args) {
      command.add(args);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder args(Collection<String> args) {
      command.addAll(args);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder args(Stream<String> args) {
      command.addAll(args.iterator());
      return this;
    }

    @CanIgnoreReturnValue
    public Builder addBlazeCommand(BlazeCommand blazeCommand) {
      this.blazeCommand = blazeCommand;
      command.addAll(blazeCommand.toList());
      blazeCommand.getEffectiveWorkspaceRoot().ifPresent(p -> workingDirectory = p.toFile());
      return this;
    }

    @CanIgnoreReturnValue
    public Builder maybeArg(boolean b, String arg) {
      if (b) {
        command.add(arg);
      }
      return this;
    }

    @CanIgnoreReturnValue
    public Builder context(@Nullable BlazeContext context) {
      this.context = context;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder redirectStderr(boolean redirectStderr) {
      this.redirectErrorStream = redirectStderr;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder stdout(@Nullable OutputStream stdout) {
      this.stdout = stdout;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder stderr(@Nullable OutputStream stderr) {
      this.stderr = stderr;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder environmentVar(String key, String value) {
      environmentVariables.put(key, value);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder environmentVars(Map<String, String> values) {
      environmentVariables.putAll(values);
      return this;
    }

    /**
     * The default behaviour calls {@link BlazeContext#setHasError()} if the process's exit code is
     * not 0. Setting this to {@code true} disables this behaviour so that callers can handle exit
     * codes in a context appropriate way.
     */
    @CanIgnoreReturnValue
    public Builder ignoreExitCode(boolean ignore) {
      this.ignoreExitCode = ignore;
      return this;
    }

    public ExternalTask build() {
      return ExternalTaskProvider.getInstance().build(this);
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

  static Builder builder(Path workingDirectory) {
    return new Builder(workingDirectory.toFile());
  }

  static Builder builder(WorkspaceRoot workspaceRoot) {
    return new Builder(workspaceRoot);
  }
}
