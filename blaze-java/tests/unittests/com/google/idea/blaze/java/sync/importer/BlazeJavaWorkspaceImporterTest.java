/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.java.sync.importer;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.google.idea.blaze.base.async.executor.MockBlazeExecutor;
import com.google.idea.blaze.base.experiments.ExperimentService;
import com.google.idea.blaze.base.experiments.MockExperimentService;
import com.google.idea.blaze.base.ideinfo.*;
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.prefetch.MockPrefetchService;
import com.google.idea.blaze.base.prefetch.PrefetchService;
import com.google.idea.blaze.base.projectview.ProjectView;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.Glob;
import com.google.idea.blaze.base.projectview.section.ListSection;
import com.google.idea.blaze.base.projectview.section.sections.*;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.ErrorCollector;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.settings.Blaze.BuildSystem;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.sync.workspace.BlazeRoots;
import com.google.idea.blaze.base.sync.workspace.WorkingSet;
import com.google.idea.blaze.java.sync.jdeps.JdepsMap;
import com.google.idea.blaze.java.sync.model.*;
import com.google.idea.blaze.java.sync.source.JavaSourcePackageReader;
import com.google.idea.blaze.java.sync.source.PackageManifestReader;
import com.google.idea.blaze.java.sync.source.SourceArtifact;
import com.google.idea.blaze.java.sync.workingset.JavaWorkingSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.*;

/**
 * Tests for BlazeJavaWorkspaceImporter
 */
public class BlazeJavaWorkspaceImporterTest extends BlazeTestCase {

  private String FAKE_WORKSPACE_ROOT = "/root";
  private WorkspaceRoot workspaceRoot = new WorkspaceRoot(new File(FAKE_WORKSPACE_ROOT));

  private static final String FAKE_GEN_ROOT_EXECUTION_PATH_FRAGMENT =
    "blaze-out/gcc-4.X.Y-crosstool-v17-hybrid-grtev3-k8-fastbuild/bin";

  private static final String FAKE_GEN_ROOT =
    "/path/to/8093958afcfde6c33d08b621dfaa4e09/root/"
    + FAKE_GEN_ROOT_EXECUTION_PATH_FRAGMENT;

  private static final ArtifactLocationDecoder FAKE_ARTIFACT_DECODER = new ArtifactLocationDecoder(
    new BlazeRoots(
      new File("/"),
      ImmutableList.of(),
      new ExecutionRootPath("out/crosstool/bin"),
      new ExecutionRootPath("out/crosstool/gen")
    ),
    null
  );

  private static final BlazeImportSettings DUMMY_IMPORT_SETTINGS = new BlazeImportSettings("", "", "", "", "", BuildSystem.Blaze);

  private static class JdepsMock implements JdepsMap {
    Map<Label, List<String>> jdeps = Maps.newHashMap();

    @Nullable
    @Override
    public List<String> getDependenciesForRule(@NotNull Label label) {
      return jdeps.get(label);
    }

    JdepsMock put(Label label, List<String> values) {
      jdeps.put(label, values);
      return this;
    }
  }

  private BlazeContext context;
  private ErrorCollector errorCollector = new ErrorCollector();
  private final JdepsMock jdepsMap = new JdepsMock();
  private JavaWorkingSet workingSet = null;
  private MockExperimentService experimentService;

  @Override
  protected void initTest(@NotNull Container applicationServices, @NotNull Container projectServices) {
    experimentService = new MockExperimentService();
    applicationServices.register(ExperimentService.class, experimentService);

    BlazeExecutor blazeExecutor = new MockBlazeExecutor();
    applicationServices.register(BlazeExecutor.class, blazeExecutor);
    projectServices.register(BlazeImportSettingsManager.class, new BlazeImportSettingsManager(project));
    BlazeImportSettingsManager.getInstance(getProject()).setImportSettings(DUMMY_IMPORT_SETTINGS);

    // will silently fall back to FilePathJavaPackageReader
    applicationServices.register(
      JavaSourcePackageReader.class,
      new JavaSourcePackageReader() {
        @Nullable
        @Override
        public String getDeclaredPackageOfJavaFile(@NotNull BlazeContext context, @NotNull SourceArtifact sourceArtifact) {
          return null;
        }
      }
    );
    applicationServices.register(PackageManifestReader.class, new PackageManifestReader());
    applicationServices.register(PrefetchService.class, new MockPrefetchService());

    context = new BlazeContext();
    context.addOutputSink(IssueOutput.class, errorCollector);
  }

  BlazeJavaImportResult importWorkspace(
    WorkspaceRoot workspaceRoot,
    RuleMapBuilder ruleMapBuilder,
    ProjectView projectView) {

    ProjectViewSet projectViewSet = ProjectViewSet.builder().add(projectView).build();

    BlazeJavaWorkspaceImporter blazeWorkspaceImporter = new BlazeJavaWorkspaceImporter(
      project,
      workspaceRoot,
      projectViewSet,
      ruleMapBuilder.build(),
      jdepsMap,
      workingSet,
      FAKE_ARTIFACT_DECODER
    );

    return blazeWorkspaceImporter.importWorkspace(context);
  }

  /**
   * Ensure an empty response results in an empty import result.
   */
  @Test
  public void testEmptyProject() {
    BlazeJavaImportResult result = importWorkspace(
      workspaceRoot,
      RuleMapBuilder.builder(),
      ProjectView.builder().build()
    );
    errorCollector.assertNoIssues();
    assertTrue(result.contentEntries.isEmpty());
  }

