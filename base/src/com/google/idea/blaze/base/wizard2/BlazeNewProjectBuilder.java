/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.wizard2;

import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.plugin.dependency.PluginDependencyHelper;
import com.google.idea.blaze.base.projectview.ProjectView;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.ProjectViewStorageManager;
import com.google.idea.blaze.base.projectview.parser.ProjectViewParser;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.Blaze.BuildSystem;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import java.io.File;
import java.io.IOException;
import java.util.UUID;

/** Contains the state to build a new project throughout the new project wizard process. */
public final class BlazeNewProjectBuilder {
  private static final Logger LOG = Logger.getInstance(BlazeNewProjectBuilder.class);

  // Stored in user settings as the last imported workspace
  private static final String LAST_IMPORTED_BLAZE_WORKSPACE =
      "blaze-wizard.last-imported-workspace";
  private static final String LAST_IMPORTED_BAZEL_WORKSPACE =
      "blaze-wizard.last-imported-bazel-workspace";

  public static String lastImportedWorkspaceKey(BuildSystem buildSystem) {
    switch (buildSystem) {
      case Blaze:
        return LAST_IMPORTED_BLAZE_WORKSPACE;
      case Bazel:
        return LAST_IMPORTED_BAZEL_WORKSPACE;
      default:
        throw new RuntimeException("Unrecognized build system type: " + buildSystem);
    }
  }

  private final BlazeWizardUserSettings userSettings;
  private BlazeSelectWorkspaceOption workspaceOption;
  private BlazeSelectProjectViewOption projectViewOption;
  private File projectViewFile;
  private ProjectView projectView;
  private ProjectViewSet projectViewSet;
  private String projectName;
  private String projectDataDirectory;
  private WorkspaceRoot workspaceRoot;
  private BuildSystem buildSystem;

  public BlazeNewProjectBuilder() {
    this.userSettings = BlazeWizardUserSettingsStorage.getInstance().copyUserSettings();
  }

  public BlazeWizardUserSettings getUserSettings() {
    return userSettings;
  }

  public BlazeSelectWorkspaceOption getWorkspaceOption() {
    return workspaceOption;
  }

  public BlazeSelectProjectViewOption getProjectViewOption() {
    return projectViewOption;
  }

  public String getProjectName() {
    return projectName;
  }

  public ProjectView getProjectView() {
    return projectView;
  }

  public ProjectViewSet getProjectViewSet() {
    return projectViewSet;
  }

  public String getProjectDataDirectory() {
    return projectDataDirectory;
  }

  public BuildSystem getBuildSystem() {
    return buildSystem;
  }

  public String getBuildSystemName() {
    if (buildSystem != null) {
      return buildSystem.getName();
    }
    return Blaze.defaultBuildSystemName();
  }

  public BlazeNewProjectBuilder setWorkspaceOption(BlazeSelectWorkspaceOption workspaceOption) {
    this.workspaceOption = workspaceOption;
    this.buildSystem = workspaceOption.getBuildSystemForWorkspace();
    return this;
  }

  public BlazeNewProjectBuilder setProjectViewOption(
      BlazeSelectProjectViewOption projectViewOption) {
    this.projectViewOption = projectViewOption;
    return this;
  }

  public BlazeNewProjectBuilder setProjectView(ProjectView projectView) {
    this.projectView = projectView;
    return this;
  }

  public BlazeNewProjectBuilder setProjectViewFile(File projectViewFile) {
    this.projectViewFile = projectViewFile;
    return this;
  }

  public BlazeNewProjectBuilder setProjectViewSet(ProjectViewSet projectViewSet) {
    this.projectViewSet = projectViewSet;
    return this;
  }

  public BlazeNewProjectBuilder setProjectName(String projectName) {
    this.projectName = projectName;
    return this;
  }

  public BlazeNewProjectBuilder setProjectDataDirectory(String projectDataDirectory) {
    this.projectDataDirectory = projectDataDirectory;
    return this;
  }

  /** Commits the project. May report errors. */
  public void commit() throws BlazeProjectCommitException {
    this.workspaceRoot = workspaceOption.getWorkspaceRoot();

    workspaceOption.commit();
    projectViewOption.commit();

    String workspaceKey = lastImportedWorkspaceKey(workspaceOption.getBuildSystemForWorkspace());
    userSettings.put(workspaceKey, workspaceRoot.toString());

    if (!StringUtil.isEmpty(projectDataDirectory)) {
      File projectDataDir = new File(projectDataDirectory);
      if (!projectDataDir.exists()) {
        if (!projectDataDir.mkdirs()) {
          throw new BlazeProjectCommitException(
              "Unable to create the project directory: " + projectDataDirectory);
        }
      }
    }

    try {
      LOG.assertTrue(projectViewFile != null);
      ProjectViewStorageManager.getInstance()
          .writeProjectView(ProjectViewParser.projectViewToString(projectView), projectViewFile);
    } catch (IOException e) {
      throw new BlazeProjectCommitException("Could not create project view file", e);
    }
  }

  /**
   * Commits the project data. This method mustn't fail, because the project has already been
   * created.
   */
  public void commitToProject(Project project) {
    BlazeWizardUserSettingsStorage.getInstance().commit(userSettings);

    BlazeImportSettings importSettings =
        new BlazeImportSettings(
            workspaceRoot.directory().getPath(),
            projectName,
            projectDataDirectory,
            createLocationHash(projectName),
            projectViewFile.getPath(),
            buildSystem);

    BlazeImportSettingsManager.getInstance(project).setImportSettings(importSettings);
    PluginDependencyHelper.addDependencyOnSyncPlugin(project);
    // Initial sync of the project happens in BlazeSyncStartupActivity
  }

  private static String createLocationHash(String projectName) {
    String uuid = UUID.randomUUID().toString();
    uuid = uuid.substring(0, Math.min(uuid.length(), 8));
    return projectName.replaceAll("[^a-zA-Z0-9]", "") + "-" + uuid;
  }
}
