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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.qsync.project.ProjectProto;
import com.google.idea.blaze.qsync.query.Query;
import com.google.idea.blaze.qsync.query.Query.SourceFile;
import com.google.idea.blaze.qsync.query.QuerySpec;
import com.google.idea.blaze.qsync.query.QuerySummary;
import com.google.idea.blaze.qsync.vcs.VcsState;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * Implements a query strategy based on querying a minimal set of packages derived from the VCS
 * working set.
 *
 * <p>Instance of this class will be returned from {@link
 * ProjectRefresher#startPartialUpdate(Context, BlazeProjectSnapshot, Optional)} when appropriate.
 */
class PartialProjectUpdate implements ProjectUpdate {

  private final BlazeProjectSnapshot previousState;
  private final BlazeQueryParser queryParser;
  private final GraphToProjectConverter graphToProjectConverter;
  private final Optional<VcsState> vcsState;
  @VisibleForTesting final ImmutableSet<Path> modifiedPackages;
  @VisibleForTesting final ImmutableSet<Path> deletedPackages;
  private QuerySummary partialQuery;

  PartialProjectUpdate(
      Context context,
      PackageReader packageReader,
      BlazeProjectSnapshot previousState,
      Optional<VcsState> vcsState,
      ImmutableSet<Path> modifiedPackages,
      ImmutableSet<Path> deletedPackages) {
    this.previousState = previousState;
    this.vcsState = vcsState;
    this.modifiedPackages = modifiedPackages;
    this.deletedPackages = deletedPackages;
    this.queryParser = new BlazeQueryParser(context);
    this.graphToProjectConverter =
        new GraphToProjectConverter(
            packageReader,
            context,
            previousState.projectIncludes(),
            previousState.projectIncludes());
  }

  @Override
  public Optional<QuerySpec> getQuerySpec() {
    if (modifiedPackages.isEmpty()) {
      // this can happen if the user just deletes a build file that doesn't have a parent package.
      return Optional.empty();
    }
    return Optional.of(QuerySpec.builder().includePackages(modifiedPackages).build());
  }

  @Override
  public void setQueryOutput(QuerySummary output) {
    this.partialQuery = output;
  }

  @Override
  public BlazeProjectSnapshot createBlazeProject() throws IOException {
    Preconditions.checkNotNull(partialQuery, "queryOutput");
    Query.Summary effectiveQuery = applyDelta();
    BuildGraphData graph = queryParser.parse(effectiveQuery);
    ProjectProto.Project project = graphToProjectConverter.createProject(graph);
    return previousState.toBuilder()
        .vcsState(vcsState)
        .queryOutput(effectiveQuery)
        .graph(graph)
        .project(project)
        .build();
  }

  /**
   * Calculates the effective query output, based on an earlier full query output, the output from a
   * partial query, and any deleted packages.
   */
  @VisibleForTesting
  Query.Summary applyDelta() {
    // copy all unaffected rules / source files to result:
    Map<Label, SourceFile> newSourceFiles = Maps.newHashMap();
    for (Map.Entry<Label, SourceFile> sfEntry :
        previousState.queryOutput().getSourceFilesMap().entrySet()) {
      Path buildPackage = sfEntry.getKey().getPackage();
      if (!(deletedPackages.contains(buildPackage)
          || partialQuery.getPackages().contains(buildPackage))) {
        newSourceFiles.put(sfEntry.getKey(), sfEntry.getValue());
      }
    }
    Map<Label, Query.Rule> newRules = Maps.newHashMap();
    for (Map.Entry<Label, Query.Rule> ruleEntry :
        previousState.queryOutput().getRulesMap().entrySet()) {
      Path buildPackage = ruleEntry.getKey().getPackage();
      if (!(deletedPackages.contains(buildPackage)
          || partialQuery.getPackages().contains(buildPackage))) {
        newRules.put(ruleEntry.getKey(), ruleEntry.getValue());
      }
    }
    // now add all rules / source files from the delta
    newSourceFiles.putAll(partialQuery.getSourceFilesMap());
    newRules.putAll(partialQuery.getRulesMap());
    return QuerySummary.newBuilder()
        .putAllSourceFiles(newSourceFiles)
        .putAllRules(newRules)
        .build()
        .proto();
  }
}
