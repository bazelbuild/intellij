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

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableCollection;
import com.google.idea.blaze.common.artifact.BuildArtifactCache;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.artifacts.BuildArtifact;
import com.google.idea.blaze.qsync.java.JavaArtifactInfo;
import com.google.idea.blaze.qsync.java.SrcJarInnerPathFinder;
import com.google.idea.blaze.qsync.java.SrcJarInnerPathFinder.JarPath;
import com.google.idea.blaze.qsync.project.ProjectDefinition;
import com.google.idea.blaze.qsync.project.ProjectPath;
import com.google.idea.blaze.qsync.project.ProjectProto;
import com.google.idea.blaze.qsync.project.TestSourceGlobMatcher;
import java.nio.file.Path;

/**
 * Adds in-project generated {@code .srcjar} files to the project proto. This allows these sources
 * to be resolved and viewed.
 */
public class AddProjectGenSrcJars implements ProjectProtoUpdateOperation {

  private final Supplier<ImmutableCollection<TargetBuildInfo>> builtTargetsSupplier;
  private final BuildArtifactCache buildCache;
  private final ProjectDefinition projectDefinition;
  private final SrcJarInnerPathFinder srcJarInnerPathFinder;
  private final TestSourceGlobMatcher testSourceMatcher;

  public AddProjectGenSrcJars(
      Supplier<ImmutableCollection<TargetBuildInfo>> builtTargetsSupplier,
      ProjectDefinition projectDefinition,
      BuildArtifactCache buildCache,
      SrcJarInnerPathFinder srcJarInnerPathFinder) {
    this.builtTargetsSupplier = builtTargetsSupplier;
    this.projectDefinition = projectDefinition;
    this.buildCache = buildCache;
    this.srcJarInnerPathFinder = srcJarInnerPathFinder;
    testSourceMatcher = TestSourceGlobMatcher.create(projectDefinition);
  }

  @Override
  public void update(ProjectProtoUpdate update) throws BuildException {
    for (TargetBuildInfo target : builtTargetsSupplier.get()) {
      if (target.javaInfo().isEmpty()) {
        continue;
      }
      JavaArtifactInfo javaInfo = target.javaInfo().get();
      if (!projectDefinition.isIncluded(javaInfo.label())) {
        continue;
      }
      for (BuildArtifact genSrc : javaInfo.genSrcs()) {
        if (JAVA_ARCHIVE_EXTENSIONS.contains(genSrc.getExtension())) {
          // a zip of generated sources
          ProjectPath added =
              update
                  .artifactDirectory(Path.of("buildout"))
                  .addIfNewer(genSrc.path(), genSrc, target.buildContext())
                  .orElse(null);
          if (added != null) {
            ProjectProto.ContentEntry.Builder genSrcJarContentEntry =
                ProjectProto.ContentEntry.newBuilder().setRoot(added.toProto());
            for (JarPath innerPath :
                srcJarInnerPathFinder.findInnerJarPaths(genSrc.blockingGetFrom(buildCache))) {

              genSrcJarContentEntry.addSources(
                  ProjectProto.SourceFolder.newBuilder()
                      .setProjectPath(added.withInnerJarPath(innerPath.path).toProto())
                      .setIsGenerated(true)
                      .setIsTest(testSourceMatcher.matches(genSrc.target().getPackage()))
                      .setPackagePrefix(innerPath.packagePrefix)
                      .build());
            }
            update.workspaceModule().addContentEntries(genSrcJarContentEntry.build());
          }
        }
      }
    }
  }
}
