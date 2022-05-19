/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.protoeditor;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.command.buildresult.OutputArtifactResolver;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.GenericIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.GenericBlazeRules;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.sync.libraries.BlazeExternalLibraryProvider;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.google.common.collect.ImmutableList.toImmutableList;

/** Provides out-of-project proto sources for indexing. */
public final class BlazeProtoAdditionalLibraryRootsProvider extends BlazeExternalLibraryProvider {
  private static final BoolExperiment useProtoAdditionalLibraryRootsProvider =
      new BoolExperiment("use.proto.additional.library.roots.provider", true);

  @Override
  protected String getLibraryName() {
    return "External Protocol Buffers";
  }

  @Override
  protected ImmutableList<File> getLibraryFiles(Project project, BlazeProjectData projectData) {
    if (!useProtoAdditionalLibraryRootsProvider.getValue()) {
      return ImmutableList.of();
    }
    ImportRoots importRoots = ImportRoots.forProjectSafe(project);
    return importRoots != null
        ? getLibraryFiles(project, projectData, importRoots)
        : ImmutableList.of();
  }

  static ImmutableList<File> getLibraryFiles(
      Project project, BlazeProjectData projectData, ImportRoots importRoots) {
    WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProjectSafe(project);
    if (workspaceRoot == null) {
      return ImmutableList.of();
    }

    return externalProtoArtifacts(projectData)
        .map(
            (location) ->
                OutputArtifactResolver.resolve(
                    project, projectData.getArtifactLocationDecoder(), location))
        .filter(Objects::nonNull)
        .collect(toImmutableList());
  }

  @NotNull
  static Stream<ArtifactLocation> externalProtoArtifacts(BlazeProjectData projectData) {
    return projectData.getTargetMap().targets().stream()
        .filter((info) -> info.getKind() == GenericBlazeRules.RuleTypes.PROTO_LIBRARY.getKind())
        .map(TargetIdeInfo::getSources)
        .flatMap(Collection::stream)
        .filter(ArtifactLocation::isExternal);
  }
}
