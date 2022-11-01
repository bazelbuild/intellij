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

import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandRunner;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper.GetArtifactsException;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs;
import com.google.idea.blaze.base.sync.aspects.BuildResult;
import com.intellij.openapi.project.Project;

/**
 * A fake for {@link BlazeCommandRunner} that doesn't execute the build, but returns results from
 * the provided result helper.
 */
public final class FakeBlazeCommandRunner implements BlazeCommandRunner {
  private BlazeCommand command;

  @Override
  public BlazeBuildOutputs run(
      Project project,
      BlazeCommand.Builder blazeCommandBuilder,
      BuildResultHelper buildResultHelper,
      WorkspaceRoot workspaceRoot,
      BlazeContext context) {
    command = blazeCommandBuilder.build();
    try {
      return BlazeBuildOutputs.fromParsedBepOutput(
          BuildResult.SUCCESS, buildResultHelper.getBuildOutput());
    } catch (GetArtifactsException e) {
      return BlazeBuildOutputs.noOutputs(BuildResult.FATAL_ERROR);
    }
  }

  public BlazeCommand getIssuedCommand() {
    return command;
  }
}
