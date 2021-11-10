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
package com.google.idea.blaze.java.fastbuild;

import com.google.auto.value.AutoValue;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.model.primitives.Label;
import java.io.File;
import java.util.Map;
import java.util.Optional;

/** Internal state about an existing build. */
@AutoValue
abstract class FastBuildState {

  /**
   * Any build state that can change during the course of the (asynchronous) build needs to live in
   * this class.
   */
  @AutoValue
  abstract static class BuildOutput {
    abstract File deployJar();

    abstract Map<Label, FastBuildBlazeData> blazeData();

    abstract BlazeInfo blazeInfo();

    static BuildOutput create(
        File deployJar, Map<Label, FastBuildBlazeData> blazeData, BlazeInfo blazeInfo) {
      return new AutoValue_FastBuildState_BuildOutput(deployJar, blazeData, blazeInfo);
    }
  }

  /**
   * The last BuildOutput that was successfully completed. Used to restart a build if newBuildOutput
   * returns a failure.
   */
  abstract Optional<BuildOutput> completedBuildOutput();

  abstract ListenableFuture<BuildOutput> newBuildOutput();

  abstract File compilerOutputDirectory();

  abstract FastBuildParameters buildParameters();

  static FastBuildState create(
      ListenableFuture<BuildOutput> newBuildOutput,
      File compilerOutputDirectory,
      FastBuildParameters buildParameters) {
    return new AutoValue_FastBuildState(
        Optional.empty(), newBuildOutput, compilerOutputDirectory, buildParameters);
  }

  @CheckReturnValue
  FastBuildState withCompletedBuildOutput(BuildOutput completedBuildOutput) {
    return new AutoValue_FastBuildState(
        Optional.of(completedBuildOutput),
        newBuildOutput(),
        compilerOutputDirectory(),
        buildParameters());
  }

  @CheckReturnValue
  FastBuildState withNewBuildOutput(ListenableFuture<BuildOutput> newBuildOutput) {
    return new AutoValue_FastBuildState(
        completedBuildOutput(), newBuildOutput, compilerOutputDirectory(), buildParameters());
  }
}
