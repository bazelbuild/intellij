/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.bazel;

import com.google.errorprone.annotations.MustBeClosed;
import com.google.idea.blaze.base.bazel.BuildSystem.BuildInvoker;
import com.google.idea.blaze.base.command.BlazeCommandRunner;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.settings.BuildBinaryType;
import java.util.function.Supplier;

/** Simple implementation of {@link BuildInvoker} for injecting dependencies in test code. */
public class FakeBuildInvoker implements BuildInvoker {

  private final BuildBinaryType type;
  private final String path;
  private final boolean supportsParallelism;
  private final Supplier<BuildResultHelper> buildResultHelperProvider;
  private final BlazeCommandRunner commandRunner;

  public FakeBuildInvoker(
      BuildBinaryType type,
      String path,
      boolean supportsParallelism,
      Supplier<BuildResultHelper> buildResultHelperSupplier,
      BlazeCommandRunner commandRunner) {
    this.type = type;
    this.path = path;
    this.supportsParallelism = supportsParallelism;
    this.buildResultHelperProvider = buildResultHelperSupplier;
    this.commandRunner = commandRunner;
  }

  @Override
  public BuildBinaryType getType() {
    return type;
  }

  @Override
  public String getBinaryPath() {
    return path;
  }

  @Override
  public boolean supportsParallelism() {
    return supportsParallelism;
  }

  @Override
  @MustBeClosed
  public BuildResultHelper createBuildResultProvider() {
    return buildResultHelperProvider.get();
  }

  @Override
  public BlazeCommandRunner getCommandRunner() {
    return commandRunner;
  }
}
