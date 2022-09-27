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

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.command.buildresult.BuildFlags;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.command.buildresult.ParsedBepOutput;
import java.util.List;
import java.util.Optional;

public class FakeBuildResultHelperBep implements BuildResultHelper {
  private static final ImmutableList.Builder<String> startupOptions = ImmutableList.builder();
  private static final ImmutableList.Builder<String> cmdlineOptions = ImmutableList.builder();

  public FakeBuildResultHelperBep(
      ImmutableList<String> startupOptions, ImmutableList<String> cmdlineOptions) {
    FakeBuildResultHelperBep.startupOptions.addAll(startupOptions);
    FakeBuildResultHelperBep.cmdlineOptions.addAll(cmdlineOptions);
  }

  @Override
  public List<String> getBuildFlags() {
    return ImmutableList.of();
  }

  @Override
  public ParsedBepOutput getBuildOutput(Optional<String> completedBuildId)
      throws GetArtifactsException {
    return null;
  }

  @Override
  public BuildFlags getBlazeFlags(Optional<String> completedBuildId) throws GetFlagsException {
    return new BuildFlags(startupOptions.build(), cmdlineOptions.build());
  }

  @Override
  public void close() {}
}
