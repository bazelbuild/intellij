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
package com.google.idea.blaze.scala.sync.importer;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.JavaIdeInfo;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.ideinfo.TargetMapBuilder;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.model.LibraryKey;
import com.google.idea.blaze.base.model.primitives.GenericBlazeRules;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Kind.Provider;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.prefetch.MockPrefetchService;
import com.google.idea.blaze.base.prefetch.PrefetchService;
import com.google.idea.blaze.base.prefetch.RemoteArtifactPrefetcher;
import com.google.idea.blaze.base.projectview.ProjectView;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.ListSection;
import com.google.idea.blaze.base.projectview.section.sections.DirectoryEntry;
import com.google.idea.blaze.base.projectview.section.sections.DirectorySection;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.ErrorCollector;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.sync.MockRemoteArtifactPrefetcher;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.sync.workspace.MockArtifactLocationDecoder;
import com.google.idea.blaze.java.JavaBlazeRules;
import com.google.idea.blaze.java.sync.BlazeJavaSyncAugmenter;
import com.google.idea.blaze.java.sync.importer.BlazeJavaWorkspaceImporter;
import com.google.idea.blaze.java.sync.importer.JavaSourceFilter;
import com.google.idea.blaze.java.sync.importer.emptylibrary.EmptyLibraryFilterSettings;
import com.google.idea.blaze.java.sync.jdeps.JdepsMap;
import com.google.idea.blaze.java.sync.model.BlazeContentEntry;
import com.google.idea.blaze.java.sync.model.BlazeJarLibrary;
import com.google.idea.blaze.java.sync.model.BlazeJavaImportResult;
import com.google.idea.blaze.java.sync.model.BlazeSourceDirectory;
import com.google.idea.blaze.java.sync.source.JavaLikeLanguage;
import com.google.idea.blaze.java.sync.source.JavaSourcePackageReader;
import com.google.idea.blaze.java.sync.source.PackageManifestReader;
import com.google.idea.blaze.java.sync.source.SourceArtifact;
import com.google.idea.blaze.scala.ScalaBlazeRules;
import com.google.idea.blaze.scala.ScalaJavaLikeLanguage;
import com.google.idea.blaze.scala.sync.model.BlazeScalaImportResult;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import java.io.File;
import java.util.Map;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link BlazeScalaWorkspaceImporter} */
@RunWith(JUnit4.class)
public class BlazeScalaWorkspaceImporterTest extends BlazeTestCase {
  private final WorkspaceRoot workspaceRoot = new WorkspaceRoot(new File("/root"));
  private BlazeContext context;
  private final ErrorCollector errorCollector = new ErrorCollector();

