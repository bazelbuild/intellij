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

import static com.google.common.truth.Truth.assertThat;
import static com.google.idea.blaze.qsync.QuerySyncTestUtils.createSrcJar;

import com.google.common.collect.ImmutableSet;
import com.google.common.truth.Truth8;
import com.google.idea.blaze.qsync.QuerySyncTestUtils.PathPackage;
import com.google.idea.blaze.qsync.java.GeneratedSourceProjectUpdater.GeneratedSourceJar;
import com.google.idea.blaze.qsync.project.ProjectPath;
import com.google.idea.blaze.qsync.project.ProjectPath.Resolver;
import com.google.idea.blaze.qsync.project.ProjectProto;
import com.google.idea.blaze.qsync.project.ProjectProto.ProjectPath.Base;
import com.google.idea.blaze.qsync.project.ProjectProto.SourceFolder;
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
public class GeneratedSourceProjectUpdaterTest {

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  private Path projectRoot;
  private Path workspaceRoot;

  @Before
  public void createProjectDir() throws IOException {
    projectRoot = temporaryFolder.newFolder("project").toPath();
    workspaceRoot = temporaryFolder.newFolder("workspace").toPath();
  }

  @Test
  public void noGeneratedSources_returnsSameProjectProto() throws Exception {
    ProjectProto.Project project =
        ProjectProtos.forTestProject(TestData.JAVA_LIBRARY_NO_DEPS_QUERY);

    GeneratedSourceProjectUpdater updater =
        new GeneratedSourceProjectUpdater(
            project, ImmutableSet.of(), ImmutableSet.of(), Resolver.EMPTY_FOR_TESTING);

    ProjectProto.Project newProject = updater.addGenSrcContentEntry();
    assertThat(newProject).isSameInstanceAs(project);
  }

  @Test
  public void generatedSourceRoots_addedAsContentEntry() throws Exception {
    ProjectProto.Project project =
        ProjectProtos.forTestProject(TestData.JAVA_LIBRARY_NO_DEPS_QUERY);

    ProjectPath sourceRoot = ProjectPath.projectRelative(projectRoot.resolve("generated/java"));

    GeneratedSourceProjectUpdater updater =
        new GeneratedSourceProjectUpdater(
            project, ImmutableSet.of(sourceRoot), ImmutableSet.of(), Resolver.EMPTY_FOR_TESTING);

    ProjectProto.Project newProject = updater.addGenSrcContentEntry();
    assertThat(newProject.getModules(0).getContentEntriesCount()).isEqualTo(2);
    assertThat(newProject.getModules(0).getContentEntries(1))
        .isEqualTo(
            ProjectProto.ContentEntry.newBuilder()
                .setRoot(
                    ProjectProto.ProjectPath.newBuilder()
                        .setBase(Base.PROJECT)
                        .setPath(sourceRoot.relativePath().toString()))
                .addSources(
                    SourceFolder.newBuilder()
                        .setProjectPath(sourceRoot.toProto())
                        .setIsGenerated(true))
                .build());
  }

  @Test
  public void addGenSrcContentEntry_withGenSrcJar_addsContentEntry() throws Exception {
    ProjectProto.Project project =
        ProjectProtos.forTestProject(TestData.JAVA_LIBRARY_NO_DEPS_QUERY);

    ProjectPath srcJarPath = ProjectPath.projectRelative("generated/srcjars/sources.srcjar");
    createSrcJar(
        projectRoot.resolve(srcJarPath.relativePath()),
        PathPackage.of("com/package/Class.java", "com.package"));

    GeneratedSourceProjectUpdater updater =
        new GeneratedSourceProjectUpdater(
            project,
            ImmutableSet.of(),
            ImmutableSet.of(GeneratedSourceJar.create(srcJarPath, false)),
            Resolver.create(workspaceRoot, projectRoot));

    ProjectProto.Project newProject = updater.addGenSrcContentEntry();
    assertThat(newProject.getModules(0).getContentEntriesCount()).isEqualTo(2);
    ProjectProto.ContentEntry contentEntry = newProject.getModules(0).getContentEntries(1);
    assertThat(contentEntry.getRoot().getBase()).isEqualTo(Base.PROJECT);
    assertThat(contentEntry.getRoot().getPath()).isEqualTo(srcJarPath.relativePath().toString());
    assertThat(contentEntry.getRoot().getInnerPath()).isEmpty();

    assertThat(contentEntry.getSourcesCount()).isEqualTo(1);

    ProjectProto.SourceFolder sourceFolder = contentEntry.getSources(0);
    assertThat(sourceFolder.getProjectPath()).isEqualTo(srcJarPath.toProto());
    assertThat(sourceFolder.getIsGenerated()).isTrue();
    assertThat(sourceFolder.getIsTest()).isFalse();
  }

  @Test
  public void addGenSrcContentEntry_withGenSrcJar_addsContentEntry_testSources() throws Exception {
    ProjectProto.Project project =
        ProjectProtos.forTestProject(TestData.JAVA_LIBRARY_NO_DEPS_QUERY);

    ProjectPath srcJarPath = ProjectPath.projectRelative("generated/srcjars/sources.srcjar");
    createSrcJar(
        projectRoot.resolve(srcJarPath.relativePath()),
        PathPackage.of("com/package/Class.java", "com.package"));

    GeneratedSourceProjectUpdater updater =
        new GeneratedSourceProjectUpdater(
            project,
            ImmutableSet.of(),
            ImmutableSet.of(GeneratedSourceJar.create(srcJarPath, true)),
            Resolver.create(workspaceRoot, projectRoot));

    ProjectProto.Project newProject = updater.addGenSrcContentEntry();

    assertThat(newProject.getModules(0).getContentEntries(1).getSources(0).getIsTest()).isTrue();
  }

  @Test
  public void addGenSrcContentEntry_withGenSrcJarWithNestedRoot_addsContentEntryWithInnerPath()
      throws Exception {
    ProjectProto.Project project =
        ProjectProtos.forTestProject(TestData.JAVA_LIBRARY_NO_DEPS_QUERY);

    ProjectPath srcJarPath = ProjectPath.projectRelative("generated/srcjars/sources.srcjar");
    createSrcJar(
        projectRoot.resolve(srcJarPath.relativePath()),
        PathPackage.of("some/subpath/java/com/package/Class.java", "com.package"));

    GeneratedSourceProjectUpdater updater =
        new GeneratedSourceProjectUpdater(
            project,
            ImmutableSet.of(),
            ImmutableSet.of(GeneratedSourceJar.create(srcJarPath, false)),
            Resolver.create(workspaceRoot, projectRoot));

    ProjectProto.Project newProject = updater.addGenSrcContentEntry();
    assertThat(newProject.getModules(0).getContentEntriesCount()).isEqualTo(2);

    ProjectProto.ContentEntry contentEntry = newProject.getModules(0).getContentEntries(1);
    assertThat(contentEntry.getRoot().getBase()).isEqualTo(Base.PROJECT);
    assertThat(contentEntry.getRoot().getPath()).isEqualTo(srcJarPath.relativePath().toString());
    assertThat(contentEntry.getRoot().getInnerPath()).isEmpty();

    Truth8.assertThat(
            contentEntry.getSourcesList().stream()
                .map(SourceFolder::getProjectPath)
                .map(ProjectPath::create)
                .map(pp -> String.format("%s!/%s", pp.relativePath(), pp.innerJarPath())))
        .containsExactly("generated/srcjars/sources.srcjar!/some/subpath/java");
    assertThat(contentEntry.getSourcesList().stream().allMatch(SourceFolder::getIsGenerated))
        .isTrue();
  }
}
