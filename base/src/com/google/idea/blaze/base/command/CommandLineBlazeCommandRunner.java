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
import com.google.errorprone.annotations.MustBeClosed;
import com.google.idea.blaze.base.async.process.ExternalTask;
import com.google.idea.blaze.base.async.process.LineProcessingOutputStream;
import com.google.idea.blaze.base.async.process.PrintOutputLineProcessor;
import com.google.idea.blaze.base.bazel.BazelExitCodeException;
import com.google.idea.blaze.base.bazel.BazelExitCodeException.ThrowOption;
import com.google.idea.blaze.base.command.buildresult.BuildEventProtocolUtils;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper.GetArtifactsException;
import com.google.idea.blaze.base.command.info.BlazeInfoException;
import com.google.idea.blaze.base.console.BlazeConsoleLineProcessorProvider;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.run.testlogs.BlazeTestResults;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.scopes.SharedStringPoolScope;
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs;
import com.google.idea.blaze.base.sync.aspects.BuildResult;
import com.google.idea.blaze.base.sync.aspects.BuildResult.Status;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.exception.BuildException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Optional;

/** {@inheritDoc} Start a build via local binary. */
public class CommandLineBlazeCommandRunner implements BlazeCommandRunner {

  @Override
  public BlazeBuildOutputs run(
      Project project,
      BlazeCommand.Builder blazeCommandBuilder,
      BuildResultHelper buildResultHelper,
      BlazeContext context) {

    BuildResult buildResult =
        issueBuild(blazeCommandBuilder, WorkspaceRoot.fromProject(project), context);
    if (buildResult.status == Status.FATAL_ERROR) {
      return BlazeBuildOutputs.noOutputs(buildResult);
    }
    context.output(PrintOutput.log("Build command finished. Retrieving BEP outputs..."));
    try {
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

  @Override
  public BlazeTestResults runTest(
      Project project,
      BlazeCommand.Builder blazeCommandBuilder,
      BuildResultHelper buildResultHelper,
      BlazeContext context) {
    BuildResult buildResult =
        issueBuild(blazeCommandBuilder, WorkspaceRoot.fromProject(project), context);
    if (buildResult.status == Status.FATAL_ERROR) {
      return BlazeTestResults.NO_RESULTS;
    }
    context.output(PrintOutput.log("Build command finished. Retrieving BEP outputs..."));
    try {
      return buildResultHelper.getTestResults(Optional.empty());
    } catch (GetArtifactsException e) {
      IssueOutput.error("Failed to get build outputs: " + e.getMessage()).submit(context);
      return BlazeTestResults.NO_RESULTS;
    }
  }

  @Override
  public InputStream runQuery(
      Project project,
      BlazeCommand.Builder blazeCommandBuilder,
      BuildResultHelper buildResultHelper,
      BlazeContext context)
      throws BuildException {
    File outputFile = BuildEventProtocolUtils.createTempOutputFile();
    try (FileOutputStream out = new FileOutputStream(outputFile)) {
      int retVal =
          ExternalTask.builder(WorkspaceRoot.fromProject(project))
              .addBlazeCommand(blazeCommandBuilder.build())
              .context(context)
              .stdout(out)
              .stderr(
                  LineProcessingOutputStream.of(
                      line -> {
                        // errors are expected, so limit logging to info level
                        Logger.getInstance(this.getClass()).info(line.stripTrailing());
                        context.output(PrintOutput.output(line.stripTrailing()));
                        return true;
                      }))
              .build()
              .run();
      BazelExitCodeException.throwIfFailed(
          blazeCommandBuilder, retVal, ThrowOption.ALLOW_PARTIAL_SUCCESS);
      return new BufferedInputStream(new FileInputStream(outputFile));
    } catch (IOException e) {
      throw new BuildException(e);
    }
  }

  @Override
  @MustBeClosed
  public InputStream runBlazeInfo(
      Project project,
      BlazeCommand.Builder blazeCommandBuilder,
      BuildResultHelper buildResultHelper,
      BlazeContext context)
      throws BlazeInfoException {
    File outputFile = BuildEventProtocolUtils.createTempOutputFile();
    try (FileOutputStream out = new FileOutputStream(outputFile);
        OutputStream stderr =
            LineProcessingOutputStream.of(new PrintOutputLineProcessor(context))) {
      int exitCode =
          ExternalTask.builder(WorkspaceRoot.fromProject(project))
              .addBlazeCommand(blazeCommandBuilder.build())
              .context(context)
              .stdout(out)
              .stderr(stderr)
              .build()
              .run();
      if (exitCode != 0) {
        // TODO(akhildixit): Fix converting out and stderr to string
        throw new BlazeInfoException(
            String.format(
                "Blaze info failed with exit code %d: \nStdout: %s \nStderr: %s",
                exitCode, out, stderr));
      }
    } catch (IOException e) {
      throw new BlazeInfoException(
          String.format("Error writing blaze info to file %s", outputFile.getPath()), e);
    }
    try {
      return new BufferedInputStream(new FileInputStream(outputFile));
    } catch (FileNotFoundException e) {
      throw new BlazeInfoException(
          String.format("Error reading blaze info from file %s", outputFile.getPath()), e);
    }
  }

  private BuildResult issueBuild(
      BlazeCommand.Builder blazeCommandBuilder, WorkspaceRoot workspaceRoot, BlazeContext context) {
    blazeCommandBuilder.addBlazeFlags(getExtraBuildFlags(blazeCommandBuilder));
    int retVal =
        ExternalTask.builder(workspaceRoot)
            .addBlazeCommand(blazeCommandBuilder.build())
            .context(context)
            .stderr(
                LineProcessingOutputStream.of(
                    BlazeConsoleLineProcessorProvider.getAllStderrLineProcessors(context)))
            .build()
            .run();
    return BuildResult.fromExitCode(retVal);
  }

  @Override
  public Optional<Integer> getMaxCommandLineLength() {
    // Return a conservative value.
    // `getconf ARG_MAX` returns large values (1048576 on mac, 2097152 on linux) but this is
    // much larger than the actual command line limit seen in practice.
    // On linux, `xargs --show-limits` says "Size of command buffer we are actually using: 131072"
    // so choose a value somewhere south of that, which seems to work.
    return Optional.of(130000);
  }
}
