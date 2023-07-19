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
import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.qsync.project.ProjectProto;
import com.google.idea.blaze.qsync.project.ProjectProto.LibrarySource;
import com.google.idea.blaze.qsync.testdata.ProjectProtos;
import com.google.idea.blaze.qsync.testdata.TestData;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SrcJarProjectUpdaterTest {

  @Test
  public void no_src_jars() throws Exception {
    ProjectProto.Project project =
        ProjectProtos.forTestProject(TestData.JAVA_LIBRARY_NO_DEPS_QUERY);

    SrcJarProjectUpdater updater = new SrcJarProjectUpdater(project, ImmutableList.of());

    assertThat(updater.addSrcJars()).isSameInstanceAs(project);
  }

  @Test
  public void same_src_jars() throws Exception {
    ProjectProto.Project project =
        ProjectProtos.forTestProject(TestData.JAVA_LIBRARY_NO_DEPS_QUERY);
    // sanity check:
    assertThat(project.getLibrary(0).getName()).isEqualTo(".dependencies");
    // add some existing srcjars:
    project =
        project.toBuilder()
            .setLibrary(
                0,
                project.getLibrary(0).toBuilder()
                    .addSources(
                        LibrarySource.newBuilder().setSrcjarPath("path/to/sources1.srcjar").build())
                    .addSources(
                        LibrarySource.newBuilder().setSrcjarPath("path/to/sources2.srcjar").build())
                    .build())
            .build();

    SrcJarProjectUpdater updater =
        new SrcJarProjectUpdater(
            project,
            ImmutableList.of(
                Path.of("path/to/sources1.srcjar"), Path.of("path/to/sources2.srcjar")));

    assertThat(updater.addSrcJars()).isSameInstanceAs(project);
  }

  @Test
  public void remove_src_jars() throws Exception {
    ProjectProto.Project project =
        ProjectProtos.forTestProject(TestData.JAVA_LIBRARY_NO_DEPS_QUERY);
    // sanity check:
    assertThat(project.getLibrary(0).getName()).isEqualTo(".dependencies");
    // add some existing srcjars:
    project =
        project.toBuilder()
            .setLibrary(
                0,
                project.getLibrary(0).toBuilder()
                    .addSources(
                        LibrarySource.newBuilder().setSrcjarPath("path/to/sources1.srcjar").build())
                    .addSources(
                        LibrarySource.newBuilder().setSrcjarPath("path/to/sources2.srcjar").build())
                    .build())
            .build();

    SrcJarProjectUpdater updater =
        new SrcJarProjectUpdater(project, ImmutableList.of(Path.of("path/to/sources1.srcjar")));

    ProjectProto.Project newProject = updater.addSrcJars();
    assertThat(newProject).isNotSameInstanceAs(project);

    assertThat(
            newProject.getLibrary(0).getSourcesList().stream()
                .map(LibrarySource::getSrcjarPath)
                .collect(toImmutableList()))
        .containsExactly("path/to/sources1.srcjar");
  }

  @Test
  public void add_src_jars() throws Exception {
    ProjectProto.Project project =
        ProjectProtos.forTestProject(TestData.JAVA_LIBRARY_NO_DEPS_QUERY);
    // sanity check:
    assertThat(project.getLibrary(0).getName()).isEqualTo(".dependencies");
    // add some existing srcjars:
    project =
        project.toBuilder()
            .setLibrary(
                0,
                project.getLibrary(0).toBuilder()
                    .addSources(
                        LibrarySource.newBuilder().setSrcjarPath("path/to/sources1.srcjar").build())
                    .build())
            .build();

    SrcJarProjectUpdater updater =
        new SrcJarProjectUpdater(
            project,
            ImmutableList.of(
                Path.of("path/to/sources1.srcjar"), Path.of("path/to/sources2.srcjar")));

    ProjectProto.Project newProject = updater.addSrcJars();
    assertThat(newProject).isNotSameInstanceAs(project);

    assertThat(
            newProject.getLibrary(0).getSourcesList().stream()
                .map(LibrarySource::getSrcjarPath)
                .collect(toImmutableList()))
        .containsExactly("path/to/sources1.srcjar", "path/to/sources2.srcjar");
  }
}
