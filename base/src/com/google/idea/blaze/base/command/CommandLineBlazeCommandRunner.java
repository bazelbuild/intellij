/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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

import com.google.common.collect.Interner;
import com.google.idea.blaze.base.async.process.ExternalTask;
import com.google.idea.blaze.base.async.process.LineProcessingOutputStream;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper.GetArtifactsException;
import com.google.idea.blaze.base.console.BlazeConsoleLineProcessorProvider;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.output.PrintOutput;
import com.google.idea.blaze.base.scope.scopes.SharedStringPoolScope;
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs;
import com.google.idea.blaze.base.sync.aspects.BuildResult;
import com.google.idea.blaze.base.sync.aspects.BuildResult.Status;
import com.intellij.openapi.project.Project;
import java.util.Optional;

/** {@inheritDoc} Start a build via local binary */
public class CommandLineBlazeCommandRunner implements BlazeCommandRunner {

  @Override
  public BlazeBuildOutputs run(
      Project project,
      BlazeCommand.Builder blazeCommandBuilder,
      BuildResultHelper buildResultHelper,
      WorkspaceRoot workspaceRoot,
      BlazeContext context) {
    int retVal =
        ExternalTask.builder(workspaceRoot)
            .addBlazeCommand(blazeCommandBuilder.build())
            .context(context)
            .stderr(
                LineProcessingOutputStream.of(
                    BlazeConsoleLineProcessorProvider.getAllStderrLineProcessors(context)))
            .build()
            .run();

    BuildResult buildResult = BuildResult.fromExitCode(retVal);
    if (buildResult.status == Status.FATAL_ERROR) {
      return BlazeBuildOutputs.noOutputs(buildResult);
    }
    try {
      context.output(PrintOutput.log("Build command finished. Retrieving BEP outputs..."));

      Interner<String> stringInterner =
          Optional.ofNullable(context.getScope(SharedStringPoolScope.class))
              .map(SharedStringPoolScope::getStringInterner)
              .orElse(null);
      return BlazeBuildOutputs.fromParsedBepOutput(
          buildResult, buildResultHelper.getBuildOutput(stringInterner));
    } catch (GetArtifactsException e) {
      IssueOutput.error("Failed to get build outputs: " + e.getMessage()).submit(context);
      return BlazeBuildOutputs.noOutputs(buildResult);
    }
  }
}
