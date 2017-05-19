/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.sync.importer;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.idea.blaze.android.projectview.GeneratedAndroidResourcesSection;
import com.google.idea.blaze.android.projectview.GenfilesPath;
import com.google.idea.blaze.android.sync.BlazeAndroidJavaSyncAugmenter;
import com.google.idea.blaze.android.sync.model.AndroidResourceModule;
import com.google.idea.blaze.android.sync.model.BlazeAndroidImportResult;
import com.google.idea.blaze.android.sync.model.BlazeResourceLibrary;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.google.idea.blaze.base.async.executor.MockBlazeExecutor;
import com.google.idea.blaze.base.ideinfo.AndroidIdeInfo;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.JavaIdeInfo;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.ideinfo.TargetMapBuilder;
import com.google.idea.blaze.base.io.FileAttributeProvider;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.projectview.ProjectView;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.ListSection;
import com.google.idea.blaze.base.projectview.section.sections.DirectoryEntry;
import com.google.idea.blaze.base.projectview.section.sections.DirectorySection;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.ErrorCollector;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.settings.Blaze.BuildSystem;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.java.sync.model.BlazeJarLibrary;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import java.io.File;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for BlazeAndroidWorkspaceImporter */
@RunWith(JUnit4.class)
public class BlazeAndroidWorkspaceImporterTest extends BlazeTestCase {

  private final WorkspaceRoot workspaceRoot = new WorkspaceRoot(new File("/root"));

  private static final String FAKE_GEN_ROOT_EXECUTION_PATH_FRAGMENT =
      "blaze-out/gcc-4.X.Y-crosstool-v17-hybrid-grtev3-k8-fastbuild/bin";

  private static final ArtifactLocationDecoder FAKE_ARTIFACT_DECODER =
      (ArtifactLocationDecoder)
          artifactLocation -> new File("/", artifactLocation.getRelativePath());

  private static final BlazeImportSettings DUMMY_IMPORT_SETTINGS =
      new BlazeImportSettings("", "", "", "", BuildSystem.Blaze);

  private BlazeContext context;
  private ErrorCollector errorCollector = new ErrorCollector();

  @Override
  protected void initTest(
      @NotNull Container applicationServices, @NotNull Container projectServices) {
    MockExperimentService mockExperimentService = new MockExperimentService();
    applicationServices.register(ExperimentService.class, mockExperimentService);

    BlazeExecutor blazeExecutor = new MockBlazeExecutor();
    applicationServices.register(BlazeExecutor.class, blazeExecutor);

    projectServices.register(BlazeImportSettingsManager.class, new BlazeImportSettingsManager());
    BlazeImportSettingsManager.getInstance(getProject()).setImportSettings(DUMMY_IMPORT_SETTINGS);

    MockFileAttributeProvider mockFileAttributeProvider = new MockFileAttributeProvider();
    applicationServices.register(FileAttributeProvider.class, mockFileAttributeProvider);

    context = new BlazeContext();
    context.addOutputSink(IssueOutput.class, errorCollector);
  }

  BlazeAndroidImportResult importWorkspace(
      WorkspaceRoot workspaceRoot, TargetMapBuilder targetMapBuilder, ProjectView projectView) {

    ProjectViewSet projectViewSet = ProjectViewSet.builder().add(projectView).build();

    BlazeAndroidWorkspaceImporter workspaceImporter =
        new BlazeAndroidWorkspaceImporter(
            project,
            context,
            workspaceRoot,
            projectViewSet,
            targetMapBuilder.build(),
            FAKE_ARTIFACT_DECODER);

    return workspaceImporter.importWorkspace();
  }

