/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.dependencies;

import static java.util.stream.Collectors.joining;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.async.process.ExternalTask;
import com.google.idea.blaze.base.async.process.LineProcessingOutputStream;
import com.google.idea.blaze.base.async.process.PrintOutputLineProcessor;
import com.google.idea.blaze.base.bazel.BuildSystemProvider;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.base.sync.sharding.QueryResultLineProcessor;
import com.intellij.openapi.project.Project;
import java.util.List;
import javax.annotation.Nullable;

/** Runs a blaze query to derive a set of targets from the project's {@link ImportRoots}. */
public class BlazeQueryDirectoryToTargetProvider implements DirectoryToTargetProvider {

  @Override
  @Nullable
  public List<TargetInfo> doExpandDirectoryTargets(
      Project project, ImportRoots directories, BlazeContext context) {
    return runQuery(project, getQueryString(directories), context);
  }

  private static String getQueryString(ImportRoots directories) {
    StringBuilder query = new StringBuilder();
    query.append(
        directories.rootDirectories().stream()
            .map(w -> String.format("//%s/...", w))
            .collect(joining(" + ")));
    for (WorkspacePath excluded : directories.excludeDirectories()) {
      query.append(String.format(" - //%s/...", excluded));
    }
    return "'" + query + "'";
  }

  /**
   * Runs a herb query synchronously, returning an output list of {@link TargetInfo}, or null if the
   * query failed.
   */
  @Nullable
  private static ImmutableList<TargetInfo> runQuery(
      Project project, String query, BlazeContext context) {
    BlazeCommand command =
        BlazeCommand.builder(getBinaryPath(project), BlazeCommandName.QUERY)
            .addBlazeFlags("--output=label_kind")
            .addBlazeFlags(query)
            .build();

    QueryResultLineProcessor outputProcessor = new QueryResultLineProcessor(t -> true);
    int retVal =
        ExternalTask.builder(WorkspaceRoot.fromProject(project))
            .addBlazeCommand(command)
            .context(context)
            .stdout(LineProcessingOutputStream.of(outputProcessor))
            .stderr(LineProcessingOutputStream.of(new PrintOutputLineProcessor(context)))
            .build()
            .run();
    if (retVal != 0) {
      return null;
    }
    return outputProcessor.getTargets();
  }

  private static String getBinaryPath(Project project) {
    BuildSystemProvider buildSystemProvider = Blaze.getBuildSystemProvider(project);
    return buildSystemProvider.getSyncBinaryPath(project);
  }
}
