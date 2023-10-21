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
package com.google.idea.blaze.gazelle;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.async.process.ExternalTask;
import com.google.idea.blaze.base.async.process.LineProcessingOutputStream;
import com.google.idea.blaze.base.bazel.BuildSystem.BuildInvoker;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.issueparser.BlazeIssueParser.Parser;
import com.google.idea.blaze.base.issueparser.IssueOutputLineProcessor;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.scope.BlazeContext;
import java.io.ByteArrayOutputStream;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class GazelleRunnerImpl extends GazelleRunner {

  @Override
  public GazelleRunResult runBlazeGazelle(
      BlazeContext context,
      BuildInvoker invoker,
      WorkspaceRoot workspaceRoot,
      List<String> blazeFlags,
      Label gazelleTarget,
      Collection<WorkspacePath> directories,
      ImmutableList<Parser> issueParsers) {
    BlazeCommand command = GazelleRunner.createGazelleRunCommand(invoker,
            blazeFlags, gazelleTarget, directories);
    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    int exitCode =
        ExternalTask.builder(workspaceRoot)
            .addBlazeCommand(command)
            .context(context)
            .stderr(
                LineProcessingOutputStream.of(new IssueOutputLineProcessor(context, issueParsers)))
            .stdout(stdout)
            .build()
            .run();
    if (exitCode != 0) {
      // Note that gazelle won't return a non-0 exit code on a failure unless we specify the -strict
      // flag.
      // This exception will only catch the instances of bazel failing to build the gazelle target.
      return GazelleRunResult.FAILED_TO_RUN;
    }
    // If the issue parsers have caught at least one error, they will have modified the context.
    return context.hasErrors() ? GazelleRunResult.RAN_WITH_ERRORS : GazelleRunResult.SUCCESS;
  }
}
