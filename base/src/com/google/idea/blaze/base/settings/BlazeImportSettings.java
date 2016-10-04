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
package com.google.idea.blaze.base.settings;

import com.google.idea.blaze.base.settings.Blaze.BuildSystem;
import com.intellij.util.xmlb.annotations.Tag;
import javax.annotation.Nullable;

// The tag here is for legacy migration support.
// No longer needed and can be removed with {@link BlazeImportSettingsManagerLegacy}
/** Project settings that are set at import time. */
@Tag("BlazeProjectSettings")
public final class BlazeImportSettings {

  private String workspaceRoot = "";

  private String projectName = "";

  private String projectDataDirectory = "";

  private String locationHash = "";

  private String projectViewFile;

  private BuildSystem buildSystem =
      BuildSystem.Blaze; // default for backwards compatibility with existing projects.

  // Used by bean serialization
  @SuppressWarnings("unused")
  BlazeImportSettings() {}

  public BlazeImportSettings(
      String workspaceRoot,
      String projectName,
      String projectDataDirectory,
      String locationHash,
      String projectViewFile,
      BuildSystem buildSystem) {
    this.workspaceRoot = workspaceRoot;
    this.projectName = projectName;
    this.projectDataDirectory = projectDataDirectory;
    this.locationHash = locationHash;
    this.projectViewFile = projectViewFile;
    this.buildSystem = buildSystem;
  }

  @SuppressWarnings("unused")
  public String getWorkspaceRoot() {
    return workspaceRoot;
  }

  @SuppressWarnings("unused")
  public String getProjectName() {
    return projectName;
  }

  @SuppressWarnings("unused")
  public String getProjectDataDirectory() {
    return projectDataDirectory;
  }

  /** Hash used to give the project a unique directory in the system directory. */
  @SuppressWarnings("unused")
  public String getLocationHash() {
    return locationHash;
  }

  /** The user's local project view file */
  @SuppressWarnings("unused")
  public String getProjectViewFile() {
    return projectViewFile;
  }

  /** The build system used for the project. */
  @SuppressWarnings("unused")
  public BuildSystem getBuildSystem() {
    return buildSystem;
  }

  // Used by bean serialization
  @SuppressWarnings("unused")
  public void setWorkspaceRoot(String workspaceRoot) {
    this.workspaceRoot = workspaceRoot;
  }

  // Used by bean serialization
  @SuppressWarnings("unused")
  public void setProjectName(String projectName) {
    this.projectName = projectName;
  }

  // Used by bean serialization
  @SuppressWarnings("unused")
  public void setProjectDataDirectory(String projectDataDirectory) {
    this.projectDataDirectory = projectDataDirectory;
  }

  // Used by bean serialization
  @SuppressWarnings("unused")
  public void setLocationHash(String locationHash) {
    this.locationHash = locationHash;
  }

  // Used by bean serialization
  @SuppressWarnings("unused")
  public void setProjectViewFile(@Nullable String projectViewFile) {
    this.projectViewFile = projectViewFile;
  }

  // Used by bean serialization -- legacy import support
  @SuppressWarnings("unused")
  public void setAsProjectFile(@Nullable String projectViewFile) {
    this.projectViewFile = projectViewFile;
  }

  // Used by bean serialization
  @SuppressWarnings("unused")
  public void setBuildSystem(BuildSystem buildSystem) {
    this.buildSystem = buildSystem;
  }
}