  @Override
  @SuppressWarnings("FunctionalInterfaceClash") // False positive on getDeclaredPackageOfJavaFile.
  protected void initTest(
      @NotNull Container applicationServices, @NotNull Container projectServices) {
    super.initTest(applicationServices, projectServices);
    context = BlazeContext.create();
    context.addOutputSink(IssueOutput.class, errorCollector);

    ExtensionPointImpl<Provider> ep =
        registerExtensionPoint(Kind.Provider.EP_NAME, Kind.Provider.class);
    ep.registerExtension(new JavaBlazeRules());
    ep.registerExtension(new ScalaBlazeRules());
    ep.registerExtension(new GenericBlazeRules());
    applicationServices.register(Kind.ApplicationState.class, new Kind.ApplicationState());

    registerExtensionPoint(BlazeJavaSyncAugmenter.EP_NAME, BlazeJavaSyncAugmenter.class);
    registerExtensionPoint(EmptyLibraryFilterSettings.EP_NAME, EmptyLibraryFilterSettings.class);

    BlazeImportSettingsManager importSettingsManager = new BlazeImportSettingsManager(project);
    importSettingsManager.setImportSettings(
        new BlazeImportSettings("", "", "", "", BuildSystemName.Blaze, ProjectType.ASPECT_SYNC));
    projectServices.register(BlazeImportSettingsManager.class, importSettingsManager);

    applicationServices.register(PrefetchService.class, new MockPrefetchService());
    applicationServices.register(PackageManifestReader.class, new PackageManifestReader());
    applicationServices.register(ExperimentService.class, new MockExperimentService());
    applicationServices.register(
        FileOperationProvider.class,
        new FileOperationProvider() {
          @Override
          public long getFileSize(File file) {
            // Make JARs appear nonempty so that they aren't filtered out
            return file.getName().endsWith("jar") ? 500L : super.getFileSize(file);
          }
        });

    // will silently fall back to FilePathJavaPackageReader
    applicationServices.register(
        JavaSourcePackageReader.class,
        new JavaSourcePackageReader() {
          @Nullable
          @Override
          public String getDeclaredPackageOfJavaFile(
              BlazeContext context,
              ArtifactLocationDecoder artifactLocationDecoder,
              SourceArtifact sourceArtifact) {
            return null;
          }
        });
    applicationServices.register(
        RemoteArtifactPrefetcher.class, new MockRemoteArtifactPrefetcher());

    ExtensionPoint<JavaLikeLanguage> javaLikeLanguages =
        registerExtensionPoint(JavaLikeLanguage.EP_NAME, JavaLikeLanguage.class);
    javaLikeLanguages.registerExtension(new JavaLikeLanguage.Java());
    javaLikeLanguages.registerExtension(new ScalaJavaLikeLanguage());
  }

  @Test
  public void testEmptyProject() {
    ProjectView projectView = ProjectView.builder().build();
    TargetMap targetMap = TargetMapBuilder.builder().build();

    BlazeJavaImportResult javaImportResult = importJava(projectView, targetMap);
    BlazeScalaImportResult scalaImportResult = importScala(projectView, targetMap);
    errorCollector.assertNoIssues();

    assertThat(javaImportResult.libraries).isEmpty();
    assertThat(javaImportResult.contentEntries).isEmpty();
    assertThat(javaImportResult.javaSourceFiles).isEmpty();
    assertThat(javaImportResult.libraries).isEmpty();
    assertThat(scalaImportResult.libraries).isEmpty();
  }

