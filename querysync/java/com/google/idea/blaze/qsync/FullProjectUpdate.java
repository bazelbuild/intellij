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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.qsync.project.ProjectProto;
import com.google.idea.blaze.qsync.query.QuerySpec;
import com.google.idea.blaze.qsync.query.QuerySummary;
import com.google.idea.blaze.qsync.vcs.VcsState;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * A query strategy based on a query of all targets in the project.
 *
 * <p>This strategy is used when creating a new project from scratch, or when updating the project
 * if a partial query cannot be used.
 *
 * <p>When instantiating this class directly for new project creation, you must call {@link
 * #setVcsState(Optional)} before {@link #createBlazeProject()}.
 */
public class FullProjectUpdate implements ProjectUpdate {

  private final BlazeQueryParser queryParser;
  private final ImmutableList<Path> projectIncludes;
  private final ImmutableList<Path> projectExcludes;
  private final GraphToProjectConverter graphToProjectConverter;

  private QuerySummary queryOutput;
  private Optional<VcsState> vcsState;

  public FullProjectUpdate(
      Context context,
      List<Path> projectIncludes,
      List<Path> projectExcludes,
      PackageReader packageReader) {
    this.projectIncludes = ImmutableList.copyOf(projectIncludes);
    this.projectExcludes = ImmutableList.copyOf(projectExcludes);
    this.queryParser = new BlazeQueryParser(context);
    this.graphToProjectConverter =
        new GraphToProjectConverter(
            packageReader, context, this.projectIncludes, this.projectIncludes);
  }

  @Override
  public Optional<QuerySpec> getQuerySpec() {
    return Optional.of(
        QuerySpec.builder().includePaths(projectIncludes).excludePaths(projectExcludes).build());
  }

  @Override
  public void setQueryOutput(QuerySummary output) {
    this.queryOutput = output;
  }

  public void setVcsState(Optional<VcsState> state) {
    this.vcsState = state;
  }

  @Override
  public BlazeProjectSnapshot createBlazeProject() throws IOException {
    Preconditions.checkNotNull(queryOutput, "queryOutput");
    Preconditions.checkNotNull(vcsState, "vcsState");
    BuildGraphData graph = queryParser.parse(queryOutput.proto());
    ProjectProto.Project project = graphToProjectConverter.createProject(graph);
    return BlazeProjectSnapshot.builder()
        .projectIncludes(projectIncludes)
        .projectExcludes(projectExcludes)
        .queryOutput(queryOutput)
        .vcsState(vcsState)
        .graph(graph)
        .project(project)
        .build();
  }
}
