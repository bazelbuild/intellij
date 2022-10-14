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

import java.util.UUID;
import javax.annotation.Nullable;

/** Project settings that are set at import time. */
public final class BlazeImportSettings {

  private String workspaceRoot = "";

  private String projectName = "";

  private String projectDataDirectory = "";

  private String locationHash = "";

  private String projectViewFile;

  // Prototype: import project from project proto generated in advance.
  // This will only ever be non-null if the prototype is enabled via experiment
  // "sync.prototype.project.proto.enabled" AND the user selected the option to import from project
  // proto in the project creation wizard. All prototype functionality should be gated on this
  // being non-null to ensure prototype code does not cause problems when not enabled.
  private String projectProtoFile;

  // default for backwards compatibility with existing projects
  private BuildSystemName buildSystem = BuildSystemName.Blaze;

  // Used by bean serialization
  @SuppressWarnings("unused")
  BlazeImportSettings() {}

  public BlazeImportSettings(
      String workspaceRoot,
      String projectName,
      String projectDataDirectory,
      String projectViewFile,
      BuildSystemName buildSystemName) {
    this(workspaceRoot, projectName, projectDataDirectory, projectViewFile, null, buildSystemName);
  }

  public BlazeImportSettings(
      String workspaceRoot,
      String projectName,
      String projectDataDirectory,
      String projectViewFile,
      String projectProtoFile,
      BuildSystemName buildSystemName) {
    this.workspaceRoot = workspaceRoot;
    this.projectName = projectName;
    this.projectDataDirectory = projectDataDirectory;
    this.locationHash = createLocationHash(projectName);
    this.projectViewFile = projectViewFile;
    this.buildSystem = buildSystemName;
    this.projectProtoFile = projectProtoFile;
  }

  private static String createLocationHash(String projectName) {
    String uuid = UUID.randomUUID().toString();
    uuid = uuid.substring(0, Math.min(uuid.length(), 8));
    return projectName.replaceAll("[^a-zA-Z0-9]", "") + "-" + uuid;
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

  public String getProjectProtoFile() {
    return projectProtoFile;
  }

  /** The build system used for the project. */
  @SuppressWarnings("unused")
  public BuildSystemName getBuildSystem() {
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
  public void setBuildSystem(BuildSystemName buildSystem) {
    this.buildSystem = buildSystem;
  }
}