  @Test
  public void testSingleScalaBinary() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("src/main/scala/apps/example"))))
            .build();

    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//src/main/scala/apps/example:example")
                    .setBuildFile(source("src/main/scala/apps/example/BUILD"))
                    .setKind("scala_binary")
                    .addSource(source("src/main/scala/apps/example/Main.scala"))
                    .addSource(source("src/main/scala/apps/example/subdir/SubdirHelper.scala"))
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("src/main/scala/apps/example/example.jar"))
                                    .setClassJar(gen("src/main/scala/apps/example/example.jar")))))
            .build();

    BlazeJavaImportResult javaImportResult = importJava(projectView, targetMap);
    BlazeScalaImportResult scalaImportResult = importScala(projectView, targetMap);
    errorCollector.assertNoIssues();

    assertThat(javaImportResult.contentEntries)
        .containsExactly(
            BlazeContentEntry.builder("/root/src/main/scala/apps/example")
                .addSource(
                    BlazeSourceDirectory.builder("/root/src/main/scala/apps/example")
                        .setPackagePrefix("apps.example")
                        .build())
                .build());
    assertThat(javaImportResult.libraries).isEmpty();
    assertThat(javaImportResult.javaSourceFiles)
        .containsExactly(
            source("src/main/scala/apps/example/Main.scala"),
            source("src/main/scala/apps/example/subdir/SubdirHelper.scala"));
    assertThat(scalaImportResult.libraries).isEmpty();
  }

  @Test
  public void testScalaBinaryWithMultipleLibraries() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("src/main/scala/apps/example"))))
            .build();

    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//src/main/scala/apps/example:example")
                    .setBuildFile(source("src/main/scala/apps/example/BUILD"))
                    .setKind("scala_binary")
                    .addSource(source("src/main/scala/apps/example/Main.scala"))
                    .addSource(source("src/main/scala/apps/example/subdir/SubdirHelper.scala"))
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("src/main/scala/apps/example/example.jar"))
                                    .setClassJar(gen("src/main/scala/apps/example/example.jar"))))
                    .addDependency("//src/main/scala/some/library1:library1")
                    .addDependency("//src/main/java/other/library2:library2"))
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//src/main/scala/some/library1:library1")
                    .setBuildFile(source("src/main/scala/some/library1/BUILD"))
                    .setKind("scala_library")
                    .addSource(source("src/main/scala/some/library1/Library.scala"))
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(
                                        gen("src/main/scala/some/library1/library1_ijar.jar"))
                                    .setClassJar(gen("src/main/scala/some/library1/library1.jar"))))
                    .addDependency("//src/main/java/other/import:import"))
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//src/main/java/other/library2:library2")
                    .setBuildFile(source("src/main/java/other/library2/BUILD"))
                    .setKind("java_library")
                    .addSource(source("src/main/java/other/library2/Library.java"))
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(
                                        gen("src/main/java/other/library2/liblibrary2-ijar.jar"))
                                    .setClassJar(
                                        gen("src/main/java/other/library2/liblibrary2.jar")))))
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//src/main/java/other/import:import")
                    .setBuildFile(source("src/main/java/other/import/BUILD"))
                    .setKind("java_import")
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(
                                        gen("src/main/java/other/import/libimport-ijar.jar"))
                                    .setClassJar(gen("src/main/java/other/import/libimport.jar")))))
            .build();

    BlazeJavaImportResult javaImportResult = importJava(projectView, targetMap);
    BlazeScalaImportResult scalaImportResult = importScala(projectView, targetMap);
    errorCollector.assertNoIssues();

    assertThat(javaImportResult.contentEntries)
        .containsExactly(
            BlazeContentEntry.builder("/root/src/main/scala/apps/example")
                .addSource(
                    BlazeSourceDirectory.builder("/root/src/main/scala/apps/example")
                        .setPackagePrefix("apps.example")
                        .build())
                .build());
    // Direct library deps will be double counted.
    assertThat(javaImportResult.libraries).hasSize(2);
    assertThat(hasLibrary(javaImportResult.libraries, "library1")).isTrue();
    assertThat(hasLibrary(javaImportResult.libraries, "library2")).isTrue();
    assertThat(javaImportResult.javaSourceFiles)
        .containsExactly(
            source("src/main/scala/apps/example/Main.scala"),
            source("src/main/scala/apps/example/subdir/SubdirHelper.scala"));
    assertThat(scalaImportResult.libraries).hasSize(3);
    assertThat(hasLibrary(scalaImportResult.libraries, "library1")).isTrue();
    assertThat(hasLibrary(scalaImportResult.libraries, "library2")).isTrue();
    assertThat(hasLibrary(scalaImportResult.libraries, "import")).isTrue();
  }

  @Test
  public void testScalaAndJavaBinary() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("src/main/scala/apps/example")))
                    .add(DirectoryEntry.include(new WorkspacePath("src/main/java/apps/example"))))
            .build();

    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//src/main/scala/apps/example:example")
                    .setBuildFile(source("src/main/scala/apps/example/BUILD"))
                    .setKind("scala_binary")
                    .addSource(source("src/main/scala/apps/example/Main.scala"))
                    .addSource(source("src/main/scala/apps/example/subdir/SubdirHelper.scala"))
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("src/main/scala/apps/example/example.jar"))
                                    .setClassJar(gen("src/main/scala/apps/example/example.jar")))))
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//src/main/java/apps/example:example")
                    .setBuildFile(source("src/main/java/apps/example/BUILD"))
                    .setKind("java_binary")
                    .addSource(source("src/main/java/apps/example/Main.java"))
                    .addSource(source("src/main/java/apps/example/subdir/SubdirHelper.java"))
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("src/main/java/apps/example/example.jar"))
                                    .setClassJar(gen("src/main/java/apps/example/example.jar")))))
            .build();

    BlazeJavaImportResult javaImportResult = importJava(projectView, targetMap);
    BlazeScalaImportResult scalaImportResult = importScala(projectView, targetMap);
    errorCollector.assertNoIssues();

    assertThat(javaImportResult.contentEntries)
        .containsExactly(
            BlazeContentEntry.builder("/root/src/main/scala/apps/example")
                .addSource(
                    BlazeSourceDirectory.builder("/root/src/main/scala/apps/example")
                        .setPackagePrefix("apps.example")
                        .build())
                .build(),
            BlazeContentEntry.builder("/root/src/main/java/apps/example")
                .addSource(
                    BlazeSourceDirectory.builder("/root/src/main/java/apps/example")
                        .setPackagePrefix("apps.example")
                        .build())
                .build());
    assertThat(javaImportResult.libraries).isEmpty();
    assertThat(javaImportResult.javaSourceFiles)
        .containsExactly(
            source("src/main/scala/apps/example/Main.scala"),
            source("src/main/scala/apps/example/subdir/SubdirHelper.scala"),
            source("src/main/java/apps/example/Main.java"),
            source("src/main/java/apps/example/subdir/SubdirHelper.java"));
    assertThat(scalaImportResult.libraries).isEmpty();
  }

  @Test
  public void testTwoScalaBinariesWithSharedLibrary() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("src/main/scala/apps/example")))
                    .add(DirectoryEntry.include(new WorkspacePath("src/main/scala/apps/other"))))
            .build();

    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//src/main/scala/apps/example:example")
                    .setBuildFile(source("src/main/scala/apps/example/BUILD"))
                    .setKind("scala_binary")
                    .addSource(source("src/main/scala/apps/example/Main.scala"))
                    .addSource(source("src/main/scala/apps/example/subdir/SubdirHelper.scala"))
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("src/main/scala/apps/example/example.jar"))
                                    .setClassJar(gen("src/main/scala/apps/example/example.jar"))))
                    .addDependency("//src/main/scala/some/library:library"))
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//src/main/scala/apps/other:other")
                    .setBuildFile(source("src/main/scala/apps/other/BUILD"))
                    .setKind("scala_binary")
                    .addSource(source("src/main/scala/apps/other/Main.scala"))
                    .addSource(source("src/main/scala/apps/other/subdir/SubdirHelper.scala"))
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("src/main/scala/apps/other/other.jar"))
                                    .setClassJar(gen("src/main/scala/apps/other/other.jar"))))
                    .addDependency("//src/main/scala/some/library:library"))
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//src/main/scala/some/library:library")
                    .setBuildFile(source("src/main/scala/some/library/BUILD"))
                    .setKind("scala_library")
                    .addSource(source("src/main/scala/some/library/Library.scala"))
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(
                                        gen("src/main/scala/some/library/library_ijar.jar"))
                                    .setClassJar(gen("src/main/scala/some/library/library.jar")))))
            .build();

    BlazeJavaImportResult javaImportResult = importJava(projectView, targetMap);
    BlazeScalaImportResult scalaImportResult = importScala(projectView, targetMap);
    errorCollector.assertNoIssues();

    assertThat(javaImportResult.contentEntries)
        .containsExactly(
            BlazeContentEntry.builder("/root/src/main/scala/apps/example")
                .addSource(
                    BlazeSourceDirectory.builder("/root/src/main/scala/apps/example")
                        .setPackagePrefix("apps.example")
                        .build())
                .build(),
            BlazeContentEntry.builder("/root/src/main/scala/apps/other")
                .addSource(
                    BlazeSourceDirectory.builder("/root/src/main/scala/apps/other")
                        .setPackagePrefix("apps.other")
                        .build())
                .build());
    // Direct library deps will be double counted.
    assertThat(javaImportResult.libraries).hasSize(1);
    assertThat(javaImportResult.javaSourceFiles)
        .containsExactly(
            source("src/main/scala/apps/example/Main.scala"),
            source("src/main/scala/apps/example/subdir/SubdirHelper.scala"),
            source("src/main/scala/apps/other/Main.scala"),
            source("src/main/scala/apps/other/subdir/SubdirHelper.scala"));
    assertThat(scalaImportResult.libraries).hasSize(1);
  }

  @Test
  public void testSourceRulesNotInLibraries() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("src/main/scala/apps/example")))
                    .add(DirectoryEntry.include(new WorkspacePath("src/main/scala/some/library1")))
                    .add(DirectoryEntry.include(new WorkspacePath("src/main/java/other/library2"))))
            .build();

    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//src/main/scala/apps/example:example")
                    .setBuildFile(source("src/main/scala/apps/example/BUILD"))
                    .setKind("scala_binary")
                    .addSource(source("src/main/scala/apps/example/Main.scala"))
                    .addSource(source("src/main/scala/apps/example/subdir/SubdirHelper.scala"))
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("src/main/scala/apps/example/example.jar"))
                                    .setClassJar(gen("src/main/scala/apps/example/example.jar"))))
                    .addDependency("//src/main/scala/some/library1:library1")
                    .addDependency("//src/main/java/other/library2:library2"))
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//src/main/scala/some/library1:library1")
                    .setBuildFile(source("src/main/scala/some/library1/BUILD"))
                    .setKind("scala_library")
                    .addSource(source("src/main/scala/some/library1/Library.scala"))
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(
                                        gen("src/main/scala/some/library1/library1_ijar.jar"))
                                    .setClassJar(
                                        gen("src/main/scala/some/library1/library1.jar")))))
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//src/main/java/other/library2:library2")
                    .setBuildFile(source("src/main/java/other/library2/BUILD"))
                    .setKind("java_library")
                    .addSource(source("src/main/java/other/library2/Library.java"))
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(
                                        gen("src/main/java/other/library2/libibrary2-ijar.jar"))
                                    .setClassJar(
                                        gen("src/main/java/other/library2/liblibrary2.jar")))))
            .build();

    BlazeJavaImportResult javaImportResult = importJava(projectView, targetMap);
    BlazeScalaImportResult scalaImportResult = importScala(projectView, targetMap);
    errorCollector.assertNoIssues();

    assertThat(javaImportResult.contentEntries)
        .containsExactly(
            BlazeContentEntry.builder("/root/src/main/scala/apps/example")
                .addSource(
                    BlazeSourceDirectory.builder("/root/src/main/scala/apps/example")
                        .setPackagePrefix("apps.example")
                        .build())
                .build(),
            BlazeContentEntry.builder("/root/src/main/scala/some/library1")
                .addSource(
                    BlazeSourceDirectory.builder("/root/src/main/scala/some/library1")
                        .setPackagePrefix("some.library1")
                        .build())
                .build(),
            BlazeContentEntry.builder("/root/src/main/java/other/library2")
                .addSource(
                    BlazeSourceDirectory.builder("/root/src/main/java/other/library2")
                        .setPackagePrefix("other.library2")
                        .build())
                .build());
    assertThat(javaImportResult.libraries).isEmpty();
    assertThat(javaImportResult.javaSourceFiles)
        .containsExactly(
            source("src/main/scala/apps/example/Main.scala"),
            source("src/main/scala/apps/example/subdir/SubdirHelper.scala"),
            source("src/main/scala/some/library1/Library.scala"),
            source("src/main/java/other/library2/Library.java"));
    assertThat(scalaImportResult.libraries).isEmpty();
  }

  @Test
  public void testSourceTargetsWithoutNonGeneratedSourcesAddedToLibraries() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("src/main/scala/apps/example")))
                    .add(DirectoryEntry.include(new WorkspacePath("src/main/scala/some/library"))))
            .build();

    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//src/main/scala/apps/example:example")
                    .setBuildFile(source("src/main/scala/apps/example/BUILD"))
                    .setKind("scala_binary")
                    .addSource(source("src/main/scala/apps/example/Main.scala"))
                    .addSource(source("src/main/scala/apps/example/subdir/SubdirHelper.scala"))
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("src/main/scala/apps/example/example.jar"))
                                    .setClassJar(gen("src/main/scala/apps/example/example.jar"))))
                    .addDependency("//src/main/scala/some/library:library"))
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//src/main/scala/some/library:library")
                    .setBuildFile(source("src/main/scala/some/library/BUILD"))
                    .setKind("scala_library")
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(
                                        gen("src/main/scala/some/library/library_ijar.jar"))
                                    .setClassJar(gen("src/main/scala/some/library/library.jar")))))
            .build();

    BlazeScalaImportResult scalaImportResult = importScala(projectView, targetMap);
    errorCollector.assertNoIssues();

    assertThat(scalaImportResult.libraries).hasSize(1);
    assertThat(hasLibrary(scalaImportResult.libraries, "library")).isTrue();
  }

  @Test
  public void testDuplicateScalaLibraries() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("src/main/scala/apps/example"))))
            .build();

    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//src/main/scala/apps/example:example")
                    .setKind("scala_binary")
                    .addSource(source("src/main/scala/apps/example/Main.scala"))
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("src/main/scala/apps/example/example.jar"))
                                    .setClassJar(gen("src/main/scala/apps/example/example.jar"))))
                    .addDependency("//src/main/scala/imports:import1")
                    .addDependency("//src/main/scala/imports:import2"))
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//src/main/scala/imports:import1")
                    .setKind("scala_import")
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("src/main/scala/imports/import.jar"))
                                    .setClassJar(gen("src/main/scala/imports/import.jar")))))
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//src/main/scala/imports:import2")
                    .setKind("scala_import")
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("src/main/scala/imports/import.jar"))
                                    .setClassJar(gen("src/main/scala/imports/import.jar")))))
            .build();

    BlazeScalaImportResult scalaImportResult = importScala(projectView, targetMap);
    errorCollector.assertNoIssues();

    assertThat(scalaImportResult.libraries).hasSize(1);
    assertThat(hasLibrary(scalaImportResult.libraries, "import")).isTrue();
  }

  private static boolean hasLibrary(
      Map<LibraryKey, BlazeJarLibrary> libraries, String libraryName) {
    return libraries.values().stream()
        .anyMatch(
            library ->
                library
                    .libraryArtifact
                    .jarForIntellijLibrary()
                    .getRelativePath()
                    .endsWith(libraryName + ".jar"));
  }

  private BlazeJavaImportResult importJava(ProjectView projectView, TargetMap targetMap) {
    ProjectViewSet projectViewSet = ProjectViewSet.builder().add(projectView).build();
    WorkspaceLanguageSettings languageSettings =
        new WorkspaceLanguageSettings(
            WorkspaceType.JAVA,
            ImmutableSet.of(LanguageClass.GENERIC, LanguageClass.SCALA, LanguageClass.JAVA));
    JavaSourceFilter sourceFilter =
        new JavaSourceFilter(
            Blaze.getBuildSystemName(project), workspaceRoot, projectViewSet, targetMap);
    JdepsMap jdepsMap = key -> ImmutableList.of();
    ArtifactLocationDecoder decoder = new MockArtifactLocationDecoder();
    return new BlazeJavaWorkspaceImporter(
            project,
            workspaceRoot,
            projectViewSet,
            languageSettings,
            targetMap,
            sourceFilter,
            jdepsMap,
            null,
            decoder,
            null)
        .importWorkspace(context);
  }

  private BlazeScalaImportResult importScala(ProjectView projectView, TargetMap targetMap) {
    ProjectViewSet projectViewSet = ProjectViewSet.builder().add(projectView).build();
    return new BlazeScalaWorkspaceImporter(project, workspaceRoot, projectViewSet, targetMap)
        .importWorkspace();
  }

  private static ArtifactLocation source(String relativePath) {
    return ArtifactLocation.builder().setRelativePath(relativePath).setIsSource(true).build();
  }

  private static ArtifactLocation gen(String relativePath) {
    return ArtifactLocation.builder()
        .setRootExecutionPathFragment("blaze-out/bin")
        .setRelativePath(relativePath)
        .setIsSource(false)
        .build();
  }
}
