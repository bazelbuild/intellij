/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.scala.sync;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.JavaIdeInfo;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.ideinfo.TargetMapBuilder;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.sync.BlazeSyncIntegrationTestCase;
import com.google.idea.blaze.base.sync.BlazeSyncParams;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.java.sync.model.BlazeContentEntry;
import com.google.idea.blaze.java.sync.model.BlazeJavaSyncData;
import com.google.idea.blaze.java.sync.model.BlazeSourceDirectory;

import java.util.List;

import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryProperties;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.libraries.PersistentLibraryKind;
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel;
import org.jetbrains.plugins.scala.project.ScalaLibraryProperties;
import org.jetbrains.plugins.scala.project.ScalaLibraryType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Scala-specific sync integration tests. */
@RunWith(JUnit4.class)
public class ScalaSyncTest extends BlazeSyncIntegrationTestCase {
  @Test
  public void testScalaClassesPresentInClassPath() throws Exception {
    setProjectView(
        "directories:",
        "  src/main/scala/com/google",
        "targets:",
        "  //src/main/scala/com/google:lib",
        "additional_languages:",
        "  scala");

    workspace.createFile(
        new WorkspacePath("src/main/scala/com/google/ClassWithUniqueName1.scala"),
        "package com.google;",
        "public class ClassWithUniqueName1 {}");

    workspace.createFile(
        new WorkspacePath("src/main/scala/com/google/ClassWithUniqueName2.scala"),
        "package com.google;",
        "public class ClassWithUniqueName2 {}");

    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(sourceRoot("src/main/scala/com/google/BUILD"))
                    .setLabel("//src/main/scala/com/google:lib")
                    .setKind("scala_library")
                    .addSource(sourceRoot("src/main/scala/com/google/ClassWithUniqueName1.scala"))
                    .addSource(sourceRoot("src/main/scala/com/google/ClassWithUniqueName2.scala"))
                    .setJavaInfo(JavaIdeInfo.builder()))
            .build();

    setTargetMap(targetMap);

    BlazeSyncParams syncParams =
        BlazeSyncParams.builder()
            .setTitle("Full Sync")
            .setSyncMode(SyncMode.FULL)
            .setSyncOrigin("test")
            .setAddProjectViewTargets(true)
            .build();
    runBlazeSync(syncParams);

    errorCollector.assertNoIssues();

    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(getProject()).getBlazeProjectData();
    assertThat(blazeProjectData).isNotNull();
    assertThat(blazeProjectData.getTargetMap()).isEqualTo(targetMap);
    assertThat(blazeProjectData.getWorkspaceLanguageSettings())
        .isEqualTo(
            new WorkspaceLanguageSettings(
                WorkspaceType.JAVA,
                ImmutableSet.of(LanguageClass.GENERIC, LanguageClass.JAVA, LanguageClass.SCALA)));

    BlazeJavaSyncData javaSyncData = blazeProjectData.getSyncState().get(BlazeJavaSyncData.class);
    assertThat(javaSyncData).isNotNull();
    List<BlazeContentEntry> contentEntries = javaSyncData.getImportResult().contentEntries;
    assertThat(contentEntries).hasSize(1);

    BlazeContentEntry contentEntry = contentEntries.get(0);
    assertThat(contentEntry.contentRoot.getPath())
        .isEqualTo(
            workspaceRoot.fileForPath(new WorkspacePath("src/main/scala/com/google")).getPath());
    assertThat(contentEntry.sources).hasSize(1);

