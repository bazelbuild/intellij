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
package com.google.idea.blaze.qsync;

import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.qsync.project.BlazeProjectSnapshot;
import com.google.idea.blaze.qsync.project.BuildGraphData;
import com.google.idea.blaze.qsync.project.PostQuerySyncData;
import com.google.idea.blaze.qsync.project.ProjectDefinition;
import com.google.idea.blaze.qsync.project.ProjectProto;
import com.google.idea.blaze.qsync.query.QuerySpec;
import com.google.idea.blaze.qsync.query.QuerySummary;
import com.google.idea.blaze.qsync.vcs.VcsState;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * A project update based on a query of all targets in the project.
 *
 * <p>This strategy is used when creating a new project from scratch, or when updating the project
 * if a partial query cannot be used.
 *
 * <p>When instantiating this class directly for new project creation, you must call {@link
 * #setVcsState(Optional)} before {@link #createBlazeProject()}.
 */
public class FullProjectUpdate implements RefreshOperation {

  private final Context context;
  private final Path workspaceRoot;
  private final BlazeQueryParser queryParser;
  private final ProjectDefinition projectDefinition;
  private final GraphToProjectConverter graphToProjectConverter;

  private final PostQuerySyncData.Builder result;

  public FullProjectUpdate(
      Context context,
      Path workspaceRoot,
      ProjectDefinition definition,
      PackageReader packageReader) {
    this.context = context;
    this.workspaceRoot = workspaceRoot;
    this.result = PostQuerySyncData.builder().setProjectDefinition(definition);
    this.projectDefinition = definition;
    this.queryParser = new BlazeQueryParser(context);
    this.graphToProjectConverter =
        new GraphToProjectConverter(packageReader, context, projectDefinition);
  }

  @Override
  public Optional<QuerySpec> getQuerySpec() throws IOException {
    QuerySpec querySpec = projectDefinition.deriveQuerySpec(context, workspaceRoot);
    return Optional.of(querySpec);
  }

  @Override
  public void setQueryOutput(QuerySummary output) {
    result.setQuerySummary(output);
  }

  public void setVcsState(Optional<VcsState> state) {
    result.setVcsState(state);
  }

  @Override
  public BlazeProjectSnapshot createBlazeProject() throws IOException {
    PostQuerySyncData newData = result.build();
    BuildGraphData graph = queryParser.parse(newData.querySummary());
    ProjectProto.Project project = graphToProjectConverter.createProject(graph);
    return BlazeProjectSnapshot.builder().queryData(newData).graph(graph).project(project).build();
  }
}
