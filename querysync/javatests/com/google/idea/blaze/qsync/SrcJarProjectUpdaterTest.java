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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.idea.blaze.qsync.QuerySyncTestUtils.createSrcJar;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.qsync.QuerySyncTestUtils.PathPackage;
import com.google.idea.blaze.qsync.project.ProjectPath;
import com.google.idea.blaze.qsync.project.ProjectPath.Resolver;
import com.google.idea.blaze.qsync.project.ProjectProto;
import com.google.idea.blaze.qsync.project.ProjectProto.LibrarySource;
import com.google.idea.blaze.qsync.project.ProjectProto.ProjectPath.Base;
import com.google.idea.blaze.qsync.testdata.ProjectProtos;
import com.google.idea.blaze.qsync.testdata.TestData;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SrcJarProjectUpdaterTest {

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  private Path workspaceRoot;
  private Path projectRoot;

  @Before
  public void createWorkspaceAndProjectDir() throws IOException {
    workspaceRoot = temporaryFolder.newFolder("workspace").toPath();
    projectRoot = temporaryFolder.newFolder("project").toPath();
  }

  @Test
  public void no_src_jars() throws Exception {
    ProjectProto.Project project =
        ProjectProtos.forTestProject(TestData.JAVA_LIBRARY_NO_DEPS_QUERY);

    SrcJarProjectUpdater updater =
        new SrcJarProjectUpdater(project, ImmutableList.of(), Resolver.EMPTY_FOR_TESTING);

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
                        LibrarySource.newBuilder()
                            .setSrcjar(
                                ProjectProto.ProjectPath.newBuilder()
                                    .setBase(Base.WORKSPACE)
                                    .setPath("path/to/sources1.srcjar"))
                            .build())
                    .addSources(
                        LibrarySource.newBuilder()
                            .setSrcjar(
                                ProjectProto.ProjectPath.newBuilder()
                                    .setBase(Base.WORKSPACE)
                                    .setPath("path/to/sources2.srcjar"))
                            .build())
                    .build())
            .build();

    SrcJarProjectUpdater updater =
        new SrcJarProjectUpdater(
            project,
            ImmutableList.of(
                ProjectPath.workspaceRelative("path/to/sources1.srcjar"),
                ProjectPath.workspaceRelative("path/to/sources2.srcjar")),
            Resolver.EMPTY_FOR_TESTING);

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
                        LibrarySource.newBuilder()
                            .setSrcjar(
                                ProjectProto.ProjectPath.newBuilder()
                                    .setBase(Base.WORKSPACE)
                                    .setPath("path/to/sources1.srcjar"))
                            .build())
                    .addSources(
                        LibrarySource.newBuilder()
                            .setSrcjar(
                                ProjectProto.ProjectPath.newBuilder()
                                    .setBase(Base.WORKSPACE)
                                    .setPath("path/to/sources2.srcjar"))
                            .build())
                    .build())
            .build();

    SrcJarProjectUpdater updater =
        new SrcJarProjectUpdater(
            project,
            ImmutableList.of(ProjectPath.workspaceRelative("path/to/sources1.srcjar")),
            Resolver.EMPTY_FOR_TESTING);

    ProjectProto.Project newProject = updater.addSrcJars();
    assertThat(newProject).isNotSameInstanceAs(project);

    assertThat(
            newProject.getLibrary(0).getSourcesList().stream()
                .map(LibrarySource::getSrcjar)
                .map(ProjectProto.ProjectPath::getPath))
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
                        LibrarySource.newBuilder()
                            .setSrcjar(
                                ProjectProto.ProjectPath.newBuilder()
                                    .setBase(Base.WORKSPACE)
                                    .setPath("path/to/sources1.srcjar"))
                            .build())
                    .build())
            .build();

    SrcJarProjectUpdater updater =
        new SrcJarProjectUpdater(
            project,
            ImmutableList.of(
                ProjectPath.workspaceRelative("path/to/sources1.srcjar"),
                ProjectPath.workspaceRelative("path/to/sources2.srcjar")),
            Resolver.EMPTY_FOR_TESTING);

    ProjectProto.Project newProject = updater.addSrcJars();
    assertThat(newProject).isNotSameInstanceAs(project);

    assertThat(
            newProject.getLibrary(0).getSourcesList().stream()
                .map(LibrarySource::getSrcjar)
                .map(ProjectProto.ProjectPath::getPath))
        .containsExactly("path/to/sources1.srcjar", "path/to/sources2.srcjar");
  }

  @Test
  public void src_jars_roots() throws Exception {
    ProjectProto.Project project =
        ProjectProtos.forTestProject(TestData.JAVA_LIBRARY_NO_DEPS_QUERY);
    // sanity check:
    assertThat(project.getLibrary(0).getName()).isEqualTo(".dependencies");

    ProjectPath.Resolver resolver = Resolver.create(Path.of("/workspace"), Path.of("/project"));

    SrcJarProjectUpdater updater =
        new SrcJarProjectUpdater(
            project,
            ImmutableList.of(
                ProjectPath.workspaceRelative("path/to/sources1.srcjar"),
                ProjectPath.projectRelative("path/to/sources2.srcjar")),
            resolver);

    ProjectProto.Project newProject = updater.addSrcJars();
    assertThat(newProject).isNotSameInstanceAs(project);

    assertThat(
            newProject.getLibrary(0).getSourcesList().stream()
                .map(LibrarySource::getSrcjar)
                .map(ProjectPath::create)
                .map(resolver::resolve)
                .map(Path::toString))
        .containsExactly("/workspace/path/to/sources1.srcjar", "/project/path/to/sources2.srcjar");
  }

  @Test
  public void src_jars_inner_path_root() throws Exception {
    ProjectProto.Project project =
        ProjectProtos.forTestProject(TestData.JAVA_LIBRARY_NO_DEPS_QUERY);
    // sanity check:
    assertThat(project.getLibrary(0).getName()).isEqualTo(".dependencies");

    createSrcJar(
        workspaceRoot.resolve("path/to/sources.srcjar"),
        PathPackage.of("com/package/Class.java", "com.package"));

    SrcJarProjectUpdater updater =
        new SrcJarProjectUpdater(
            project,
            ImmutableList.of(ProjectPath.workspaceRelative("path/to/sources.srcjar")),
            Resolver.create(workspaceRoot, projectRoot));

    ProjectProto.Project newProject = updater.addSrcJars();

    assertThat(
            newProject.getLibrary(0).getSourcesList().stream()
                .map(LibrarySource::getSrcjar)
                .map(ProjectPath::create)
                .map(pp -> String.format("%s!/%s", pp.relativePath(), pp.innerJarPath())))
        .containsExactly("path/to/sources.srcjar!/");
  }

  @Test
  public void src_jars_inner_path_not_root() throws Exception {
    ProjectProto.Project project =
        ProjectProtos.forTestProject(TestData.JAVA_LIBRARY_NO_DEPS_QUERY);
    // sanity check:
    assertThat(project.getLibrary(0).getName()).isEqualTo(".dependencies");

    createSrcJar(
        workspaceRoot.resolve("path/to/sources.srcjar"),
        PathPackage.of("java/package/root/com/package/Class.java", "com.package"));

    SrcJarProjectUpdater updater =
        new SrcJarProjectUpdater(
            project,
            ImmutableList.of(ProjectPath.workspaceRelative("path/to/sources.srcjar")),
            Resolver.create(workspaceRoot, projectRoot));

    ProjectProto.Project newProject = updater.addSrcJars();

    assertThat(
            newProject.getLibrary(0).getSourcesList().stream()
                .map(LibrarySource::getSrcjar)
                .map(ProjectPath::create)
                .map(pp -> String.format("%s!/%s", pp.relativePath(), pp.innerJarPath())))
        .containsExactly("path/to/sources.srcjar!/java/package/root");
  }

  @Test
  public void src_jars_inner_path_root_default_package() throws Exception {
    ProjectProto.Project project =
        ProjectProtos.forTestProject(TestData.JAVA_LIBRARY_NO_DEPS_QUERY);
    // sanity check:
    assertThat(project.getLibrary(0).getName()).isEqualTo(".dependencies");

    createSrcJar(workspaceRoot.resolve("path/to/sources.srcjar"), PathPackage.of("Class.java", ""));

    SrcJarProjectUpdater updater =
        new SrcJarProjectUpdater(
            project,
            ImmutableList.of(ProjectPath.workspaceRelative("path/to/sources.srcjar")),
            Resolver.create(workspaceRoot, projectRoot));

    ProjectProto.Project newProject = updater.addSrcJars();

    assertThat(
            newProject.getLibrary(0).getSourcesList().stream()
                .map(LibrarySource::getSrcjar)
                .map(ProjectPath::create)
                .map(pp -> String.format("%s!/%s", pp.relativePath(), pp.innerJarPath())))
        .containsExactly("path/to/sources.srcjar!/");
  }

  @Test
  public void src_jars_inner_path_root_non_matching_package() throws Exception {
    ProjectProto.Project project =
        ProjectProtos.forTestProject(TestData.JAVA_LIBRARY_NO_DEPS_QUERY);
    // sanity check:
    assertThat(project.getLibrary(0).getName()).isEqualTo(".dependencies");

    createSrcJar(
        workspaceRoot.resolve("path/to/sources.srcjar"),
        PathPackage.of("Class.java", "com.package"),
        PathPackage.of("java/root/com/package/AnotherClass.java", "com.package"));
    // Class.java should be ignored since its path does not match its package

    SrcJarProjectUpdater updater =
        new SrcJarProjectUpdater(
            project,
            ImmutableList.of(ProjectPath.workspaceRelative("path/to/sources.srcjar")),
            Resolver.create(workspaceRoot, projectRoot));

    ProjectProto.Project newProject = updater.addSrcJars();

    assertThat(
            newProject.getLibrary(0).getSourcesList().stream()
                .map(LibrarySource::getSrcjar)
                .map(ProjectPath::create)
                .map(pp -> String.format("%s!/%s", pp.relativePath(), pp.innerJarPath())))
        .containsExactly("path/to/sources.srcjar!/java/root");
  }

  @Test
  public void src_jars_inner_path_multiple_roots() throws Exception {
    ProjectProto.Project project =
        ProjectProtos.forTestProject(TestData.JAVA_LIBRARY_NO_DEPS_QUERY);
    // sanity check:
    assertThat(project.getLibrary(0).getName()).isEqualTo(".dependencies");

    createSrcJar(
        workspaceRoot.resolve("path/to/sources.srcjar"),
        PathPackage.of("java/root/com/package/Class.java", "com.package"),
        PathPackage.of("com/package/AnotherClass.java", "com.package"),
        PathPackage.of("otherroot/com/package/YetAnotherClass.java", "com.package"));

    SrcJarProjectUpdater updater =
        new SrcJarProjectUpdater(
            project,
            ImmutableList.of(ProjectPath.workspaceRelative("path/to/sources.srcjar")),
            Resolver.create(workspaceRoot, projectRoot));

    ProjectProto.Project newProject = updater.addSrcJars();

    assertThat(
            newProject.getLibrary(0).getSourcesList().stream()
                .map(LibrarySource::getSrcjar)
                .map(ProjectPath::create)
                .map(pp -> String.format("%s!/%s", pp.relativePath(), pp.innerJarPath())))
        .containsExactly(
            "path/to/sources.srcjar!/java/root",
            "path/to/sources.srcjar!/",
            "path/to/sources.srcjar!/otherroot");
  }

  @Test
  public void update_src_jars_new_inner_root() throws Exception {
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
                        LibrarySource.newBuilder()
                            .setSrcjar(
                                ProjectProto.ProjectPath.newBuilder()
                                    .setBase(Base.WORKSPACE)
                                    .setPath("path/to/sources.srcjar"))
                            .build())
                    .build())
            .build();

    SrcJarProjectUpdater updater =
        new SrcJarProjectUpdater(
            project,
            ImmutableList.of(ProjectPath.workspaceRelative("path/to/sources.srcjar")),
            Resolver.create(workspaceRoot, projectRoot));

    createSrcJar(
        workspaceRoot.resolve("path/to/sources.srcjar"),
        PathPackage.of("java/package/root/com/package/Class.java", "com.package"));

    ProjectProto.Project newProject = updater.addSrcJars();

    assertThat(
            newProject.getLibrary(0).getSourcesList().stream()
                .map(LibrarySource::getSrcjar)
                .map(ProjectProto.ProjectPath::getInnerPath))
        .containsExactly("java/package/root");
  }
}
