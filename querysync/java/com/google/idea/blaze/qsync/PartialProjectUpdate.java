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

  private static Path blazePackageFromTargetName(String target) {
    Preconditions.checkState(target.startsWith("//"), "Invalid target: %s", target);
    int colonPos = target.indexOf(':');
    Preconditions.checkState(colonPos > 1, "Invalid target: %s", target);
    return Path.of(target.substring(2, colonPos));
  }

  /**
   * Calculates the effective query output, based on an earlier full query output, the output from a
   * partial query, and any deleted packages.
   */
  @VisibleForTesting
  Query.Summary applyDelta() {
    // copy all unaffected rules / source files to result:
    Map<String, SourceFile> newSourceFiles = Maps.newHashMap();
    for (Map.Entry<String, SourceFile> sfEntry :
        previousState.queryOutput().proto().getSourceFilesMap().entrySet()) {
      Path buildPackage = blazePackageFromTargetName(sfEntry.getKey());
      if (!(deletedPackages.contains(buildPackage)
          || partialQuery.getPackages().contains(buildPackage))) {
        newSourceFiles.put(sfEntry.getKey(), sfEntry.getValue());
      }
    }
    Map<String, Query.Rule> newRules = Maps.newHashMap();
    for (Map.Entry<String, Query.Rule> ruleEntry :
        previousState.queryOutput().proto().getRulesMap().entrySet()) {
      Path buildPackage = blazePackageFromTargetName(ruleEntry.getKey());
      if (!(deletedPackages.contains(buildPackage)
          || partialQuery.getPackages().contains(buildPackage))) {
        newRules.put(ruleEntry.getKey(), ruleEntry.getValue());
      }
    }
    // now add all rules / source files from the delta
    newSourceFiles.putAll(partialQuery.proto().getSourceFilesMap());
    newRules.putAll(partialQuery.proto().getRulesMap());
    return Query.Summary.newBuilder()
        .putAllSourceFiles(newSourceFiles)
        .putAllRules(newRules)
        .build();
  }
}