  @Test
  public void testSingleModule() {
    ProjectView projectView = ProjectView.builder()
      .put(ListSection.builder(DirectorySection.KEY)
             .add(DirectoryEntry.include(new WorkspacePath("java/com/google/android/apps/example"))))
      .build();

    RuleMapBuilder ruleMapBuilder = RuleMapBuilder.builder()
      .addRule(
        RuleIdeInfo.builder()
          .setLabel("//java/com/google/android/apps/example:example_debug")
          .setBuildFile(sourceRoot("java/com/google/android/apps/example/BUILD"))
          .setKind("android_binary")
          .addSource(sourceRoot("java/com/google/android/apps/example/MainActivity.java"))
          .addSource(sourceRoot("java/com/google/android/apps/example/subdir/SubdirHelper.java"))
          .setAndroidInfo(AndroidRuleIdeInfo.builder()
                            .setManifestFile(sourceRoot("java/com/google/android/apps/example/AndroidManifest.xml"))
                            .addResource(sourceRoot("java/com/google/android/apps/example/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.android.apps.example"))
          .setJavaInfo(JavaRuleIdeInfo.builder()
                         .addJar(LibraryArtifact.builder()
                                   .setJar(genRoot("java/com/google/android/apps/example/example_debug-ijar.jar"))
                                   .setRuntimeJar(genRoot("java/com/google/android/apps/example/example_debug.jar")))));

    BlazeJavaImportResult result = importWorkspace(
      workspaceRoot,
      ruleMapBuilder,
      projectView
    );
    errorCollector.assertNoIssues();

    assertEquals(1, result.buildOutputJars.size());
    File compilerOutputLib = result.buildOutputJars.iterator().next();
    assertNotNull(compilerOutputLib);
    assertTrue(compilerOutputLib.getPath().endsWith("example_debug.jar"));

    assertThat(result.contentEntries).containsExactly(
      BlazeContentEntry.builder("/root/java/com/google/android/apps/example")
        .addSource(BlazeSourceDirectory.builder("/root/java/com/google/android/apps/example")
                     .setPackagePrefix("com.google.android.apps.example")
                     .build())
        .build()
    );

    assertThat(result.javaSourceFiles).containsExactly(
      sourceRoot("java/com/google/android/apps/example/MainActivity.java").getFile(),
      sourceRoot("java/com/google/android/apps/example/subdir/SubdirHelper.java").getFile()
    );
  }

  @Test
  public void testGeneratedLibrariesIncluded() {
    ProjectView projectView = ProjectView.builder()
      .put(ListSection.builder(DirectorySection.KEY)
             .add(DirectoryEntry.include(new WorkspacePath("java/com/google/example"))))
             .build();

    RuleMapBuilder ruleMapBuilder = RuleMapBuilder.builder()
      .addRule(
        RuleIdeInfo.builder()
          .setLabel("//java/com/google/example:lib")
          .setBuildFile(sourceRoot("java/com/google/example/BUILD"))
          .setKind("java_library")
          .addSource(sourceRoot("java/com/google/example/Test.java"))
          .setJavaInfo(JavaRuleIdeInfo.builder()
                         .addJar(LibraryArtifact.builder()
                                   .setJar(genRoot("java/com/google/example/lib-ijar.jar"))
                                   .setRuntimeJar(genRoot("java/com/google/example/lib.jar")))
                         .addGeneratedJar(LibraryArtifact.builder()
                                            .setJar(genRoot("java/com/google/example/lib-gen.jar"))
                                            .setRuntimeJar(genRoot("java/com/google/example/lib-gen.jar")))));

    BlazeJavaImportResult result = importWorkspace(
      workspaceRoot,
      ruleMapBuilder,
      projectView
    );
    assertThat(result.libraries.values().stream().map(BlazeJavaWorkspaceImporterTest::libraryFileName).collect(Collectors.toList()))
      .containsExactly("lib-gen.jar");
  }


  /**
   * Imports two binaries and a library. Only one binary should pass the package filter.
   */
  @Test
  public void testImportFilter() {
    ProjectView projectView = ProjectView.builder()
      .put(ListSection.builder(DirectorySection.KEY)
             .add(DirectoryEntry.include(new WorkspacePath("java/com/google/android/apps/example"))))
      .build();

    RuleMapBuilder ruleMapBuilder = RuleMapBuilder.builder()
      .addRule(
        RuleIdeInfo.builder()
          .setLabel("//java/com/google/android/apps/example:example_debug")
          .setBuildFile(sourceRoot("java/com/google/android/apps/example/BUILD"))
          .setKind("android_binary")
          .addSource(sourceRoot("java/com/google/android/apps/example/MainActivity.java"))
          .setAndroidInfo(AndroidRuleIdeInfo.builder()
                            .setManifestFile(sourceRoot("java/com/google/android/apps/example/AndroidManifest.xml"))
                            .addResource(sourceRoot("java/com/google/android/apps/example/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.android.apps.example"))
          .addDependency("//java/com/google/android/libraries/example:example")
          .setJavaInfo(JavaRuleIdeInfo.builder()
                         .addJar(LibraryArtifact.builder()
                                   .setJar(genRoot("java/com/google/android/apps/example/example_debug.jar"))
                                   .setRuntimeJar(genRoot("java/com/google/android/apps/example/example_debug.jar")))))
      .addRule(
        RuleIdeInfo.builder()
          .setLabel("//java/com/google/android/libraries/example:example")
          .setBuildFile(sourceRoot("java/com/google/android/libraries/example/BUILD"))
          .setKind("android_library")
          .addSource(sourceRoot("java/com/google/android/libraries/example/SharedActivity.java"))
          .setAndroidInfo(
            AndroidRuleIdeInfo.builder()
              .setManifestFile(sourceRoot("java/com/google/android/libraries/example/AndroidManifest.xml"))
              .addResource(sourceRoot("java/com/google/android/libraries/example/res"))
              .setGenerateResourceClass(true)
              .setResourceJavaPackage("com.google.android.libraries.example"))
          .setJavaInfo(JavaRuleIdeInfo.builder()
                         .addJar(LibraryArtifact.builder()
                                   .setJar(genRoot("java/com/google/android/libraries/example/example.jar"))
                                   .setRuntimeJar(genRoot("java/com/google/android/libraries/example/example.jar")))))
      .addRule(
        RuleIdeInfo.builder()
          .setLabel("//java/com/dontimport:example_debug")
          .setBuildFile(sourceRoot("java/com/dontimport/BUILD"))
          .setKind("android_binary")
          .addSource(sourceRoot("java/com/dontimport/MainActivity.java"))
          .setAndroidInfo(AndroidRuleIdeInfo.builder()
                            .setManifestFile(sourceRoot("java/com/dontimport/AndroidManifest.xml"))
                            .addResource(sourceRoot("java/com/dontimport/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.dontimport"))
          .addDependency("//java/com/dontimport:sometarget")
          .setJavaInfo(JavaRuleIdeInfo.builder()
                         .addJar(LibraryArtifact.builder()
                                   .setJar(genRoot("java/com/dontimport/example_debug.jar"))
                                   .setRuntimeJar(genRoot("java/com/dontimport/example_debug.jar")))));

    BlazeJavaImportResult result = importWorkspace(
      workspaceRoot,
      ruleMapBuilder,
      projectView
    );
    errorCollector.assertNoIssues();

    assertThat(result.contentEntries).containsExactly(
      BlazeContentEntry.builder("/root/java/com/google/android/apps/example")
        .addSource(BlazeSourceDirectory.builder("/root/java/com/google/android/apps/example")
                     .setPackagePrefix("com.google.android.apps.example")
                     .build())
        .build()
    );
    assertThat(result.javaSourceFiles).containsExactly(
      sourceRoot("java/com/google/android/apps/example/MainActivity.java").getFile()
    );
  }

  /**
   * Import a project and its tests
   */
  @Test
  public void testProjectAndTests() {
    ProjectView projectView = ProjectView.builder()
      .put(ListSection.builder(DirectorySection.KEY)
             .add(DirectoryEntry.include(new WorkspacePath("java/com/google/android/apps/example")))
             .add(DirectoryEntry.include(new WorkspacePath("javatests/com/google/android/apps/example"))))
      .put(ListSection.builder(TestSourceSection.KEY).add(new Glob("javatests/*")))
      .build();

    RuleMapBuilder ruleMapBuilder = RuleMapBuilder.builder()
      .addRule(
        RuleIdeInfo.builder()
          .setLabel("//java/com/google/android/apps/example:example_debug")
          .setBuildFile(sourceRoot("java/com/google/android/apps/example/BUILD"))
          .setKind("android_binary")
          .addSource(sourceRoot("java/com/google/android/apps/example/MainActivity.java"))
          .addSource(sourceRoot("java/com/google/android/apps/example/subdir/SubdirHelper.java"))
          .setAndroidInfo(AndroidRuleIdeInfo.builder()
                            .setManifestFile(sourceRoot("java/com/google/android/apps/example/AndroidManifest.xml"))
                            .addResource(sourceRoot("java/com/google/android/apps/example/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.android.apps.example"))
          .setJavaInfo(JavaRuleIdeInfo.builder()
                         .addJar(LibraryArtifact.builder()
                                   .setJar(genRoot("java/com/google/android/apps/example/example_debug.jar"))
                                   .setRuntimeJar(genRoot("java/com/google/android/apps/example/example_debug.jar")))))
      .addRule(
        RuleIdeInfo.builder()
          .setLabel("//javatests/com/google/android/apps/example:example")
          .setBuildFile(sourceRoot("javatests/com/google/android/apps/example/BUILD"))
          .setKind("android_test")
          .addSource(sourceRoot("javatests/com/google/android/apps/example/ExampleTests.java"))
          .setAndroidInfo(AndroidRuleIdeInfo.builder()
                            .setResourceJavaPackage("com.google.android.apps.example"))
          .addDependency("//java/com/google/android/apps/example:example_debug")
          .setJavaInfo(JavaRuleIdeInfo.builder()
                         .addJar(LibraryArtifact.builder()
                                   .setJar(genRoot("javatests/com/google/android/apps/example/example.jar"))
                                   .setRuntimeJar(genRoot("javatests/com/google/android/apps/example/example.jar")))));

    BlazeJavaImportResult result = importWorkspace(
      workspaceRoot,
      ruleMapBuilder,
      projectView
    );
    errorCollector.assertNoIssues();

    assertThat(result.contentEntries).containsExactly(
      BlazeContentEntry.builder("/root/java/com/google/android/apps/example")
        .addSource(BlazeSourceDirectory.builder("/root/java/com/google/android/apps/example")
                     .setPackagePrefix("com.google.android.apps.example")
                     .build())
        .build(),
      BlazeContentEntry.builder("/root/javatests/com/google/android/apps/example")
        .addSource(BlazeSourceDirectory.builder("/root/javatests/com/google/android/apps/example")
                     .setPackagePrefix("com.google.android.apps.example")
                     .setTest(true)
                     .build())
        .build()
    );
  }

  /**
   * Test library with a source jar
   */
  @Test
  public void testLibraryWithSourceJar() {
    ProjectView projectView = ProjectView.builder()
      .put(ListSection.builder(DirectorySection.KEY)
             .add(DirectoryEntry.include(new WorkspacePath("java/com/google/android/apps/example")))
             .add(DirectoryEntry.include(new WorkspacePath("javatests/com/google/android/apps/example"))))
      .build();

    RuleMapBuilder ruleMapBuilder = RuleMapBuilder.builder()
      .addRule(
        RuleIdeInfo.builder()
          .setLabel("//java/com/google/android/apps/example:example_debug")
          .setBuildFile(sourceRoot("java/com/google/android/apps/example/BUILD"))
          .setKind("android_binary")
          .addSource(sourceRoot("java/com/google/android/apps/example/MainActivity.java"))
          .addSource(sourceRoot("java/com/google/android/apps/example/subdir/SubdirHelper.java"))
          .setAndroidInfo(AndroidRuleIdeInfo.builder()
                            .setManifestFile(sourceRoot("java/com/google/android/apps/example/AndroidManifest.xml"))
                            .addResource(genRoot("java/com/google/android/apps/example/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.android.apps.example"))
          .addDependency("//thirdparty/some/library:library")
          .setJavaInfo(JavaRuleIdeInfo.builder()
                         .addJar(LibraryArtifact.builder()
                                   .setJar(genRoot("java/com/google/android/apps/example/example_debug.jar"))
                                   .setRuntimeJar(genRoot("java/com/google/android/apps/example/example_debug.jar")))))
      .addRule(
        RuleIdeInfo.builder()
          .setLabel("//thirdparty/some/library:library")
          .setBuildFile(sourceRoot("/thirdparty/some/library/BUILD"))
          .setKind("java_import")
          .setJavaInfo(JavaRuleIdeInfo.builder()
                         .addJar(LibraryArtifact.builder()
                                   .setJar(genRoot("thirdparty/some/library.jar"))
                                   .setRuntimeJar(genRoot("thirdparty/some/library.jar"))
                                   .setSourceJar(genRoot("thirdparty/some/library.srcjar")))));

    BlazeJavaImportResult result = importWorkspace(
      workspaceRoot,
      ruleMapBuilder,
      projectView
    );
    errorCollector.assertNoIssues();

    BlazeLibrary library = findLibrary(result.libraries, "library.jar");
    assertNotNull(library);
    assertNotNull(library.getLibraryArtifact().sourceJar);
  }

  /**
   * Test a project with a java test rule
   */
  @Test
  public void testJavaTestRule() {
    ProjectView projectView = ProjectView.builder()
      .put(ListSection.builder(DirectorySection.KEY)
             .add(DirectoryEntry.include(new WorkspacePath("java/com/google/android/apps/example")))
             .add(DirectoryEntry.include(new WorkspacePath("javatests/com/google/android/apps/example"))))
      .put(ListSection.builder(TestSourceSection.KEY).add(new Glob("javatests/*")))
      .build();

    RuleMapBuilder ruleMapBuilder = RuleMapBuilder.builder()
      .addRule(
        RuleIdeInfo.builder()
          .setLabel("//java/com/google/android/apps/example:example_debug")
          .setBuildFile(sourceRoot("java/com/google/android/apps/example/BUILD"))
          .setKind("android_binary")
          .addSource(sourceRoot("java/com/google/android/apps/example/MainActivity.java"))
          .addSource(sourceRoot("java/com/google/android/apps/example/subdir/SubdirHelper.java"))
          .setAndroidInfo(AndroidRuleIdeInfo.builder()
                            .setManifestFile(sourceRoot("java/com/google/android/apps/example/AndroidManifest.xml"))
                            .addResource(sourceRoot("java/com/google/android/apps/example/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.android.apps.example"))
          .setJavaInfo(JavaRuleIdeInfo.builder()
                         .addJar(LibraryArtifact.builder()
                                   .setJar(genRoot("java/com/google/android/apps/example/example_debug.jar"))
                                   .setRuntimeJar(genRoot("java/com/google/android/apps/example/example_debug.jar")))))
      .addRule(
        RuleIdeInfo.builder()
          .setLabel("//javatests/com/google/android/apps/example:example")
          .setBuildFile(sourceRoot("javatests/com/google/android/apps/example/BUILD"))
          .setKind("java_test")
          .addSource(sourceRoot("javatests/com/google/android/apps/example/ExampleTests.java"))
          .addDependency("//java/com/google/android/apps/example:example_debug")
          .setJavaInfo(JavaRuleIdeInfo.builder()
                         .addJar(LibraryArtifact.builder()
                                   .setJar(genRoot("javatests/com/google/android/apps/example/example.jar"))
                                   .setRuntimeJar(genRoot("javatests/com/google/android/apps/example/example.jar")))));

    BlazeJavaImportResult result = importWorkspace(
      workspaceRoot,
      ruleMapBuilder,
      projectView
    );
    errorCollector.assertNoIssues();

    assertThat(result.contentEntries).containsExactly(
      BlazeContentEntry.builder("/root/java/com/google/android/apps/example")
        .addSource(BlazeSourceDirectory.builder("/root/java/com/google/android/apps/example")
                     .setPackagePrefix("com.google.android.apps.example")
                     .build())
        .build(),
      BlazeContentEntry.builder("/root/javatests/com/google/android/apps/example")
        .addSource(BlazeSourceDirectory.builder("/root/javatests/com/google/android/apps/example")
                     .setPackagePrefix("com.google.android.apps.example")
                     .setTest(true)
                     .build())
        .build()
    );
  }


  /*
   * Test that the non-android libraries can be imported.
   */
  @Test
  public void testNormalJavaLibraryPackage() {
    ProjectView projectView = ProjectView.builder()
      .put(ListSection.builder(DirectorySection.KEY)
             .add(DirectoryEntry.include(new WorkspacePath("java/com/google/android/apps/example")))
             .add(DirectoryEntry.include(new WorkspacePath("java/com/google/library/something"))))
      .build();

    RuleMapBuilder ruleMapBuilder = RuleMapBuilder.builder()
      .addRule(
        RuleIdeInfo.builder()
          .setLabel("//java/com/google/android/apps/example:example_debug")
          .setBuildFile(sourceRoot("java/com/google/android/apps/example/BUILD"))
          .setKind("android_binary")
          .addSource(sourceRoot("java/com/google/android/apps/example/MainActivity.java"))
          .addSource(sourceRoot("java/com/google/android/apps/example/subdir/SubdirHelper.java"))
          .setJavaInfo(JavaRuleIdeInfo.builder())
          .setAndroidInfo(AndroidRuleIdeInfo.builder()
                            .setManifestFile(sourceRoot("java/com/google/android/apps/example/AndroidManifest.xml"))
                            .addResource(sourceRoot("java/com/google/android/apps/example/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.android.apps.example"))
          .addDependency("//java/com/google/library/something:something")
      )
      .addRule(
        RuleIdeInfo.builder()
          .setLabel("//java/com/google/library/something:something")
          .setBuildFile(sourceRoot("java/com/google/library/something/BUILD"))
          .setKind("java_library")
          .addSource(sourceRoot("java/com/google/library/something/SomeJavaFile.java"))
          .setJavaInfo(JavaRuleIdeInfo.builder()
                         .addJar(LibraryArtifact.builder()
                                   .setJar(genRoot("java/com/google/library/something/something.jar"))
                                   .setRuntimeJar(genRoot("java/com/google/library/something/something.jar")))));

    BlazeJavaImportResult result = importWorkspace(
      workspaceRoot,
      ruleMapBuilder,
      projectView
    );
    errorCollector.assertNoIssues();

    assertThat(result.contentEntries).containsExactly(
      BlazeContentEntry.builder("/root/java/com/google/android/apps/example")
        .addSource(BlazeSourceDirectory.builder("/root/java/com/google/android/apps/example")
                     .setPackagePrefix("com.google.android.apps.example")
                     .build())
        .build(),
      BlazeContentEntry.builder("/root/java/com/google/library/something")
        .addSource(BlazeSourceDirectory.builder("/root/java/com/google/library/something")
                     .setPackagePrefix("com.google.library.something")
                     .build())
        .build()
    );
  }

  @Test
  public void testImportTargetOutputTag() {
    ProjectView projectView = ProjectView.builder()
      .put(ListSection.builder(DirectorySection.KEY)
             .add(DirectoryEntry.include(new WorkspacePath("lib")))
             .add(DirectoryEntry.include(new WorkspacePath("lib2"))))
      .build();

    RuleMapBuilder response = RuleMapBuilder.builder()
      .addRule(
        RuleIdeInfo.builder()
          .setLabel("//lib:lib")
          .setBuildFile(sourceRoot("lib/BUILD"))
          .setKind("java_library")
          .addDependency("//lib2:lib2")
          .setJavaInfo(JavaRuleIdeInfo.builder()
                         .addJar(LibraryArtifact.builder()
                                   .setJar(genRoot("lib/lib.jar"))
                                   .setRuntimeJar(genRoot("lib/lib.jar")))))
      .addRule(
        RuleIdeInfo.builder()
          .setLabel("//lib2:lib2")
          .setBuildFile(sourceRoot("lib2/BUILD"))
          .setKind("java_library")
          .addTag("intellij-import-target-output")
          .setJavaInfo(JavaRuleIdeInfo.builder()
                         .addJar(LibraryArtifact.builder()
                                   .setJar(genRoot("lib2/lib2.jar"))
                                   .setRuntimeJar(genRoot("lib2/lib2.jar")))));

    BlazeJavaImportResult result = importWorkspace(
      workspaceRoot,
      response,
      projectView
    );
    errorCollector.assertNoIssues();
    assertEquals(1, result.libraries.size());
  }

  @Test
  public void testImportAsLibraryTagLegacy() {
    ProjectView projectView = ProjectView.builder()
      .put(ListSection.builder(DirectorySection.KEY)
             .add(DirectoryEntry.include(new WorkspacePath("lib")))
             .add(DirectoryEntry.include(new WorkspacePath("lib2"))))
      .build();

    RuleMapBuilder response = RuleMapBuilder.builder()
      .addRule(
        RuleIdeInfo.builder()
          .setLabel("//lib:lib")
          .setBuildFile(sourceRoot("lib/BUILD"))
          .setKind("java_library")
          .addDependency("//lib2:lib2")
          .setJavaInfo(JavaRuleIdeInfo.builder()
                         .addJar(LibraryArtifact.builder()
                                   .setJar(genRoot("lib/lib.jar"))
                                   .setRuntimeJar(genRoot("lib/lib.jar")))))
      .addRule(
        RuleIdeInfo.builder()
          .setLabel("//lib2:lib2")
          .setBuildFile(sourceRoot("lib2/BUILD"))
          .setKind("java_library")
          .addTag("aswb-import-as-library")
          .setJavaInfo(JavaRuleIdeInfo.builder()
                         .addJar(LibraryArtifact.builder()
                                   .setJar(genRoot("lib2/lib2.jar"))
                                   .setRuntimeJar(genRoot("lib2/lib2.jar")))));

    BlazeJavaImportResult result = importWorkspace(
      workspaceRoot,
      response,
      projectView
    );
    errorCollector.assertNoIssues();

    assertEquals(1, result.libraries.size());
  }

  @Test
  public void testMultipleImportOfJarsGetMerged() {
    ProjectView projectView = ProjectView.builder()
      .put(ListSection.builder(DirectorySection.KEY)
             .add(DirectoryEntry.include(new WorkspacePath("lib"))))
      .build();

    RuleMapBuilder response = RuleMapBuilder.builder()
      .addRule(
        RuleIdeInfo.builder()
          .setLabel("//lib:libsource")
          .setBuildFile(sourceRoot("lib/BUILD"))
          .setKind("java_library")
          .setJavaInfo(JavaRuleIdeInfo.builder())
          .addDependency("//lib:lib0")
          .addDependency("//lib:lib1"))
      .addRule(
        RuleIdeInfo.builder()
          .setLabel("//lib:lib0")
          .setBuildFile(sourceRoot("lib/BUILD"))
          .setKind("java_import")
          .setJavaInfo(JavaRuleIdeInfo.builder()
                         .addJar(LibraryArtifact.builder()
                                   .setJar(sourceRoot("lib/lib.jar"))
                                   .setRuntimeJar(sourceRoot("lib/lib.jar")))))
      .addRule(
        RuleIdeInfo.builder()
          .setLabel("//lib:lib1")
          .setBuildFile(sourceRoot("lib/BUILD"))
          .setKind("java_import")
          .setJavaInfo(JavaRuleIdeInfo.builder()
                         .addJar(LibraryArtifact.builder()
                                   .setJar(sourceRoot("lib/lib.jar"))
                                   .setRuntimeJar(sourceRoot("lib/lib.jar")))));

    BlazeJavaImportResult result = importWorkspace(
      workspaceRoot,
      response,
      projectView
    );
    errorCollector.assertNoIssues();
    assertEquals(1, result.libraries.size()); // The libraries were merged
  }

  @Test
  public void testRuleWithOnlyGeneratedSourcesIsAddedAsLibrary() {
    ProjectView projectView = ProjectView.builder()
      .put(ListSection.builder(DirectorySection.KEY)
             .add(DirectoryEntry.include(new WorkspacePath("import"))))
      .build();

    RuleMapBuilder response = RuleMapBuilder.builder()
      .addRule(
        RuleIdeInfo.builder()
          .setLabel("//import:lib")
          .setBuildFile(sourceRoot("import/BUILD"))
          .setKind("android_library")
          .setJavaInfo(JavaRuleIdeInfo.builder())
          .addDependency("//import:import")
          .addDependency("//import:import_android"))
      .addRule(
        RuleIdeInfo.builder()
          .setLabel("//import:import")
          .setBuildFile(sourceRoot("import/BUILD"))
          .addSource(genRoot("import/GenSource.java"))
          .setKind("java_library")
          .setJavaInfo(JavaRuleIdeInfo.builder()
                         .addJar(LibraryArtifact.builder()
                                   .setJar(genRoot("import/import.jar"))
                                   .setRuntimeJar(genRoot("import/import.jar")))))
      .addRule(
        RuleIdeInfo.builder()
          .setLabel("//import:import_android")
          .setBuildFile(sourceRoot("import/BUILD"))
          .addSource(genRoot("import/GenSource.java"))
          .setKind("android_library")
          .setJavaInfo(JavaRuleIdeInfo.builder()
                         .addJar(LibraryArtifact.builder()
                                   .setJar(genRoot("import/import_android.jar"))
                                   .setRuntimeJar(genRoot("import/import_android.jar")))));

    BlazeJavaImportResult result = importWorkspace(
      workspaceRoot,
      response,
      projectView
    );
    errorCollector.assertNoIssues();

    assertThat(findLibrary(result.libraries, "import.jar")).isNotNull();
    assertThat(findLibrary(result.libraries, "import_android.jar")).isNotNull();
  }

  @Test
  public void testImportTargetOutput() {
    ProjectView projectView = ProjectView.builder()
      .put(ListSection.builder(DirectorySection.KEY)
             .add(DirectoryEntry.include(new WorkspacePath("import"))))
      .put(ListSection.builder(ImportTargetOutputSection.KEY)
             .add(new Label("//import:import")))
      .build();

    RuleMapBuilder response = RuleMapBuilder.builder()
      .addRule(
        RuleIdeInfo.builder()
          .setLabel("//import:lib")
          .setBuildFile(sourceRoot("import/BUILD"))
          .setKind("java_library")
          .setJavaInfo(JavaRuleIdeInfo.builder())
          .addDependency("//import:import"))
      .addRule(
        RuleIdeInfo.builder()
          .setLabel("//import:import")
          .setBuildFile(sourceRoot("import/BUILD"))
          .setKind("java_import")
          .setJavaInfo(JavaRuleIdeInfo.builder()
                         .addJar(LibraryArtifact.builder()
                                   .setJar(genRoot("import/import.jar"))
                                   .setRuntimeJar(genRoot("import/import.jar")))));

    BlazeJavaImportResult result = importWorkspace(
      workspaceRoot,
      response,
      projectView
    );
    errorCollector.assertNoIssues();

    assertThat(result.libraries).isNotEmpty();
  }

  private RuleMapBuilder ruleMapForJdepsSuite() {
    return RuleMapBuilder.builder()
      .addRule(
        RuleIdeInfo.builder()
          .setLabel("//java/com/google/android/apps/example:example_debug")
          .setBuildFile(sourceRoot("java/com/google/android/apps/example/BUILD"))
          .addSource(sourceRoot("java/com/google/android/apps/example/Test.java"))
          .setKind("java_library")
          .setJavaInfo(JavaRuleIdeInfo.builder())
          .addDependency("//thirdparty/a:a"))
      .addRule(
        RuleIdeInfo.builder()
          .setLabel("//thirdparty/a:a")
          .setKind("java_library")
          .setBuildFile(sourceRoot("third_party/a/BUILD"))
          .addDependency("//thirdparty/b:b")
          .setJavaInfo(JavaRuleIdeInfo.builder()
                         .addJar(LibraryArtifact.builder()
                                   .setJar(genRoot("thirdparty/a.jar"))
                                   .setRuntimeJar(genRoot("thirdparty/a.jar"))
                                   .setSourceJar(genRoot("thirdparty/a.srcjar")))))
      .addRule(
        RuleIdeInfo.builder()
          .setLabel("//thirdparty/b:b")
          .setKind("java_library")
          .setBuildFile(sourceRoot("third_party/b/BUILD"))
          .addDependency("//thirdparty/c:c")
          .setJavaInfo(JavaRuleIdeInfo.builder()
                         .addJar(LibraryArtifact.builder()
                                   .setJar(genRoot("thirdparty/b.jar"))
                                   .setRuntimeJar(genRoot("thirdparty/b.jar"))
                                   .setSourceJar(genRoot("thirdparty/b.srcjar")))))
      .addRule(
        RuleIdeInfo.builder()
          .setLabel("//thirdparty/c:c")
          .setKind("java_library")
          .setBuildFile(sourceRoot("third_party/c/BUILD"))
          .setJavaInfo(JavaRuleIdeInfo.builder()
                         .addJar(LibraryArtifact.builder()
                                   .setJar(genRoot("thirdparty/c.jar"))
                                   .setRuntimeJar(genRoot("thirdparty/c.jar"))
                                   .setSourceJar(genRoot("thirdparty/c.srcjar")))));
  }

  @Test
  public void testLibraryDependenciesWithJdepsSet() {
    ProjectView projectView = ProjectView.builder()
      .put(ListSection.builder(DirectorySection.KEY)
             .add(DirectoryEntry.include(new WorkspacePath("java/com/google/android/apps/example")))
             .add(DirectoryEntry.include(new WorkspacePath("javatests/com/google/android/apps/example"))))
      .build();
    RuleMapBuilder ruleMapBuilder = ruleMapForJdepsSuite();
    jdepsMap.put(new Label("//java/com/google/android/apps/example:example_debug"), Lists.newArrayList(
      jdepsPath("thirdparty/a.jar"),
      jdepsPath("thirdparty/c.jar"))
    );

    BlazeJavaImportResult result = importWorkspace(
      workspaceRoot,
      ruleMapBuilder,
      projectView
    );
    assertThat(result.libraries.values().stream().map(BlazeJavaWorkspaceImporterTest::libraryFileName).collect(Collectors.toList()))
      .containsExactly("a.jar", "c.jar");
  }

  @Test
  public void testLibraryDependenciesWithJdepsReportingNothingShouldStillIncludeDirectDepsIfInWorkingSet() {
    ProjectView projectView = ProjectView.builder()
      .put(ListSection.builder(DirectorySection.KEY)
             .add(DirectoryEntry.include(new WorkspacePath("java/com/google/android/apps/example")))
             .add(DirectoryEntry.include(new WorkspacePath("javatests/com/google/android/apps/example"))))
      .build();
    RuleMapBuilder ruleMapBuilder = ruleMapForJdepsSuite();
    workingSet = new JavaWorkingSet(workspaceRoot, new WorkingSet(
      ImmutableList.of(new WorkspacePath("java/com/google/android/apps/example/Test.java")),
      ImmutableList.of(),
      ImmutableList.of()
    ));

    BlazeJavaImportResult result = importWorkspace(
      workspaceRoot,
      ruleMapBuilder,
      projectView
    );
    assertThat(result.libraries.values().stream().map(BlazeJavaWorkspaceImporterTest::libraryFileName).collect(Collectors.toList()))
      .containsExactly("a.jar");
  }

  @Test
  public void testLibraryDependenciesWithJdepsReportingNothingShouldNotIncludeDirectDepsIfNotInWorkingSet() {
    ProjectView projectView = ProjectView.builder()
      .put(ListSection.builder(DirectorySection.KEY)
             .add(DirectoryEntry.include(new WorkspacePath("java/com/google/android/apps/example")))
             .add(DirectoryEntry.include(new WorkspacePath("javatests/com/google/android/apps/example"))))
      .build();
    RuleMapBuilder ruleMapBuilder = ruleMapForJdepsSuite();
    workingSet = new JavaWorkingSet(workspaceRoot, new WorkingSet(
      ImmutableList.of(),
      ImmutableList.of(),
      ImmutableList.of()
    ));

    BlazeJavaImportResult result = importWorkspace(
      workspaceRoot,
      ruleMapBuilder,
      projectView
    );
    assertThat(result.libraries.values().stream().map(BlazeJavaWorkspaceImporterTest::libraryFileName).collect(Collectors.toList()))
      .isEmpty();
  }

  /*
   * Test the exclude_target section
   */
  @Test
  public void testExcludeTarget() {
    ProjectView projectView = ProjectView.builder()
      .put(ListSection.builder(DirectorySection.KEY))
      .put(ListSection.builder(ExcludeTargetSection.KEY)
             .add(new Label("//java/com/google/android/apps/example:example_debug")))
      .build();

    RuleMapBuilder ruleMapBuilder = RuleMapBuilder.builder()
      .addRule(
        RuleIdeInfo.builder()
          .setLabel("//java/com/google/android/apps/example:example_debug")
          .setBuildFile(sourceRoot("java/com/google/android/apps/example/BUILD"))
          .setKind("android_binary")
          .addSource(sourceRoot("java/com/google/android/apps/example/MainActivity.java"))
          .addSource(sourceRoot("java/com/google/android/apps/example/subdir/SubdirHelper.java"))
          .setAndroidInfo(AndroidRuleIdeInfo.builder()
                            .setManifestFile(sourceRoot("java/com/google/android/apps/example/AndroidManifest.xml"))
                            .addResource(sourceRoot("java/com/google/android/apps/example/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.android.apps.example"))
          .addDependency("//java/com/google/library/something:something")
      );

    BlazeJavaImportResult result = importWorkspace(
      workspaceRoot,
      ruleMapBuilder,
      projectView
    );
    errorCollector.assertNoIssues();

    assertThat(result.libraries).isEmpty();
  }

  /**
   * Test legacy proto_library jars, complete with overrides and everything.
   */
  @Test
  public void testLegacyProtoLibraryInfo() {
    ProjectView projectView = ProjectView.builder()
      .put(ListSection.builder(DirectorySection.KEY)
             .add(DirectoryEntry.include(new WorkspacePath("java/com/google/example"))))
      .build();

    RuleMapBuilder ruleMapBuilder = RuleMapBuilder.builder()
      .addRule(
        RuleIdeInfo.builder()
          .setLabel("//java/com/google/example:liba")
          .setBuildFile(sourceRoot("java/com/google/example/BUILD"))
          .setKind("java_library")
          .setJavaInfo(JavaRuleIdeInfo.builder())
          .addDependency("//thirdparty/proto/a:a"))
      .addRule(
        RuleIdeInfo.builder()
          .setLabel("//java/com/google/example:libb")
          .setBuildFile(sourceRoot("java/com/google/example/BUILD"))
          .setKind("java_library")
          .setJavaInfo(JavaRuleIdeInfo.builder())
          .addDependency("//thirdparty/proto/b:b"))
      .addRule(
        RuleIdeInfo.builder()
          .setLabel("//thirdparty/proto/a:a")
          .setBuildFile(sourceRoot("/thirdparty/a/BUILD"))
          .setKind("proto_library")
          .setProtoLibraryLegacyInfo(ProtoLibraryLegacyInfo.builder(ProtoLibraryLegacyInfo.ApiFlavor.IMMUTABLE)
                                       .addJarV1(LibraryArtifact.builder().setJar(genRoot("thirdparty/proto/a/liba-1-ijar.jar")))
                                       .addJarImmutable(LibraryArtifact.builder().setJar(genRoot("thirdparty/proto/a/liba-ijar.jar"))))
          .addDependency("//thirdparty/proto/b:b")
          .addDependency("//thirdparty/proto/c:c"))
      .addRule(
        RuleIdeInfo.builder()
          .setLabel("//thirdparty/proto/b:b")
          .setBuildFile(sourceRoot("/thirdparty/b/BUILD"))
          .setKind("proto_library")
          .setProtoLibraryLegacyInfo(ProtoLibraryLegacyInfo.builder(ProtoLibraryLegacyInfo.ApiFlavor.VERSION_1)
                                       .addJarV1(LibraryArtifact.builder().setJar(genRoot("thirdparty/proto/b/libb-ijar.jar")))
                                       .addJarImmutable(LibraryArtifact.builder().setJar(genRoot("thirdparty/proto/b/libb-2-ijar.jar"))))
          .addDependency("//thirdparty/proto/d:d"))
      .addRule(
        RuleIdeInfo.builder()
          .setLabel("//thirdparty/proto/c:c")
          .setBuildFile(sourceRoot("/thirdparty/c/BUILD"))
          .setKind("proto_library")
          .setProtoLibraryLegacyInfo(ProtoLibraryLegacyInfo.builder(ProtoLibraryLegacyInfo.ApiFlavor.IMMUTABLE)
                                       .addJarV1(LibraryArtifact.builder().setJar(genRoot("thirdparty/proto/c/libc-1-ijar.jar")))
                                       .addJarImmutable(LibraryArtifact.builder().setJar(genRoot("thirdparty/proto/c/libc-ijar.jar"))))
          .addDependency("//thirdparty/proto/d:d"))
      .addRule(
        RuleIdeInfo.builder()
          .setLabel("//thirdparty/proto/d:d")
          .setBuildFile(sourceRoot("/thirdparty/d/BUILD"))
          .setKind("proto_library")
          .setProtoLibraryLegacyInfo(ProtoLibraryLegacyInfo.builder(ProtoLibraryLegacyInfo.ApiFlavor.VERSION_1)
                                       .addJarV1(LibraryArtifact.builder().setJar(genRoot("thirdparty/proto/d/libd-ijar.jar")))
                                       .addJarImmutable(LibraryArtifact.builder().setJar(genRoot("thirdparty/proto/d/libd-2-ijar.jar")))));

    workingSet = new JavaWorkingSet(workspaceRoot, new WorkingSet(ImmutableList.of(), ImmutableList.of(), ImmutableList.of()));

    // First test - make sure that jdeps is working
    jdepsMap.put(new Label("//java/com/google/example:liba"), Lists.newArrayList(jdepsPath("thirdparty/proto/a/liba-ijar.jar")));
    BlazeJavaImportResult result = importWorkspace(
      workspaceRoot,
      ruleMapBuilder,
      projectView
    );
    errorCollector.assertNoIssues();
    assertThat(result.libraries).hasSize(1);
    assertThat(findLibrary(result.libraries, "liba-ijar.jar")).isNotNull();


    // Second test - put everything in the working set, which should expand to the full transitive closure
    workingSet = new JavaWorkingSet(workspaceRoot, new WorkingSet(
      ImmutableList.of(new WorkspacePath("java/com/google/example/BUILD")),
      ImmutableList.of(),
      ImmutableList.of()
    ));

    result = importWorkspace(
      workspaceRoot,
      ruleMapBuilder,
      projectView
    );
    errorCollector.assertNoIssues();

    assertThat(result.libraries).hasSize(6);
    assertThat(findLibrary(result.libraries, "liba-ijar.jar")).isNotNull();
    assertThat(findLibrary(result.libraries, "libb-ijar.jar")).isNotNull();
    assertThat(findLibrary(result.libraries, "libb-2-ijar.jar")).isNotNull();
    assertThat(findLibrary(result.libraries, "libc-ijar.jar")).isNotNull();
    assertThat(findLibrary(result.libraries, "libd-ijar.jar")).isNotNull();
    assertThat(findLibrary(result.libraries, "libd-2-ijar.jar")).isNotNull();
  }

  /*
 * Test that the non-android libraries can be imported.
 */
  @Test
  public void testImporterWorksWithWorkspaceRootDirectoryIncluded() {
    ProjectView projectView = ProjectView.builder()
      .put(ListSection.builder(DirectorySection.KEY)
        .add(DirectoryEntry.include(new WorkspacePath(""))))
      .build();

    RuleMapBuilder ruleMapBuilder = RuleMapBuilder.builder()
      .addRule(
        RuleIdeInfo.builder()
          .setLabel("//java/com/google/android/apps/example:example_debug")
          .setBuildFile(sourceRoot("java/com/google/android/apps/example/BUILD"))
          .setKind("android_binary")
          .addSource(sourceRoot("java/com/google/android/apps/example/MainActivity.java"))
          .addSource(sourceRoot("java/com/google/android/apps/example/subdir/SubdirHelper.java"))
          .setJavaInfo(JavaRuleIdeInfo.builder())
          .setAndroidInfo(AndroidRuleIdeInfo.builder()
            .setManifestFile(sourceRoot("java/com/google/android/apps/example/AndroidManifest.xml"))
            .addResource(sourceRoot("java/com/google/android/apps/example/res"))
            .setGenerateResourceClass(true)
            .setResourceJavaPackage("com.google.android.apps.example"))
          .addDependency("//java/com/google/library/something:something")
      )
      .addRule(
        RuleIdeInfo.builder()
          .setLabel("//java/com/google/library/something:something")
          .setBuildFile(sourceRoot("java/com/google/library/something/BUILD"))
          .setKind("java_library")
          .addSource(sourceRoot("java/com/google/library/something/SomeJavaFile.java"))
          .setJavaInfo(JavaRuleIdeInfo.builder()
            .addJar(LibraryArtifact.builder()
              .setJar(genRoot("java/com/google/library/something/something.jar"))
              .setRuntimeJar(genRoot("java/com/google/library/something/something.jar")))));

    BlazeJavaImportResult result = importWorkspace(
      workspaceRoot,
      ruleMapBuilder,
      projectView
    );
    errorCollector.assertNoIssues();

    assertThat(result.contentEntries).containsExactly(
      BlazeContentEntry.builder("/root")
        .addSource(BlazeSourceDirectory.builder("/root")
          .build())
        .addSource(BlazeSourceDirectory.builder("/root/java")
                     .build())
        .build()
    );
  }

  @Test
  public void testLanguageLevelIsReadFromToolchain() {
    ProjectView projectView = ProjectView.builder()
      .build();

    RuleMapBuilder ruleMapBuilder = RuleMapBuilder.builder()
      .addRule(
        RuleIdeInfo.builder()
          .setLabel("//java/com/google:toolchain")
          .setBuildFile(sourceRoot("java/com/google/BUILD"))
          .setKind("java_toolchain")
          .setJavaToolchainIdeInfo(JavaToolchainIdeInfo.builder()
                                     .setSourceVersion("8")
                                     .setTargetVersion("8")));

    BlazeJavaImportResult result = importWorkspace(
      workspaceRoot,
      ruleMapBuilder,
      projectView
    );
    assertThat(result.sourceVersion).isEqualTo("8");
  }

  /* Utility methods */

  private static String libraryFileName(BlazeLibrary library) {
    return new File(library.getLibraryArtifact().jar.getRelativePath()).getName();
  }

  @Nullable
  private static BlazeLibrary findLibrary(Map<LibraryKey, BlazeLibrary> libraries, String libraryName) {
    for (BlazeLibrary library : libraries.values()) {
      if (library.getLibraryArtifact().jar.getFile().getPath().endsWith(libraryName)) {
        return library;
      }
    }
    return null;
  }

  private ArtifactLocation sourceRoot(String relativePath) {
    return ArtifactLocation.builder()
      .setRootPath(FAKE_WORKSPACE_ROOT)
      .setRelativePath(relativePath)
      .setIsSource(true)
      .build();
  }

  private static ArtifactLocation genRoot(String relativePath) {
    return ArtifactLocation.builder()
      .setRootPath(FAKE_GEN_ROOT)
      .setRootExecutionPathFragment(FAKE_GEN_ROOT_EXECUTION_PATH_FRAGMENT)
      .setRelativePath(relativePath)
      .setIsSource(false)
      .build();
  }

  private static String jdepsPath(String relativePath) {
    return FAKE_GEN_ROOT_EXECUTION_PATH_FRAGMENT + "/" + relativePath;
  }
}
