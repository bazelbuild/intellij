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

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.qsync.project.ProjectProto;
import com.google.idea.blaze.qsync.project.ProjectProto.Project;
import com.google.idea.blaze.qsync.project.ProjectProto.ProjectPath.Base;
import java.nio.file.Path;

/** Updates project protos with a content entry for generated sources */
public class GeneratedSourceProjectUpdater {

  private final Project project;
  private final Path genSrcCacheDirectory;
  private final ImmutableList<Path> genSrcFolders;

  public GeneratedSourceProjectUpdater(
      Project project, Path genSrcCacheDirectory, ImmutableList<Path> genSrcFolders) {
    this.project = project;
    this.genSrcCacheDirectory = genSrcCacheDirectory;
    this.genSrcFolders = genSrcFolders;
  }

  public Project addGenSrcContentEntry() {
    if (genSrcFolders.isEmpty()) {
      return project;
    }

    Project.Builder protoBuilder = project.toBuilder();
    ProjectProto.Module.Builder workspaceModule =
        protoBuilder.getModulesBuilderList().stream()
            .filter(m -> m.getName().equals(".workspace"))
            .findFirst()
            .orElseThrow();

    ProjectProto.ContentEntry.Builder genSourcesContentEntry =
        ProjectProto.ContentEntry.newBuilder()
            .setRoot(
                ProjectProto.ProjectPath.newBuilder()
                    .setBase(Base.PROJECT)
                    .setPath(genSrcCacheDirectory.toString()));

    for (Path path : genSrcFolders) {
      genSourcesContentEntry.addSources(
          ProjectProto.SourceFolder.newBuilder()
              .setProjectPath(
                  ProjectProto.ProjectPath.newBuilder()
                      .setBase(Base.PROJECT)
                      .setPath(genSrcCacheDirectory.resolve(path.getFileName()).toString())
                      .build())
              .setIsTest(false)
              .setIsGenerated(true)
              .setPackagePrefix(""));
    }
    workspaceModule.addContentEntries(genSourcesContentEntry);
    return protoBuilder.build();
  }
}
