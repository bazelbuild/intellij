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
package com.google.idea.blaze.base.command;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.idea.blaze.base.bazel.BuildSystem.BuildInvoker;
import com.google.idea.blaze.base.model.primitives.TargetExpression;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** A command to issue to Blaze/Bazel on the command line. */
@AutoValue
public abstract class BlazeCommand {

  public abstract Path binary();

  public abstract BlazeCommandName name();

  public abstract ImmutableList<String> cmdlineFlags();

  public abstract ImmutableList<String> startupFlags();

  public abstract Optional<Path> workspaceRoot();

  public ImmutableList<String> toArgumentList() {
    return ImmutableList.<String>builder()
        .addAll(startupFlags())
        .add(name().toString())
        .addAll(cmdlineFlags())
        .build();
  }

  public ImmutableList<String> toList() {
    return ImmutableList.<String>builder()
        .add(binary().toString())
        .addAll(startupFlags())
        .add(name().toString())
        .addAll(cmdlineFlags())
        .build();
  }

  public static Builder builder(BuildInvoker invoker, BlazeCommandName name) {
    return new Builder(invoker.getBinaryPath(), name);
  }

  /**
   * @deprecated Use {@link #builder(BuildInvoker, BlazeCommandName)} instead.
   */
  @Deprecated
  public static Builder builder(String binaryPath, BlazeCommandName name) {
    return new Builder(binaryPath, name);
  }

  public static class Builder {

    private final Path binary;
    private final BlazeCommandName name;
    private Path effectiveWorkspaceRoot;
    private final ImmutableList.Builder<String> startupFlags = ImmutableList.builder();
    private final ImmutableList.Builder<TargetExpression> targets = ImmutableList.builder();
    private final ImmutableList.Builder<String> cmdlineFlags = ImmutableList.builder();
    private final ImmutableList.Builder<String> exeFlags = ImmutableList.builder();

    public Builder(String binaryPath, BlazeCommandName name) {
      this.binary = Path.of(binaryPath);
      this.name = name;

      // Tell forge what tool we used to call blaze so we can track usage.
      addBlazeFlags(BlazeFlags.getToolTagFlag());
    }

    public BlazeCommandName getName() {
      return name;
    }

    public BlazeCommand build() {
      ImmutableList.Builder<String> arguments = ImmutableList.builder();
      arguments.addAll(cmdlineFlags.build());

      // Need to add '--' before targets, to support subtracted/excluded targets.
      arguments.add("--");

      // Trust the user's ordering of the targets since order matters to blaze
      for (TargetExpression targetExpression : targets.build()) {
        arguments.add(targetExpression.toString());
      }

      arguments.addAll(exeFlags.build());

      return new AutoValue_BlazeCommand(
          binary,
          name,
          arguments.build(),
          startupFlags.build(),
          Optional.ofNullable(effectiveWorkspaceRoot)
      );
    }

    @CanIgnoreReturnValue
    public Builder addTargets(TargetExpression... targets) {
      return this.addTargets(Arrays.asList(targets));
    }

    @CanIgnoreReturnValue
    public Builder addTargets(List<? extends TargetExpression> targets) {
      this.targets.addAll(targets);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder addExeFlags(String... flags) {
      return addExeFlags(Arrays.asList(flags));
    }

    @CanIgnoreReturnValue
    public Builder addExeFlags(List<String> flags) {
      this.exeFlags.addAll(flags);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder addBlazeFlags(String... flags) {
      return addBlazeFlags(Arrays.asList(flags));
    }

    @CanIgnoreReturnValue
    public Builder addBlazeFlags(List<String> flags) {
      this.cmdlineFlags.addAll(flags);
      return this;
    }

    /**
     * This function is designed to add start up flags to blaze build command only when blazerc file
     * is not accessible (e.g. build api). If there is an already running Blaze server and the
     * startup options do not match, it will be restarted. So do not use this function unless you
     * cannot update blazerc used by blaze and you are sure new flags will not break running blaze
     * server.
     */
    @CanIgnoreReturnValue
    public BlazeCommand.Builder addBlazeStartupFlags(List<String> flags) {
      this.startupFlags.addAll(flags);
      return this;
    }

    /** Sets the workspace root that the command should run in, overriding the project default. */
    @CanIgnoreReturnValue
    public BlazeCommand.Builder setWorkspaceRoot(Path root) {
      this.effectiveWorkspaceRoot = root;
      return this;
    }
  }
}
