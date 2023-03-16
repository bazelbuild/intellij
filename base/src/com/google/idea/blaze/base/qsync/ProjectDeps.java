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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Preconditions;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.base.sync.projectview.LanguageSupport;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverImpl;
import com.google.idea.blaze.qsync.project.ProjectDefinition;
import com.intellij.openapi.project.Project;

/**
 * Stores classes that are needed for interaction between the querysync code and the wider plugin
 * codebase. Things in here are available at the earliest stage of sync, and are derived from the
 * basic project configuration.
 *
 * <p>All code within querysync should acquire instances of these classes from here via {@link
 * QuerySyncProjectDataManager} (or injection) rather than getting them directly from their static
 * getter methods.
 */
public class ProjectDeps {

  private final ProjectViewSet projectViewSet;
  private final ImportRoots importRoots;
  private final WorkspaceLanguageSettings workspaceLanguageSettings;
  private final WorkspacePathResolver workspacePathResolver;
  private final ProjectDefinition projectDefinition;

  ProjectDeps(
      ProjectViewSet projectViewSet,
      ImportRoots importRoots,
      WorkspaceLanguageSettings workspaceLanguageSettings,
      WorkspacePathResolver workspacePathResolver,
      ProjectDefinition projectDefinition) {
    this.projectViewSet = projectViewSet;
    this.importRoots = importRoots;
    this.workspaceLanguageSettings = workspaceLanguageSettings;
    this.workspacePathResolver = workspacePathResolver;
    this.projectDefinition = projectDefinition;
  }

  public ProjectViewSet projectViewSet() {
    return projectViewSet;
  }

  public ImportRoots importRoots() {
    return importRoots;
  }

  public WorkspaceLanguageSettings workspaceLanguageSettings() {
    return workspaceLanguageSettings;
  }

  public WorkspacePathResolver workspacePathResolver() {
    return workspacePathResolver;
  }

  public ProjectDefinition projectDefinition() {
    return projectDefinition;
  }

  public static Builder builder(Project project) {
    return new Builder(project);
  }

  /**
   * Builder for {@link ProjectDeps}.
   *
   * <p>This is not really a normal builder. Rather, it stores the subset of {@link ProjectDeps}
   * that can be constructed without a {@link BlazeContext}. The rest is created via {@link #build}
   * when we have context (which allows any messages generated to be user visible).
   */
  public static class Builder {

    private final Project project;
    private final BlazeImportSettings importSettings;
    private final WorkspaceRoot workspaceRoot;

    public Builder(Project project) {
      this.project = Preconditions.checkNotNull(project);
      this.importSettings =
          Preconditions.checkNotNull(
              BlazeImportSettingsManager.getInstance(project).getImportSettings());
      this.workspaceRoot = WorkspaceRoot.fromImportSettings(importSettings);
    }

    public Project getProject() {
      return project;
    }

    public BlazeImportSettings getSettings() {
      return importSettings;
    }

    public WorkspaceRoot getWorkspaceRoot() {
      return workspaceRoot;
    }

    public ProjectDeps build(BlazeContext context) {
      Preconditions.checkNotNull(project, "buildSystemName");
      Preconditions.checkNotNull(workspaceRoot, "workspaceRoot");
      // TODO we may need to get the WorkspacePathResolver from the VcsHandler, as the old sync
      // does inside ProjectStateSyncTask.computeWorkspacePathResolverAndProjectView
      // Things will probably work without that, but we should understand why the other
      // implementations of WorkspacePathResolver exists. Perhaps they are performance
      // optimizations?
      WorkspacePathResolver workspacePathResolver = new WorkspacePathResolverImpl(workspaceRoot);
      ProjectViewSet projectViewSet =
          checkNotNull(
              ProjectViewManager.getInstance(project)
                  .reloadProjectView(context, workspacePathResolver));
      ImportRoots ir =
          ImportRoots.builder(workspaceRoot, importSettings.getBuildSystem())
              .add(projectViewSet)
              .build();
      WorkspaceLanguageSettings workspaceLanguageSettings =
          LanguageSupport.createWorkspaceLanguageSettings(projectViewSet);
      ProjectDefinition spec = ProjectDefinition.create(ir.rootPaths(), ir.excludePaths());
      return new ProjectDeps(
          projectViewSet, ir, workspaceLanguageSettings, workspacePathResolver, spec);
    }
  }
}
