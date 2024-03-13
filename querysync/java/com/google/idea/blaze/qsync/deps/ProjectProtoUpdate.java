/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.qsync.deps;

import com.google.common.collect.Maps;
import com.google.idea.blaze.qsync.project.BlazeProjectDataStorage;
import com.google.idea.blaze.qsync.project.ProjectProto;
import com.google.idea.blaze.qsync.project.ProjectProto.Library;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * Helper class for making a number of updates to the project proto.
 *
 * <p>This class provides a convenient way of accessing and updating various interesting parts of
 * the project proto, such as the {@code .workspace} module and libraries by name.
 */
public class ProjectProtoUpdate {

  private final ProjectProto.Project.Builder project;
  private final ProjectProto.Module.Builder workspaceModule;
  private final Map<String, Library.Builder> libraries = Maps.newHashMap();
  private final Map<Path, ArtifactDirectoryBuilder> artifactDirs = Maps.newHashMap();

  public ProjectProtoUpdate(ProjectProto.Project existingProject) {
    this.project = existingProject.toBuilder();
    this.workspaceModule = getWorkspaceModuleBuilder(project);
  }

  private static ProjectProto.Module.Builder getWorkspaceModuleBuilder(
      ProjectProto.Project.Builder project) {
    for (int i = 0; i < project.getModulesCount(); i++) {
      if (project.getModules(i).getName().equals(BlazeProjectDataStorage.WORKSPACE_MODULE_NAME)) {
        return project.getModulesBuilder(i);
      }
    }
    throw new IllegalArgumentException(
        "Module with name "
            + BlazeProjectDataStorage.WORKSPACE_MODULE_NAME
            + " not found in project proto.");
  }

  public ProjectProto.Project.Builder project() {
    return project;
  }

  public ProjectProto.Module.Builder workspaceModule() {
    return workspaceModule;
  }

  /** Gets a builder for a library, creating it if it doesn't already exist. */
  public ProjectProto.Library.Builder library(String name) {
    if (!libraries.containsKey(name)) {
      Optional<ProjectProto.Library.Builder> existingProto =
          project.getLibraryBuilderList().stream()
              .filter(l -> l.getName().equals(name))
              .findFirst();

      if (existingProto.isPresent()) {
        libraries.put(name, existingProto.get());
      } else {
        libraries.put(name, project.addLibraryBuilder().setName(name));
      }
    }
    return libraries.get(name);
  }

  public ArtifactDirectoryBuilder artifactDirectory(Path relativePath) {
    return artifactDirs.computeIfAbsent(relativePath, ArtifactDirectoryBuilder::new);
  }

  public ProjectProto.Project build() {
    artifactDirs.values().forEach(d -> d.addTo(project.getArtifactDirectoriesBuilder()));
    return project.build();
  }
}
