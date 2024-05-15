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
package com.google.idea.blaze.qsync.java;

import static com.google.idea.blaze.qsync.java.SrcJarInnerPathFinder.AllowPackagePrefixes.ALLOW_NON_EMPTY_PACKAGE_PREFIXES;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.qsync.java.SrcJarInnerPathFinder.JarPath;
import com.google.idea.blaze.qsync.project.ProjectPath;
import com.google.idea.blaze.qsync.project.ProjectProto;
import com.google.idea.blaze.qsync.project.ProjectProto.Project;

/** Updates project protos with a content entry for generated sources */
public class GeneratedSourceProjectUpdater {

  /**
   * Information about a generated source jar, comprising its path and whether or not it's test only
   * source.
   */
  @AutoValue
  public abstract static class GeneratedSourceJar {
    public abstract ProjectPath path();

    public abstract boolean isTestSource();

    public static GeneratedSourceJar create(ProjectPath path, boolean isTestSource) {
      return new AutoValue_GeneratedSourceProjectUpdater_GeneratedSourceJar(path, isTestSource);
    }
  }

  private final Project project;
  private final ImmutableSet<ProjectPath> genSrcRoots;
  private final ImmutableSet<GeneratedSourceJar> genSrcJars;
  private final ProjectPath.Resolver resolver;

  private final SrcJarInnerPathFinder srcJarInnerPathFinder;

  public GeneratedSourceProjectUpdater(
      Project project,
      ImmutableSet<ProjectPath> genSrcFileFolders,
      ImmutableSet<GeneratedSourceJar> genSrcJars,
      ProjectPath.Resolver resolver) {
    this.project = project;
    this.genSrcRoots = genSrcFileFolders;
    this.genSrcJars = genSrcJars;
    this.resolver = resolver;
    srcJarInnerPathFinder = new SrcJarInnerPathFinder(new PackageStatementParser());
  }

  public Project addGenSrcContentEntry() {
    if (genSrcJars.isEmpty() && genSrcRoots.isEmpty()) {
      return project;
    }

    Project.Builder protoBuilder = project.toBuilder();
    ProjectProto.Module.Builder workspaceModule =
        protoBuilder.getModulesBuilderList().stream()
            .filter(m -> m.getName().equals(".workspace"))
            .findFirst()
            .orElseThrow();

    for (ProjectPath path : genSrcRoots) {
      ProjectProto.ProjectPath pathProto = path.toProto();
      ProjectProto.ContentEntry.Builder genSourcesContentEntry =
          ProjectProto.ContentEntry.newBuilder().setRoot(pathProto);
      genSourcesContentEntry.addSources(
          ProjectProto.SourceFolder.newBuilder()
              .setProjectPath(pathProto)
              .setIsGenerated(true)
              .setPackagePrefix(""));
      workspaceModule.addContentEntries(genSourcesContentEntry);
    }

    for (GeneratedSourceJar jar : genSrcJars) {
      ProjectProto.ContentEntry.Builder genSrcJarContentEntry =
          ProjectProto.ContentEntry.newBuilder().setRoot(jar.path().toProto());
      for (JarPath innerPath :
          srcJarInnerPathFinder.findInnerJarPaths(
              resolver.resolve(jar.path()).toFile(), ALLOW_NON_EMPTY_PACKAGE_PREFIXES)) {
        genSrcJarContentEntry.addSources(
            ProjectProto.SourceFolder.newBuilder()
                .setProjectPath(jar.path().withInnerJarPath(innerPath.path).toProto())
                .setIsGenerated(true)
                .setIsTest(jar.isTestSource())
                .setPackagePrefix(innerPath.packagePrefix));
      }
      workspaceModule.addContentEntries(genSrcJarContentEntry);
    }

    return protoBuilder.build();
  }
}