  /** Test that a two packages use the same un-imported android_library */
  @Test
  public void testResourceInheritance() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("java/apps/example")))
                    .add(DirectoryEntry.include(new WorkspacePath("javatests/apps/example"))))
            .build();

    /** Deps are project -> lib0 -> lib1 -> shared project -> shared */
    TargetMapBuilder targetMapBuilder =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//java/apps/example/lib0:lib0")
                    .setKind("android_library")
                    .setBuildFile(source("java/apps/example/lib0/BUILD"))
                    .addSource(source("java/apps/example/lib0/SharedActivity.java"))
                    .setAndroidInfo(
                        AndroidIdeInfo.builder()
                            .setManifestFile(source("java/apps/example/lib0/AndroidManifest.xml"))
                            .addResource(source("java/apps/example/lib0/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.android.apps.example.lib0"))
                    .addDependency("//java/apps/example/lib1:lib1")
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("java/apps/example/lib0/lib0.jar"))
                                    .setClassJar(gen("java/apps/example/lib0/lib0.jar")))))
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//java/apps/example/lib1:lib1")
                    .setKind("android_library")
                    .setBuildFile(source("java/apps/example/lib1/BUILD"))
                    .addSource(source("java/apps/example/lib1/SharedActivity.java"))
                    .setAndroidInfo(
                        AndroidIdeInfo.builder()
                            .setManifestFile(source("java/apps/example/lib1/AndroidManifest.xml"))
                            .addResource(source("java/apps/example/lib1/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.android.apps.example.lib1"))
                    .addDependency("//java/libraries/shared:shared")
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("java/apps/example/lib1/lib1.jar"))
                                    .setClassJar(gen("java/apps/example/lib1/lib1.jar")))))
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//java/apps/example:example_debug")
                    .setKind("android_binary")
                    .setBuildFile(source("java/apps/example/BUILD"))
                    .addSource(source("java/apps/example/MainActivity.java"))
                    .setAndroidInfo(
                        AndroidIdeInfo.builder()
                            .setManifestFile(source("java/apps/example/AndroidManifest.xml"))
                            .addResource(source("java/apps/example/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.android.apps.example"))
                    .addDependency("//java/apps/example/lib0:lib0")
                    .addDependency("//java/libraries/shared:shared")
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("java/apps/example/example_debug.jar"))
                                    .setClassJar(gen("java/apps/example/example_debug.jar")))))
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//java/libraries/shared:shared")
                    .setBuildFile(source("java/libraries/shared/BUILD"))
                    .setKind("android_library")
                    .addSource(source("java/libraries/shared/SharedActivity.java"))
                    .setAndroidInfo(
                        AndroidIdeInfo.builder()
                            .setManifestFile(source("java/libraries/shared/AndroidManifest.xml"))
                            .addResource(source("java/libraries/shared/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.android.libraries.shared"))
                    .setBuildFile(source("java/libraries/shared/BUILD"))
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("java/libraries/shared.jar"))
                                    .setClassJar(gen("java/libraries/shared.jar")))));

    BlazeAndroidImportResult result = importWorkspace(workspaceRoot, targetMapBuilder, projectView);
    errorCollector.assertNoIssues();

    assertThat(result.androidResourceModules)
        .containsExactly(
            AndroidResourceModule.builder(
                    TargetKey.forPlainTarget(Label.create("//java/apps/example:example_debug")))
                .addResourceAndTransitiveResource(source("java/apps/example/res"))
                .addTransitiveResource(source("java/apps/example/lib0/res"))
                .addTransitiveResource(source("java/apps/example/lib1/res"))
                .addTransitiveResource(source("java/libraries/shared/res"))
                .addTransitiveResourceDependency("//java/apps/example/lib0:lib0")
                .addTransitiveResourceDependency("//java/apps/example/lib1:lib1")
                .addTransitiveResourceDependency("//java/libraries/shared:shared")
                .build(),
            AndroidResourceModule.builder(
                    TargetKey.forPlainTarget(Label.create("//java/apps/example/lib0:lib0")))
                .addResourceAndTransitiveResource(source("java/apps/example/lib0/res"))
                .addTransitiveResource(source("java/apps/example/lib1/res"))
                .addTransitiveResource(source("java/libraries/shared/res"))
                .addTransitiveResourceDependency("//java/apps/example/lib1:lib1")
                .addTransitiveResourceDependency("//java/libraries/shared:shared")
                .build(),
            AndroidResourceModule.builder(
                    TargetKey.forPlainTarget(Label.create("//java/apps/example/lib1:lib1")))
                .addResourceAndTransitiveResource(source("java/apps/example/lib1/res"))
                .addTransitiveResource(source("java/libraries/shared/res"))
                .addTransitiveResourceDependency("//java/libraries/shared:shared")
                .build());
  }

  /** Test adding empty resource modules as jars. */
  @Test
  public void testEmptyResourceModuleIsAddedAsJar() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("java/apps/example")))
                    .add(DirectoryEntry.include(new WorkspacePath("javatests/apps/example"))))
            .build();

    /** Deps are project -> lib0 (no res) -> lib1 (has res) \ -> lib2 (has res) */
    TargetMapBuilder response =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//java/apps/example/lib0:lib0")
                    .setKind("android_library")
                    .setBuildFile(source("java/apps/example/lib0/BUILD"))
                    .addSource(source("java/apps/example/lib0/SharedActivity.java"))
                    .setAndroidInfo(
                        AndroidIdeInfo.builder()
                            .setManifestFile(source("java/apps/example/lib0/AndroidManifest.xml"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.android.apps.example.lib0")
                            .setResourceJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(
                                        gen("java/apps/example/lib0/lib0_resources.jar"))
                                    .setClassJar(gen("java/apps/example/lib0/lib0_resources.jar"))))
                    .addDependency("//java/apps/example/lib1:lib1")
                    .addDependency("//java/apps/example/lib2:lib2")
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("java/apps/example/lib0/lib0.jar"))
                                    .setClassJar(gen("java/apps/example/lib0/lib0.jar")))
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(
                                        gen("java/apps/example/lib0/lib0_resources.jar"))
                                    .setClassJar(
                                        gen("java/apps/example/lib0/lib0_resources.jar")))))
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//java/apps/example/lib1:lib1")
                    .setKind("android_library")
                    .setBuildFile(source("java/apps/example/lib1/BUILD"))
                    .addSource(source("java/apps/example/lib1/SharedActivity.java"))
                    .setAndroidInfo(
                        AndroidIdeInfo.builder()
                            .setManifestFile(source("java/apps/example/lib1/AndroidManifest.xml"))
                            .addResource(source("java/apps/example/lib1/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.android.apps.example.lib1")
                            .setResourceJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(
                                        gen("java/apps/example/lib1/li11_resources.jar"))
                                    .setClassJar(gen("java/apps/example/lib1/lib1_resources.jar"))))
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("java/apps/example/lib1/lib1.jar"))
                                    .setClassJar(gen("java/apps/example/lib1/lib1.jar")))
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(
                                        gen("java/apps/example/lib1/lib1_resources.jar"))
                                    .setClassJar(
                                        gen("java/apps/example/lib1/lib1_resources.jar")))))
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//java/apps/example/lib2:lib2")
                    .setBuildFile(source("java/apps/example/lib2/BUILD"))
                    .setKind("android_library")
                    .addSource(source("java/apps/example/lib2/SharedActivity.java"))
                    .setAndroidInfo(
                        AndroidIdeInfo.builder()
                            .setManifestFile(source("java/apps/example/lib2/AndroidManifest.xml"))
                            .addResource(source("java/apps/example/lib2/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.android.libraries.example.lib2")
                            .setResourceJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(
                                        gen("java/apps/example/lib2/lib2_resources.jar"))
                                    .setClassJar(gen("java/apps/example/lib2/lib2_resources.jar"))))
                    .setBuildFile(source("java/apps/example/lib2/BUILD"))
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("java/apps/example/lib2/lib2.jar"))
                                    .setClassJar(gen("java/apps/example/lib2/lib2.jar")))
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(
                                        gen("java/apps/example/lib2/lib2_resources.jar"))
                                    .setClassJar(
                                        gen("java/apps/example/lib2/lib2_resources.jar")))))
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//java/apps/example:example_debug")
                    .setKind("android_binary")
                    .setBuildFile(source("java/apps/example/BUILD"))
                    .addSource(source("java/apps/example/MainActivity.java"))
                    .setAndroidInfo(
                        AndroidIdeInfo.builder()
                            .setManifestFile(source("java/apps/example/AndroidManifest.xml"))
                            .addResource(source("java/apps/example/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.android.apps.example"))
                    .addDependency("//java/apps/example/lib0:lib0")
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("java/apps/example/example_debug.jar"))
                                    .setClassJar(gen("java/apps/example/example_debug.jar")))));

    TargetMap targetMap = response.build();
    BlazeAndroidJavaSyncAugmenter syncAugmenter = new BlazeAndroidJavaSyncAugmenter();
    List<BlazeJarLibrary> jars = Lists.newArrayList();
    List<BlazeJarLibrary> genJars = Lists.newArrayList();
    ImportRoots importRoots =
        ImportRoots.builder(workspaceRoot, BuildSystem.Blaze)
            .add(ProjectViewSet.builder().add(projectView).build())
            .build();
    WorkspaceLanguageSettings workspaceLanguageSettings =
        new WorkspaceLanguageSettings(
            WorkspaceType.ANDROID, ImmutableSet.of(LanguageClass.ANDROID, LanguageClass.JAVA));
    ProjectViewSet projectViewSet = ProjectViewSet.builder().add(projectView).build();
    for (TargetIdeInfo target : targetMap.targets()) {
      if (importRoots.importAsSource(target.key.label)) {
        syncAugmenter.addJarsForSourceTarget(
            workspaceLanguageSettings, projectViewSet, target, jars, genJars);
      }
    }

    assertThat(
            jars.stream()
                .map(library -> library.libraryArtifact.interfaceJar)
                .map(artifactLocation -> new File(artifactLocation.relativePath).getName())
                .collect(Collectors.toList()))
        .containsExactly("lib0_resources.jar");
  }

  @Test
  public void testIdlClassJarIsAddedAsLibrary() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("example"))))
            .build();

    TargetMapBuilder targetMapBuilder =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//example:lib")
                    .setBuildFile(source("example/BUILD"))
                    .setKind("android_binary")
                    .addSource(source("example/MainActivity.java"))
                    .setAndroidInfo(
                        AndroidIdeInfo.builder()
                            .setResourceJavaPackage("example")
                            .setIdlJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("example/libidl.jar"))
                                    .setSourceJar(gen("example/libidl.srcjar"))
                                    .build())
                            .setHasIdlSources(true)));

    TargetMap targetMap = targetMapBuilder.build();
    BlazeAndroidJavaSyncAugmenter syncAugmenter = new BlazeAndroidJavaSyncAugmenter();
    List<BlazeJarLibrary> jars = Lists.newArrayList();
    List<BlazeJarLibrary> genJars = Lists.newArrayList();
    ImportRoots importRoots =
        ImportRoots.builder(workspaceRoot, BuildSystem.Blaze)
            .add(ProjectViewSet.builder().add(projectView).build())
            .build();
    WorkspaceLanguageSettings workspaceLanguageSettings =
        new WorkspaceLanguageSettings(
            WorkspaceType.ANDROID, ImmutableSet.of(LanguageClass.ANDROID, LanguageClass.JAVA));
    ProjectViewSet projectViewSet = ProjectViewSet.builder().add(projectView).build();
    for (TargetIdeInfo target : targetMap.targets()) {
      if (importRoots.importAsSource(target.key.label)) {
        syncAugmenter.addJarsForSourceTarget(
            workspaceLanguageSettings, projectViewSet, target, jars, genJars);
      }
    }
    assertThat(
            genJars
                .stream()
                .map(library -> library.libraryArtifact.interfaceJar)
                .map(artifactLocation -> new File(artifactLocation.relativePath).getName())
                .collect(Collectors.toList()))
        .containsExactly("libidl.jar");
  }

  @Test
  public void testAndroidResourceImport() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("java/example"))))
            .build();

    TargetMapBuilder targetMapBuilder =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//java/example:lib")
                    .setBuildFile(source("java/example/BUILD"))
                    .setKind("android_library")
                    .setAndroidInfo(
                        AndroidIdeInfo.builder()
                            .setLegacyResources(Label.create("//java/example:resources"))
                            .setManifestFile(source("java/example/AndroidManifest.xml"))
                            .addResource(source("java/example/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.android.example"))
                    .build())
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//java/example:resources")
                    .setBuildFile(source("java/example/BUILD"))
                    .setKind("android_resources")
                    .setAndroidInfo(
                        AndroidIdeInfo.builder()
                            .setManifestFile(source("java/example/AndroidManifest.xml"))
                            .addResource(source("java/example/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.android.example"))
                    .build());

    BlazeAndroidImportResult result = importWorkspace(workspaceRoot, targetMapBuilder, projectView);
    errorCollector.assertNoIssues();
    assertThat(result.androidResourceModules)
        .containsExactly(
            AndroidResourceModule.builder(
                    TargetKey.forPlainTarget(Label.create("//java/example:resources")))
                .addResourceAndTransitiveResource(source("java/example/res"))
                .build());
  }

  @Test
  public void testResourceImportOutsideSourceFilterIsAddedToResourceLibrary() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("java/example"))))
            .build();

    TargetMapBuilder targetMapBuilder =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//java/example:lib")
                    .setBuildFile(source("java/example/BUILD"))
                    .setKind("android_library")
                    .setAndroidInfo(
                        AndroidIdeInfo.builder()
                            .setManifestFile(source("java/example/AndroidManifest.xml"))
                            .addResource(source("java/example/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.android.example"))
                    .addDependency("//java/example2:resources")
                    .build())
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//java/example2:resources")
                    .setBuildFile(source("java/example2/BUILD"))
                    .setKind("android_library")
                    .setAndroidInfo(
                        AndroidIdeInfo.builder()
                            .setManifestFile(source("java/example2/AndroidManifest.xml"))
                            .addResource(source("java/example2/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.android.example2"))
                    .build());

    BlazeAndroidImportResult result = importWorkspace(workspaceRoot, targetMapBuilder, projectView);
    errorCollector.assertNoIssues();
    BlazeResourceLibrary library = result.resourceLibrary;
    assertThat(library).isNotNull();
    assertThat(library.sources)
        .containsExactly(
            ArtifactLocation.builder()
                .setRelativePath("java/example2/res")
                .setIsSource(true)
                .build());
  }

  @Test
  public void testConflictingResourceRClasses() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("java/example"))))
            .build();

    TargetMapBuilder targetMapBuilder =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//java/example:lib")
                    .setBuildFile(source("java/example/BUILD"))
                    .setKind("android_library")
                    .setAndroidInfo(
                        AndroidIdeInfo.builder()
                            .setManifestFile(source("java/example/AndroidManifest.xml"))
                            .addResource(source("java/example/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.android.example"))
                    .addDependency("//java/example2:resources")
                    .build())
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//java/example:lib2")
                    .setBuildFile(source("java/example2/BUILD"))
                    .setKind("android_library")
                    .setAndroidInfo(
                        AndroidIdeInfo.builder()
                            .setManifestFile(source("java/example2/AndroidManifest.xml"))
                            .addResource(source("java/example/res2"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.android.example"))
                    .build());

    BlazeAndroidImportResult result = importWorkspace(workspaceRoot, targetMapBuilder, projectView);
    errorCollector.assertIssueContaining("Multiple R classes generated");

    assertThat(result.androidResourceModules)
        .containsExactly(
            AndroidResourceModule.builder(
                    TargetKey.forPlainTarget(Label.create("//java/example:lib")))
                .addResourceAndTransitiveResource(source("java/example/res"))
                .build());
  }

  @Test
  public void testMixingGeneratedAndNonGeneratedSourcesGeneratesIssue() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("java/example"))))
            .build();

    TargetMapBuilder targetMapBuilder =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//java/example:lib")
                    .setBuildFile(source("java/example/BUILD"))
                    .setKind("android_library")
                    .setAndroidInfo(
                        AndroidIdeInfo.builder()
                            .setManifestFile(source("java/example/AndroidManifest.xml"))
                            .addResource(source("java/example/res"))
                            .addResource(gen("java/example/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.android.example"))
                    .build());

    importWorkspace(workspaceRoot, targetMapBuilder, projectView);
    errorCollector.assertIssueContaining("Dropping 1 generated resource");
  }

  @Test
  public void testMixingGeneratedAndNonGeneratedSourcesWhitelisted() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("java/example"))))
            .add(
                ListSection.builder(GeneratedAndroidResourcesSection.KEY)
                    .add(new GenfilesPath("java/example/res")))
            .build();

    TargetMapBuilder targetMapBuilder =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//java/example:lib")
                    .setBuildFile(source("java/example/BUILD"))
                    .setKind("android_library")
                    .setAndroidInfo(
                        AndroidIdeInfo.builder()
                            .setManifestFile(source("java/example/AndroidManifest.xml"))
                            .addResource(source("java/example/res"))
                            .addResource(gen("java/example/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.android.example"))
                    .build());

    BlazeAndroidImportResult result = importWorkspace(workspaceRoot, targetMapBuilder, projectView);
    errorCollector.assertNoIssues();
    assertThat(result.androidResourceModules)
        .containsExactly(
            AndroidResourceModule.builder(
                    TargetKey.forPlainTarget(Label.create("//java/example:lib")))
                .addResourceAndTransitiveResource(source("java/example/res"))
                .addResourceAndTransitiveResource(gen("java/example/res"))
                .build());
  }

  @Test
  public void testMixingGeneratedAndNonGeneratedSourcesPartlyWhitelisted() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("java/example")))
                    .add(DirectoryEntry.include(new WorkspacePath("java/example2")))
                    .add(DirectoryEntry.include(new WorkspacePath("java/uninterestingdir"))))
            .add(
                ListSection.builder(GeneratedAndroidResourcesSection.KEY)
                    .add(new GenfilesPath("java/example/res"))
                    .add(new GenfilesPath("unused/whitelisted/path/res")))
            .build();

    TargetMapBuilder targetMapBuilder =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//java/example:lib")
                    .setBuildFile(source("java/example/BUILD"))
                    .setKind("android_library")
                    .setAndroidInfo(
                        AndroidIdeInfo.builder()
                            .setManifestFile(source("java/example/AndroidManifest.xml"))
                            .addResource(source("java/example/res"))
                            .addResource(gen("java/example/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.android.example"))
                    .build())
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//java/example2:lib")
                    .setBuildFile(source("java/example2/BUILD"))
                    .setKind("android_library")
                    .setAndroidInfo(
                        AndroidIdeInfo.builder()
                            .setManifestFile(source("java/example2/AndroidManifest.xml"))
                            .addResource(source("java/example2/res"))
                            .addResource(gen("java/example2/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.android.example2"))
                    .build())
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//java/uninterestingdir:lib")
                    .setBuildFile(source("java/uninterestingdir/BUILD"))
                    .setKind("android_library")
                    .setAndroidInfo(
                        AndroidIdeInfo.builder()
                            .setManifestFile(source("java/uninterestingdir/AndroidManifest.xml"))
                            .addResource(source("java/uninterestingdir/res"))
                            .addResource(gen("java/uninterestingdir/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.android.uninterestingdir"))
                    .build());

    importWorkspace(workspaceRoot, targetMapBuilder, projectView);
    errorCollector.assertIssues(
        "Dropping 1 generated resource directories.\n"
            + "R classes will not contain resources from these directories.\n"
            + "Double-click to add to project view if needed to resolve references.",
        "Dropping generated resource directory "
            + String.format("'%s/java/example2/res'", FAKE_GEN_ROOT_EXECUTION_PATH_FRAGMENT)
            + " w/ 2 subdirs",
        "1 unused entries in project view section \"generated_android_resource_directories\":\n"
            + "unused/whitelisted/path/res");
  }

  @Test
  public void testMixingGeneratedAndNonGeneratedSourcesNoInterestingDirectories() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("java/uninterestingdir"))))
            .build();

    TargetMapBuilder targetMapBuilder =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//java/uninterestingdir:lib")
                    .setBuildFile(source("java/uninterestingdir/BUILD"))
                    .setKind("android_library")
                    .setAndroidInfo(
                        AndroidIdeInfo.builder()
                            .setManifestFile(source("java/uninterestingdir/AndroidManifest.xml"))
                            .addResource(source("java/uninterestingdir/res"))
                            .addResource(gen("java/uninterestingdir/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.android.uninterestingdir"))
                    .build());

    BlazeAndroidImportResult result = importWorkspace(workspaceRoot, targetMapBuilder, projectView);
    errorCollector.assertNoIssues();
    assertThat(result.androidResourceModules)
        .containsExactly(
            AndroidResourceModule.builder(
                    TargetKey.forPlainTarget(Label.create("//java/uninterestingdir:lib")))
                .addResourceAndTransitiveResource(source("java/uninterestingdir/res"))
                .build());
  }

  /**
   * Mock provider to satisfy directory listing queries from {@link
   * com.google.idea.blaze.android.sync.importer.problems.GeneratedResourceClassifier}.
   */
  private static class MockFileAttributeProvider extends FileAttributeProvider {

    // Return a few non-translation directories so that directories are considered interesting,
    // or return only-translation directories so that it's considered uninteresting.
    @Override
    public File[] listFiles(File directory) {
      File interestingResDir1 = FAKE_ARTIFACT_DECODER.decode(gen("java/example/res"));
      if (directory.equals(interestingResDir1)) {
        return new File[] {
          new File("java/example/res/raw"), new File("java/example/res/values-es"),
        };
      }
      File interestingResDir2 = FAKE_ARTIFACT_DECODER.decode(gen("java/example2/res"));
      if (directory.equals(interestingResDir2)) {
        return new File[] {
          new File("java/example2/res/layout"), new File("java/example2/res/values-ar"),
        };
      }
      File uninterestingResDir = FAKE_ARTIFACT_DECODER.decode(gen("java/uninterestingdir/res"));
      if (directory.equals(uninterestingResDir)) {
        return new File[] {
          new File("java/uninterestingdir/res/values-ar"),
          new File("java/uninterestingdir/res/values-es"),
        };
      }
      return new File[0];
    }
  }

  private ArtifactLocation source(String relativePath) {
    return ArtifactLocation.builder().setRelativePath(relativePath).setIsSource(true).build();
  }

  private static ArtifactLocation gen(String relativePath) {
    return ArtifactLocation.builder()
        .setRootExecutionPathFragment(FAKE_GEN_ROOT_EXECUTION_PATH_FRAGMENT)
        .setRelativePath(relativePath)
        .setIsSource(false)
        .build();
  }
}
