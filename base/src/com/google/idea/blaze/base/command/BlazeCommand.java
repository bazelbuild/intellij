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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import java.util.Arrays;
import java.util.List;
import javax.annotation.concurrent.Immutable;

/** A command to issue to Blaze/Bazel on the command line. */
@Immutable
public final class BlazeCommand {

  private final String binaryPath;
  private final BlazeCommandName name;
  private final ImmutableList<String> blazeCmdlineFlags;
  private final ImmutableList<String> blazeStartupFlags;

  private BlazeCommand(
      String binaryPath,
      BlazeCommandName name,
      ImmutableList<String> blazeStartupFlags,
      ImmutableList<String> blazeCmdlineFlags) {
    this.binaryPath = binaryPath;
    this.name = name;
    this.blazeCmdlineFlags = blazeCmdlineFlags;
    this.blazeStartupFlags = blazeStartupFlags;
  }

  public BlazeCommandName getName() {
    return name;
  }

  public ImmutableList<String> toArgumentList() {
    return ImmutableList.<String>builder()
        .addAll(blazeStartupFlags)
        .add(name.toString())
        .addAll(blazeCmdlineFlags)
        .build();
  }

  public ImmutableList<String> toList() {
    return ImmutableList.<String>builder()
        .add(binaryPath)
        .addAll(blazeStartupFlags)
        .add(name.toString())
        .addAll(blazeCmdlineFlags)
        .build();
  }

  @Override
  public String toString() {
    return Joiner.on(' ').join(toList());
  }

  public static Builder builder(String binaryPath, BlazeCommandName name) {
    return new Builder(binaryPath, name);
  }

  /** Builder for a blaze command */
  public static class Builder {
    private final String binaryPath;
    private final BlazeCommandName name;
    private final ImmutableList.Builder<String> blazeStartupFlags = ImmutableList.builder();
    private final ImmutableList.Builder<TargetExpression> targets = ImmutableList.builder();
    private final ImmutableList.Builder<String> blazeCmdlineFlags = ImmutableList.builder();
    private final ImmutableList.Builder<String> exeFlags = ImmutableList.builder();

    public Builder(String binaryPath, BlazeCommandName name) {
      this.binaryPath = binaryPath;
      this.name = name;
      // Tell forge what tool we used to call blaze so we can track usage.
      addBlazeFlags(BlazeFlags.getToolTagFlag());
    }

    private ImmutableList<String> getArguments() {
      ImmutableList.Builder<String> arguments = ImmutableList.builder();
      arguments.addAll(blazeCmdlineFlags.build());

      // Need to add '--' before targets, to support subtracted/excluded targets.
      arguments.add("--");

      // Trust the user's ordering of the targets since order matters to blaze
      for (TargetExpression targetExpression : targets.build()) {
        arguments.add(targetExpression.toString());
      }

      arguments.addAll(exeFlags.build());
      return arguments.build();
    }

    public BlazeCommand build() {
      return new BlazeCommand(binaryPath, name, blazeStartupFlags.build(), getArguments());
    }

    public Builder addTargets(TargetExpression... targets) {
      return this.addTargets(Arrays.asList(targets));
    }

    public Builder addTargets(List<? extends TargetExpression> targets) {
      this.targets.addAll(targets);
      return this;
    }

    public Builder addExeFlags(String... flags) {
      return addExeFlags(Arrays.asList(flags));
    }

    public Builder addExeFlags(List<String> flags) {
      this.exeFlags.addAll(flags);
      return this;
    }

    public Builder addBlazeFlags(String... flags) {
      return addBlazeFlags(Arrays.asList(flags));
    }

    public Builder addBlazeFlags(List<String> flags) {
      this.blazeCmdlineFlags.addAll(flags);
      return this;
    }

    /**
     * This function is designed to add start up flags to blaze build command only when blazerc file
     * is not accessible (e.g. build api). If there is an already running Blaze server and the
     * startup options do not match, it will be restarted. So do not use this function unless you
     * cannot update blazerc used by blaze and you are sure new flags will not break running blaze
     * server.
     */
    public BlazeCommand.Builder addBlazeStartupFlags(List<String> flags) {
      this.blazeStartupFlags.addAll(flags);
      return this;
    }
  }
}
