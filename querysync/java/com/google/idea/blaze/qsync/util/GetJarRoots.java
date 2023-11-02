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
package com.google.idea.blaze.qsync.util;

import static java.util.stream.Collectors.joining;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.idea.blaze.qsync.SrcJarProjectUpdater;
import com.google.idea.blaze.qsync.project.ProjectPath;
import com.google.idea.blaze.qsync.project.ProjectPath.Root;
import com.google.idea.blaze.qsync.project.ProjectProto;
import com.google.idea.blaze.qsync.project.ProjectProto.LibrarySource;
import java.io.File;
import java.nio.file.Path;

/**
 * Simple CLI tool to run the logic inside {@link SrcJarProjectUpdater#findInnerJarPaths(File)} on a
 * jar file provided as a CLI parameter.
 *
 * <p>Example usage:
 *
 * <pre>
 *   blaze run //querysync/java/com/google/idea/blaze/qsync/util:get_jar_roots -- $(pwd)/tools/build_defs/kotlin/release/rules/kotlin-stdlib-sources.jar
 * </pre>
 */
public class GetJarRoots {

  public static void main(String[] args) {
    System.exit(new GetJarRoots(Path.of(args[0])).run());
  }

  private final Path jarFile;

  GetJarRoots(Path jarFile) {
    this.jarFile = jarFile;
  }

  int run() {
    ProjectProto.Project project =
        ProjectProto.Project.newBuilder()
            .addLibrary(ProjectProto.Library.newBuilder().setName(".dependencies"))
            .build();
    SrcJarProjectUpdater sjpu =
        new SrcJarProjectUpdater(
            project,
            ImmutableSet.of(ProjectPath.create(Root.WORKSPACE, jarFile.getFileName())),
            ProjectPath.Resolver.create(jarFile.getParent(), jarFile.getParent()));
    project = sjpu.addSrcJars();
    System.out.println(
        Iterables.getOnlyElement(project.getLibraryList()).getSourcesList().stream()
            .map(LibrarySource::getSrcjar)
            .map(ProjectPath::create)
            .map(pp -> String.format("%s!/%s", pp.relativePath(), pp.innerJarPath()))
            .collect(joining("\n")));
    return 0;
  }
}
