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
package com.google.idea.blaze.android.sync.importer;

import com.android.annotations.NonNull;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BuildSystem;
import com.google.idea.blaze.base.sync.projectview.ProjectViewTargetImportFilter;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.java.sync.importer.JavaSourceFilter;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/** Inputs used for importing an Android project. */
public final class BlazeImportInput {
  public static final BoolExperiment createLooksLikeAarLibrary =
      new BoolExperiment("create.resource.lookslikeaarlibrary", true);

  public final @NonNull WorkspaceRoot workspaceRoot;
  public final @NonNull ProjectViewSet projectViewSet;
  public final @NonNull TargetMap targetMap;
  public final @NonNull ArtifactLocationDecoder artifactLocationDecoder;
  public final @NonNull BuildSystem buildSystem;
  public final boolean createFakeAarLibrariesExperiment;

  public BlazeImportInput(
      @NonNull WorkspaceRoot workspaceRoot,
      @NonNull ProjectViewSet projectViewSet,
      @NonNull TargetMap targetMap,
      @NonNull ArtifactLocationDecoder artifactLocationDecoder,
      @NonNull BuildSystem buildSystem,
      boolean createFakeAarLibrariesExperiment) {
    this.workspaceRoot = workspaceRoot;
    this.projectViewSet = projectViewSet;
    this.targetMap = targetMap;
    this.artifactLocationDecoder = artifactLocationDecoder;
    this.buildSystem = buildSystem;
    this.createFakeAarLibrariesExperiment = createFakeAarLibrariesExperiment;
  }

  public static BlazeImportInput forProject(
      @NonNull Project project,
      @NonNull WorkspaceRoot workspaceRoot,
      @NonNull ProjectViewSet projectViewSet,
      @NonNull BlazeProjectData projectData) {
    return forProject(
        project,
        workspaceRoot,
        projectViewSet,
        projectData.getTargetMap(),
        projectData.getArtifactLocationDecoder());
  }

  public static BlazeImportInput forProject(
      @NonNull Project project,
      @NonNull WorkspaceRoot workspaceRoot,
      @NonNull ProjectViewSet projectViewSet,
      @NonNull TargetMap targetMap,
      @NonNull ArtifactLocationDecoder artifactLocationDecoder) {
    return new BlazeImportInput(
        workspaceRoot,
        projectViewSet,
        targetMap,
        artifactLocationDecoder,
        Blaze.getBuildSystem(project),
        createLooksLikeAarLibrary.getValue());
  }

  @NotNull
  public JavaSourceFilter createSourceFilter() {
    return new JavaSourceFilter(buildSystem, workspaceRoot, projectViewSet, targetMap);
  }

  @NotNull
  public ProjectViewTargetImportFilter createImportFilter() {
    return new ProjectViewTargetImportFilter(buildSystem, workspaceRoot, projectViewSet);
  }
}
