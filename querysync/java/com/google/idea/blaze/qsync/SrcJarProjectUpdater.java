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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.qsync.project.ProjectPath;
import com.google.idea.blaze.qsync.project.ProjectProto;
import com.google.idea.blaze.qsync.project.ProjectProto.Library;
import com.google.idea.blaze.qsync.project.ProjectProto.LibrarySource;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Updates the project proto with the provided source jars. */
public class SrcJarProjectUpdater {

  private final Logger logger = Logger.getLogger(SrcJarProjectUpdater.class.getSimpleName());

  private final ProjectProto.Project project;
  private final Collection<ProjectPath> srcJars;
  private final ProjectPath.Resolver resolver;
  private final PackageStatementParser packageReader;

  public SrcJarProjectUpdater(
      ProjectProto.Project project,
      Collection<ProjectPath> srcJars,
      ProjectPath.Resolver resolver) {
    this.project = project;
    this.srcJars = srcJars;
    this.resolver = resolver;
    packageReader = new PackageStatementParser();
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
      Optional<Path> innerPath = findInnerJarPath(jarFile.toFile());
      newSrcJars.add(innerPath.map(srcJar::withInnerJarPath).orElse(srcJar));
    }
    return newSrcJars.build();
  }

  private Optional<Path> findInnerJarPath(File jarFile) {
    try {
      ZipFile zip = new ZipFile(jarFile);
      Enumeration<? extends ZipEntry> entries = zip.entries();
      while (entries.hasMoreElements()) {
        ZipEntry e = entries.nextElement();
        if (!e.isDirectory()) {
          if (e.getName().endsWith(".java") || e.getName().endsWith(".kt")) {
            try (InputStream in = zip.getInputStream(e)) {
              String pname = packageReader.readPackage(in);
              Path packageAsPath = Path.of(pname.replace('.', '/'));
              Path zipPath = Path.of(e.getName()).getParent();
              if (zipPath == null) {
                zipPath = Path.of("");
              }
              if (zipPath.equals(packageAsPath)) {
                // package root is the jar file root.
                return Optional.empty();
              }
              if (zipPath.endsWith(packageAsPath)) {
                return Optional.of(
                    zipPath.subpath(0, zipPath.getNameCount() - packageAsPath.getNameCount()));
              }
            }
          }
        }
      }
    } catch (IOException ioe) {
      logger.log(Level.WARNING, "Failed to examine " + jarFile, ioe);
    }
    return Optional.empty();
  }
}
