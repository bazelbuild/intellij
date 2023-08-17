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

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.qsync.project.ProjectPath;
import com.google.idea.blaze.qsync.project.ProjectProto;
import com.google.idea.blaze.qsync.project.ProjectProto.Library;
import com.google.idea.blaze.qsync.project.ProjectProto.LibrarySource;
import java.util.List;

/** Updates the project proto with the provided source jars. */
public class SrcJarProjectUpdater {

  private final ProjectProto.Project project;
  private final ImmutableCollection<ProjectPath> srcJars;

  public SrcJarProjectUpdater(
      ProjectProto.Project project, ImmutableCollection<ProjectPath> srcJars) {
    this.project = project;
    this.srcJars = srcJars;
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
}
