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

import com.google.errorprone.annotations.MustBeClosed;
import com.google.idea.blaze.base.async.process.ExternalTask;
import com.google.idea.blaze.base.async.process.LineProcessingOutputStream;
import com.google.idea.blaze.base.bazel.BuildSystem;
import com.google.idea.blaze.base.bazel.BuildSystem.BuildInvoker;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.console.BlazeConsoleLineProcessorProvider;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.qsync.query.QuerySpec;
import com.intellij.openapi.project.Project;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

/** The default implementation of QueryRunner. */
public class BazelBinaryQueryRunner implements QueryRunner {
  private final Project project;
  private final BuildSystem buildSystem;
  private final Path workspaceRoot;

  public BazelBinaryQueryRunner(Project project, BuildSystem buildSystem, Path workspaceRoot) {
    this.project = project;
    this.buildSystem = buildSystem;
    this.workspaceRoot = workspaceRoot;
  }

  @Override
  @MustBeClosed
  public InputStream runQuery(QuerySpec query, BlazeContext context) throws IOException {

    BuildInvoker invoker = buildSystem.getDefaultInvoker(project, context);

    BlazeCommand builder =
        BlazeCommand.builder(invoker, BlazeCommandName.QUERY)
            .addBlazeFlags(query.getQueryArgs())
            .build();

    File protoFile = new File("/tmp/q.proto");
    FileOutputStream out = new FileOutputStream(protoFile);
    LineProcessingOutputStream lpos =
        LineProcessingOutputStream.of(
            BlazeConsoleLineProcessorProvider.getAllStderrLineProcessors(context));
    ExternalTask.builder(workspaceRoot.toFile())
        .addBlazeCommand(builder)
        .context(context)
        .stdout(out)
        .stderr(lpos)
        .build()
        .run();

    return new BufferedInputStream(new FileInputStream(protoFile));
  }
}
