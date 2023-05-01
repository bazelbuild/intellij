/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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

import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.qsync.project.BlazeProjectDataStorage;
import com.google.idea.blaze.qsync.project.ProjectProto;
import com.google.idea.blaze.qsync.project.ProjectProto.ContentRoot.Base;
import com.google.idea.blaze.qsync.project.ProjectProto.Project;
import com.google.idea.blaze.qsync.project.ProjectProtoAugmenter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Augments project proto to include a content entry for folders in the generated sources directory
 */
public class GeneratedSourcesProjectProtoAugmenter implements ProjectProtoAugmenter {
  private final PathSupplier pathSupplier;

  /**
   * When invoked, {@code pathSupplier} should return a {@link List} of {@link Path} objects for all
   * source roots in the generated sources directory.
   */
  public GeneratedSourcesProjectProtoAugmenter(PathSupplier pathSupplier) {
    this.pathSupplier = pathSupplier;
  }

  @Override
  public Project augment(Context<?> context, Project projectProto) {
    List<Path> genSrcPaths = pathSupplier.getPaths(context);
    if (genSrcPaths.isEmpty()) {
      return projectProto;
    }

    Project.Builder protoBuilder = projectProto.toBuilder();
    ProjectProto.Module.Builder workspaceModule =
        protoBuilder.getModulesBuilderList().stream()
            .filter(m -> m.getName().equals(".workspace"))
            .findFirst()
            .orElseThrow();
    // TODO: we shouldn't assume a single module here. Only remove the workspace module.
    protoBuilder.clearModules();

    Path generatedSourcePath =
        Paths.get(
            BlazeProjectDataStorage.BLAZE_DATA_SUBDIRECTORY,
            BlazeProjectDataStorage.GEN_SRC_DIRECTORY);
    ProjectProto.ContentEntry.Builder genSourcesContentEntry =
        ProjectProto.ContentEntry.newBuilder()
            .setRoot(
                ProjectProto.ContentRoot.newBuilder()
                    .setBase(Base.PROJECT)
                    .setPath(generatedSourcePath.toString()));

    for (Path path : genSrcPaths) {
      genSourcesContentEntry.addSources(
          ProjectProto.SourceFolder.newBuilder()
              .setPath(generatedSourcePath.resolve(path.getFileName()).toString())
              .setIsTest(false)
              .setIsGenerated(true)
              .setPackagePrefix(""));
    }
    workspaceModule.addContentEntries(genSourcesContentEntry);
    protoBuilder.addModules(workspaceModule);
    return protoBuilder.build();
  }

  @FunctionalInterface
  interface PathSupplier {
    List<Path> getPaths(Context<?> context);
  }
}
