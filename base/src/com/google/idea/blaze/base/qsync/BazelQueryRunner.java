/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.qsync;

import com.google.idea.blaze.base.bazel.BuildSystem;
import com.google.idea.blaze.base.bazel.BuildSystem.BuildInvoker;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeCommandRunner;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.query.QuerySpec;
import com.google.idea.blaze.qsync.query.QuerySummary;
import com.intellij.openapi.project.Project;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** The default implementation of QueryRunner. */
public class BazelQueryRunner implements QueryRunner {
  private final Project project;
  private final BuildSystem buildSystem;

  public BazelQueryRunner(Project project, BuildSystem buildSystem) {
    this.project = project;
    this.buildSystem = buildSystem;
  }

  @Override
  public QuerySummary runQuery(QuerySpec query, BlazeContext context)
      throws IOException, BuildException {
    BuildInvoker invoker = buildSystem.getDefaultInvoker(project, context);
    BlazeCommandRunner commandRunner = invoker.getCommandRunner();

    BlazeCommand.Builder commandBuilder = BlazeCommand.builder(invoker, BlazeCommandName.QUERY);
    commandBuilder.addBlazeFlags(query.getQueryFlags());
    String queryExp = query.getQueryExpression();
    if (commandRunner.getMaxCommandLineLength().map(max -> queryExp.length() > max).orElse(false)) {
      // Query is too long, write it to a file.
      Path tmpFile =
          Files.createTempFile(
              Files.createDirectories(Path.of(project.getBasePath(), "tmp")), "query", ".txt");
      tmpFile.toFile().deleteOnExit();
      Files.writeString(tmpFile, queryExp, StandardOpenOption.WRITE);
      commandBuilder.addBlazeFlags("--query_file", tmpFile.toString());
    } else {
      commandBuilder.addBlazeFlags(queryExp);
    }
    try (BuildResultHelper buildResultHelper = invoker.createBuildResultHelper();
        InputStream in =
            commandRunner.runQuery(project, commandBuilder, buildResultHelper, context)) {
      return QuerySummary.create(in);
    }
  }
}