    BlazeSourceDirectory sourceDir = contentEntry.sources.get(0);
    assertThat(sourceDir.getPackagePrefix()).isEqualTo("com.google");
    assertThat(sourceDir.getDirectory().getPath())
        .isEqualTo(
            workspaceRoot.fileForPath(new WorkspacePath("src/main/scala/com/google")).getPath());
  }

  @Test
  public void testSimpleSync() throws Exception {
    setProjectView(
        "directories:",
        "  src/main/scala/com/google",
        "targets:",
        "  //src/main/scala/com/google:lib",
        "additional_languages:",
        "  scala");

    workspace.createFile(
        new WorkspacePath("src/main/scala/com/google/Source.scala"),
        "package com.google;",
        "public class Source {}");

    workspace.createFile(
        new WorkspacePath("src/main/scala/com/google/Other.scala"),
        "package com.google;",
        "public class Other {}");

    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(sourceRoot("src/main/scala/com/google/BUILD"))
                    .setLabel("//src/main/scala/com/google:lib")
                    .setKind("scala_library")
                    .addSource(sourceRoot("src/main/scala/com/google/Source.scala"))
                    .addSource(sourceRoot("src/main/scala/com/google/Other.scala")))
            .build();

    setTargetMap(targetMap);

    runBlazeSync(
        BlazeSyncParams.builder()
            .setTitle("Sync")
            .setSyncMode(SyncMode.INCREMENTAL)
            .setSyncOrigin("test")
            .setAddProjectViewTargets(true)
            .build());

    errorCollector.assertNoIssues();

    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(getProject()).getBlazeProjectData();
    assertThat(blazeProjectData).isNotNull();
    assertThat(blazeProjectData.getTargetMap()).isEqualTo(targetMap);
    assertThat(blazeProjectData.getWorkspaceLanguageSettings())
        .isEqualTo(
            new WorkspaceLanguageSettings(
                WorkspaceType.JAVA,
                ImmutableSet.of(LanguageClass.GENERIC, LanguageClass.SCALA, LanguageClass.JAVA)));
  }

  @Test
  public void testSyncScalaLibraryVersion() throws Exception {
    setProjectView(
        "directories:",
        "  src/main/scala/com/google",
        "targets:",
        "  //src/main/scala/com/google:lib",
        "additional_languages:",
        "  scala");

    workspace.createFile(
        new WorkspacePath("src/main/scala/com/google/Source.scala"),
        "package com.google;",
        "public class Source {}");

    workspace.createFile(new WorkspacePath("lib/scala-library-2.13.8.jar"),
        "content");

    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(sourceRoot("src/main/scala/com/google/BUILD"))
                    .setLabel("//src/main/scala/com/google:lib")
                    .setKind("scala_library")
                    .setJavaInfo(new JavaIdeInfo.Builder())
                    .addDependency("//lib/scala-library:jar")
                    .addSource(sourceRoot("src/main/scala/com/google/Source.scala")))
            .addTarget(TargetIdeInfo.builder()
                .setLabel("//lib/scala-library:jar")
                .setKind("scala_import")
                .setJavaInfo(new JavaIdeInfo.Builder()
                    .addJar(new LibraryArtifact.Builder()
                        .setClassJar(new ArtifactLocation.Builder().
                            setRelativePath("lib/scala-library-2.13.8.jar")
                            .build())))
            )
            .build();

    setTargetMap(targetMap);

    runBlazeSync(
        BlazeSyncParams.builder()
            .setTitle("Sync")
            .setSyncMode(SyncMode.INCREMENTAL)
            .setSyncOrigin("test")
            .setAddProjectViewTargets(true)
            .build());

    errorCollector.assertNoIssues();

    Library[] libraries = LibraryTablesRegistrar.getInstance().getLibraryTable(getProject()).getLibraries();
    assertThat(libraries.length).isEqualTo(1);
    LibraryEx library = (LibraryEx)libraries[0];
    assertThat(library).isNotNull();
    PersistentLibraryKind<?> scalaLibraryKind = ScalaLibraryType.apply().getKind();
    assertThat(library.getKind()).isEqualTo(scalaLibraryKind);
    LibraryProperties<?> properties = library.getProperties();
    assertThat(properties).isNotNull();
    assertThat(properties).isInstanceOf(ScalaLibraryProperties.class);
    ScalaLibraryProperties scalaProperties = (ScalaLibraryProperties) properties;
    assertThat(scalaProperties.languageLevel()).isEqualTo(ScalaLanguageLevel.Scala_2_13);
  }
}
