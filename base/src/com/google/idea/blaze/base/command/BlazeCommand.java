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
import com.google.idea.blaze.base.settings.Blaze.BuildSystem;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/** A command to issue to Blaze/Bazel on the command line. */
@Immutable
public final class BlazeCommand {

  private final BuildSystem buildSystem;
  private final BlazeCommandName name;
  @Nullable private final String blazeBinary;
  private final ImmutableList<String> arguments;

  private BlazeCommand(
      BuildSystem buildSystem,
      BlazeCommandName name,
      @Nullable String blazeBinary,
      ImmutableList<String> arguments) {
    this.buildSystem = buildSystem;
    this.name = name;
    this.blazeBinary = blazeBinary;
    this.arguments = arguments;
  }

  public BlazeCommandName getName() {
    return name;
  }

  public ImmutableList<String> toList() {
    String blazeBinary = this.blazeBinary;
    if (blazeBinary == null) {
      blazeBinary = getBinaryPath(buildSystem);
    }

    ImmutableList.Builder<String> commandLine = ImmutableList.builder();
    commandLine.add(blazeBinary, name.toString());
    commandLine.addAll(arguments);
    return commandLine.build();
  }

  private static String getBinaryPath(BuildSystem buildSystem) {
    BlazeUserSettings settings = BlazeUserSettings.getInstance();
    switch (buildSystem) {
      case Blaze:
        return settings.getBlazeBinaryPath();
      case Bazel:
        return settings.getBazelBinaryPath();
      default:
        throw new RuntimeException("Unrecognized build system type: " + buildSystem);
    }
  }

  @Override
  public String toString() {
    return Joiner.on(' ').join(toList());
  }

  public static Builder builder(BuildSystem buildSystem, BlazeCommandName name) {
    return new Builder(buildSystem, name);
  }

  /** Builder for a blaze command */
  public static class Builder {
    private final BuildSystem buildSystem;
    private final BlazeCommandName name;
    @Nullable private String blazeBinary;
    private final ImmutableList.Builder<TargetExpression> targets = ImmutableList.builder();
    private final ImmutableList.Builder<String> blazeFlags = ImmutableList.builder();
    private final ImmutableList.Builder<String> exeFlags = ImmutableList.builder();

    public Builder(BuildSystem buildSystem, BlazeCommandName name) {
      this.buildSystem = buildSystem;
      this.name = name;
      // Tell forge what tool we used to call blaze so we can track usage.
      addBlazeFlags(BlazeFlags.getToolTagFlag());
    }

    public BlazeCommand build() {
      ImmutableList.Builder<String> arguments = ImmutableList.builder();
      arguments.addAll(blazeFlags.build());
      arguments.add("--");

      // Trust the user's ordering of the targets since order matters to blaze
      for (TargetExpression targetExpression : targets.build()) {
        arguments.add(targetExpression.toString());
      }

      arguments.addAll(exeFlags.build());
      return new BlazeCommand(buildSystem, name, blazeBinary, arguments.build());
    }

    public Builder setBlazeBinary(@Nullable String blazeBinary) {
      this.blazeBinary = blazeBinary;
      return this;
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
      this.blazeFlags.addAll(flags);
      return this;
    }
  }
}
