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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.idea.blaze.qsync.java.SrcJarInnerPathFinder.AllowPackagePrefixes.EMPTY_PACKAGE_PREFIXES_ONLY;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.qsync.project.ProjectPath;
import com.google.idea.blaze.qsync.project.ProjectProto;
import com.google.idea.blaze.qsync.project.ProjectProto.Library;
import com.google.idea.blaze.qsync.project.ProjectProto.LibrarySource;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

/** Updates the project proto with the provided source jars. */
public class SrcJarProjectUpdater {

  private final ProjectProto.Project project;
  private final Collection<ProjectPath> srcJars;
  private final ProjectPath.Resolver resolver;
  private final SrcJarInnerPathFinder srcJarInnerPathFinder;

  public SrcJarProjectUpdater(
      ProjectProto.Project project,
      Collection<ProjectPath> srcJars,
      ProjectPath.Resolver resolver) {
    this.project = project;
    this.srcJars = srcJars;
    this.resolver = resolver;
    // Require empty package prefixes for srcjar inner paths, since the ultimate consumer of these
    // paths does not support setting a package prefix (see `Library.ModifiableModel.addRoot`).
    srcJarInnerPathFinder =
        new SrcJarInnerPathFinder(new PackageStatementParser(), EMPTY_PACKAGE_PREFIXES_ONLY);
  }

  private int findDepsLib(List<Library> libs) {
    for (int i = 0; i < libs.size(); ++i) {
      if (libs.get(i).getName().equals(".dependencies")) {
        return i;
      }
    }
    return -1;
  }

  public ProjectProto.Project addSrcJars() {

    int depLibPos = findDepsLib(project.getLibraryList());
    if (depLibPos < 0) {
      return project;
    }

    ImmutableList<ProjectPath> srcJars = resolveSrcJarInnerPaths(this.srcJars);

    ImmutableSet<ProjectPath> existingSrcjars =
        project.getLibrary(depLibPos).getSourcesList().stream()
            .filter(LibrarySource::hasSrcjar)
            .map(LibrarySource::getSrcjar)
            .map(ProjectPath::create)
            .collect(toImmutableSet());

    if (existingSrcjars.equals(ImmutableSet.copyOf(srcJars))) {
      // no changes to make.
      return project;
    }

    // If we ever support anything other than source jars, we cannot just clearSources() below:
    return project.toBuilder()
        .setLibrary(
            depLibPos,
            project.getLibrary(depLibPos).toBuilder()
                .clearSources()
                .addAllSources(
                    srcJars.stream()
                        .map(ProjectPath::toProto)
                        .map(srcJar -> LibrarySource.newBuilder().setSrcjar(srcJar).build())
                        .collect(toImmutableList()))
                .build())
        .build();
  }

  /**
   * Finds the java source roots within jar files.
   *
   * <p>For each of {@code srcJars}, sets the {@link ProjectPath#innerJarPath()} to the java source
   * root within that jar file, if necessary.
   */
  private ImmutableList<ProjectPath> resolveSrcJarInnerPaths(Collection<ProjectPath> srcJars) {
    ImmutableList.Builder<ProjectPath> newSrcJars = ImmutableList.builder();
    for (ProjectPath srcJar : srcJars) {
      Path jarFile = resolver.resolve(srcJar);
      srcJarInnerPathFinder.findInnerJarPaths(jarFile.toFile()).stream()
          .map(p -> p.path)
          .map(srcJar::withInnerJarPath)
          .forEach(newSrcJars::add);
    }
    return newSrcJars.build();
  }
}
