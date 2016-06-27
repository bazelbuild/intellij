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

import com.google.idea.blaze.android.sync.model.AndroidResourceModule;
import com.google.idea.blaze.android.sync.model.BlazeAndroidImportResult;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.google.idea.blaze.base.async.executor.MockBlazeExecutor;
import com.google.idea.blaze.base.experiments.ExperimentService;
import com.google.idea.blaze.base.experiments.MockExperimentService;
import com.google.idea.blaze.base.ideinfo.*;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
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
import com.google.idea.blaze.java.sync.model.BlazeLibrary;
import com.google.idea.blaze.java.sync.model.LibraryKey;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.File;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;

/**
 * Tests for BlazeAndroidWorkspaceImporter
 */
public class BlazeAndroidWorkspaceImporterTest extends BlazeTestCase {

  private String FAKE_ROOT = "/root";
  private WorkspaceRoot workspaceRoot = new WorkspaceRoot(new File(FAKE_ROOT));

  private static final String FAKE_GEN_ROOT_EXECUTION_PATH_FRAGMENT =
    "blaze-out/gcc-4.X.Y-crosstool-v17-hybrid-grtev3-k8-fastbuild/bin";

  private static final String FAKE_GEN_ROOT =
    "/abs_root/_blaze_user/8093958afcfde6c33d08b621dfaa4e09/root/"
    + FAKE_GEN_ROOT_EXECUTION_PATH_FRAGMENT;

  private static final BlazeImportSettings DUMMY_IMPORT_SETTINGS = new BlazeImportSettings("", "", "", "", "", BuildSystem.Blaze);

  private BlazeContext context;
  private ErrorCollector errorCollector = new ErrorCollector();

  @Override
  protected void initTest(@NotNull Container applicationServices, @NotNull Container projectServices) {
    MockExperimentService mockExperimentService = new MockExperimentService();
    applicationServices.register(ExperimentService.class, mockExperimentService);

    BlazeExecutor blazeExecutor = new MockBlazeExecutor();
    applicationServices.register(BlazeExecutor.class, blazeExecutor);

    projectServices.register(BlazeImportSettingsManager.class, new BlazeImportSettingsManager(project));
    BlazeImportSettingsManager.getInstance(getProject()).setImportSettings(DUMMY_IMPORT_SETTINGS);

    context = new BlazeContext();
    context.addOutputSink(IssueOutput.class, errorCollector);
  }

  BlazeAndroidImportResult importWorkspace(
    WorkspaceRoot workspaceRoot,
    RuleMapBuilder ruleMapBuilder,
    ProjectView projectView) {

    ProjectViewSet projectViewSet = ProjectViewSet.builder().add(projectView).build();

    BlazeAndroidWorkspaceImporter workspaceImporter = new BlazeAndroidWorkspaceImporter(
      project,
      context,
      workspaceRoot,
      projectViewSet,
      ruleMapBuilder.build()
    );

    return workspaceImporter.importWorkspace();
  }

  /**
   * Test that a two packages use the same un-imported android_library
   */
  @Test
  public void testResourceInheritance() {
    ProjectView projectView = ProjectView.builder()
      .put(ListSection.builder(DirectorySection.KEY)
             .add(DirectoryEntry.include(new WorkspacePath("java/com/google/android/apps/example")))
             .add(DirectoryEntry.include(new WorkspacePath("javatests/com/google/android/apps/example"))))
      .build();

    /**
     * Deps are project -> lib0 -> lib1 -> shared
     *          project -> shared
     */

    RuleMapBuilder response = RuleMapBuilder.builder()
      .addRule(
        RuleIdeInfo.builder()
          .setLabel("//java/com/google/android/apps/example/lib0:lib0")
          .setKind("android_library")
          .setBuildFile(sourceRoot("java/com/google/android/apps/example/lib0/BUILD"))
          .addSource(sourceRoot("java/com/google/android/apps/example/lib0/SharedActivity.java"))
          .setAndroidInfo(AndroidRuleIdeInfo.builder()
                            .setManifestFile(sourceRoot("java/com/google/android/apps/example/lib0/AndroidManifest.xml"))
                            .addResource(sourceRoot("java/com/google/android/apps/example/lib0/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.android.apps.example.lib0"))
          .addDependency("//java/com/google/android/apps/example/lib1:lib1")
          .setJavaInfo(JavaRuleIdeInfo.builder()
                         .addJar(LibraryArtifact.builder()
                                   .setJar(genRoot("java/com/google/android/apps/example/lib0/lib0.jar"))
                                   .setRuntimeJar(genRoot("java/com/google/android/apps/example/lib0/lib0.jar")))))
      .addRule(
        RuleIdeInfo.builder()
          .setLabel("//java/com/google/android/apps/example/lib1:lib1")
          .setKind("android_library")
          .setBuildFile(sourceRoot("java/com/google/android/apps/example/lib1/BUILD"))
          .addSource(sourceRoot("java/com/google/android/apps/example/lib1/SharedActivity.java"))
          .setAndroidInfo(AndroidRuleIdeInfo.builder()
                            .setManifestFile(sourceRoot("java/com/google/android/apps/example/lib1/AndroidManifest.xml"))
                            .addResource(sourceRoot("java/com/google/android/apps/example/lib1/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.android.apps.example.lib1"))
          .addDependency("//java/com/google/android/libraries/shared:shared")
          .setJavaInfo(JavaRuleIdeInfo.builder()
                         .addJar(LibraryArtifact.builder()
                                   .setJar(genRoot("java/com/google/android/apps/example/lib1/lib1.jar"))
                                   .setRuntimeJar(genRoot("java/com/google/android/apps/example/lib1/lib1.jar")))))
      .addRule(
        RuleIdeInfo.builder()
          .setLabel("//java/com/google/android/apps/example:example_debug")
          .setKind("android_binary")
          .setBuildFile(sourceRoot("java/com/google/android/apps/example/BUILD"))
          .addSource(sourceRoot("java/com/google/android/apps/example/MainActivity.java"))
          .setAndroidInfo(AndroidRuleIdeInfo.builder()
                            .setManifestFile(sourceRoot("java/com/google/android/apps/example/AndroidManifest.xml"))
                            .addResource(sourceRoot("java/com/google/android/apps/example/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.android.apps.example"))
          .addDependency("//java/com/google/android/apps/example/lib0:lib0")
          .addDependency("//java/com/google/android/libraries/shared:shared")
          .setJavaInfo(JavaRuleIdeInfo.builder()
                         .addJar(LibraryArtifact.builder()
                                   .setJar(genRoot("java/com/google/android/apps/example/example_debug.jar"))
                                   .setRuntimeJar(genRoot("java/com/google/android/apps/example/example_debug.jar")))))
      .addRule(
        RuleIdeInfo.builder()
          .setLabel("//java/com/google/android/libraries/shared:shared")
          .setBuildFile(sourceRoot("java/com/google/android/libraries/shared/BUILD"))
          .setKind("android_library")
          .addSource(sourceRoot("java/com/google/android/libraries/shared/SharedActivity.java"))
          .setAndroidInfo(AndroidRuleIdeInfo.builder()
                            .setManifestFile(sourceRoot("java/com/google/android/libraries/shared/AndroidManifest.xml"))
                            .addResource(sourceRoot("java/com/google/android/libraries/shared/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.android.libraries.shared"))
          .setBuildFile(sourceRoot("java/com/google/android/libraries/shared/BUILD"))
          .setJavaInfo(JavaRuleIdeInfo.builder()
                         .addJar(LibraryArtifact.builder()
                                   .setJar(genRoot("java/com/google/android/libraries/shared.jar"))
                                   .setRuntimeJar(genRoot("java/com/google/android/libraries/shared.jar")))));

    BlazeAndroidImportResult result = importWorkspace(
      workspaceRoot,
      response,
      projectView
    );
    errorCollector.assertNoIssues();

    assertThat(result.androidResourceModules).containsExactly(
      AndroidResourceModule.builder(new Label("//java/com/google/android/apps/example:example_debug"))
        .addResourceAndTransitiveResource(sourceRoot("java/com/google/android/apps/example/res"))
        .addTransitiveResource(sourceRoot("java/com/google/android/apps/example/lib0/res"))
        .addTransitiveResource(sourceRoot("java/com/google/android/apps/example/lib1/res"))
        .addTransitiveResource(sourceRoot("java/com/google/android/libraries/shared/res"))
        .addTransitiveResourceDependency("//java/com/google/android/apps/example/lib0:lib0")
        .addTransitiveResourceDependency("//java/com/google/android/apps/example/lib1:lib1")
        .addTransitiveResourceDependency("//java/com/google/android/libraries/shared:shared")
        .build(),
      AndroidResourceModule.builder(new Label("//java/com/google/android/apps/example/lib0:lib0"))
        .addResourceAndTransitiveResource(sourceRoot("java/com/google/android/apps/example/lib0/res"))
        .addTransitiveResource(sourceRoot("java/com/google/android/apps/example/lib1/res"))
        .addTransitiveResource(sourceRoot("java/com/google/android/libraries/shared/res"))
        .addTransitiveResourceDependency("//java/com/google/android/apps/example/lib1:lib1")
        .addTransitiveResourceDependency("//java/com/google/android/libraries/shared:shared")
        .build(),
      AndroidResourceModule.builder(new Label("//java/com/google/android/apps/example/lib1:lib1"))
        .addResourceAndTransitiveResource(sourceRoot("java/com/google/android/apps/example/lib1/res"))
        .addTransitiveResource(sourceRoot("java/com/google/android/libraries/shared/res"))
        .addTransitiveResourceDependency("//java/com/google/android/libraries/shared:shared")
        .build()
    );
  }

  /**
   * Test adding empty resource modules as jars.
   */
  @Test
  public void testEmptyResourceModuleIsAddedAsJar() {
    ProjectView projectView = ProjectView.builder()
      .put(ListSection.builder(DirectorySection.KEY)
             .add(DirectoryEntry.include(new WorkspacePath("java/com/google/android/apps/example")))
             .add(DirectoryEntry.include(new WorkspacePath("javatests/com/google/android/apps/example"))))
      .build();

    /**
     * Deps are project -> lib0 (no res) -> lib1 (has res)
     *                                    \
     *                                     -> lib2 (has res)
     */
    RuleMapBuilder response = RuleMapBuilder.builder()
      .addRule(
        RuleIdeInfo.builder()
          .setLabel("//java/com/google/android/apps/example/lib0:lib0")
          .setKind("android_library")
          .setBuildFile(sourceRoot("java/com/google/android/apps/example/lib0/BUILD"))
          .addSource(sourceRoot("java/com/google/android/apps/example/lib0/SharedActivity.java"))
          .setAndroidInfo(AndroidRuleIdeInfo.builder()
                            .setManifestFile(sourceRoot("java/com/google/android/apps/example/lib0/AndroidManifest.xml"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.android.apps.example.lib0")
                            .setResourceJar(LibraryArtifact.builder()
                                              .setJar(genRoot("java/com/google/android/apps/example/lib0/lib0_resources.jar"))
                                              .setRuntimeJar(genRoot("java/com/google/android/apps/example/lib0/lib0_resources.jar"))))
          .addDependency("//java/com/google/android/apps/example/lib1:lib1")
          .addDependency("//java/com/google/android/apps/example/lib2:lib2")
          .setJavaInfo(JavaRuleIdeInfo.builder()
                         .addJar(LibraryArtifact.builder()
                                   .setJar(genRoot("java/com/google/android/apps/example/lib0/lib0.jar"))
                                   .setRuntimeJar(genRoot("java/com/google/android/apps/example/lib0/lib0.jar")))
                         .addJar(LibraryArtifact.builder()
                                   .setJar(genRoot("java/com/google/android/apps/example/lib0/lib0_resources.jar"))
                                   .setRuntimeJar(genRoot("java/com/google/android/apps/example/lib0/lib0_resources.jar")))))
      .addRule(
        RuleIdeInfo.builder()
          .setLabel("//java/com/google/android/apps/example/lib1:lib1")
          .setKind("android_library")
          .setBuildFile(sourceRoot("java/com/google/android/apps/example/lib1/BUILD"))
          .addSource(sourceRoot("java/com/google/android/apps/example/lib1/SharedActivity.java"))
          .setAndroidInfo(AndroidRuleIdeInfo.builder()
                            .setManifestFile(sourceRoot("java/com/google/android/apps/example/lib1/AndroidManifest.xml"))
                            .addResource(sourceRoot("java/com/google/android/apps/example/lib1/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.android.apps.example.lib1")
                            .setResourceJar(LibraryArtifact.builder()
                                              .setJar(genRoot("java/com/google/android/apps/example/lib1/li11_resources.jar"))
                                              .setRuntimeJar(genRoot("java/com/google/android/apps/example/lib1/lib1_resources.jar"))))
          .setJavaInfo(JavaRuleIdeInfo.builder()
                         .addJar(LibraryArtifact.builder()
                                   .setJar(genRoot("java/com/google/android/apps/example/lib1/lib1.jar"))
                                   .setRuntimeJar(genRoot("java/com/google/android/apps/example/lib1/lib1.jar")))
                         .addJar(LibraryArtifact.builder()
                                   .setJar(genRoot("java/com/google/android/apps/example/lib1/lib1_resources.jar"))
                                   .setRuntimeJar(genRoot("java/com/google/android/apps/example/lib1/lib1_resources.jar")))))
      .addRule(
        RuleIdeInfo.builder()
          .setLabel("//java/com/google/android/apps/example/lib2:lib2")
          .setBuildFile(sourceRoot("java/com/google/android/apps/example/lib2/BUILD"))
          .setKind("android_library")
          .addSource(sourceRoot("java/com/google/android/apps/example/lib2/SharedActivity.java"))
          .setAndroidInfo(AndroidRuleIdeInfo.builder()
                            .setManifestFile(sourceRoot("java/com/google/android/apps/example/lib2/AndroidManifest.xml"))
                            .addResource(sourceRoot("java/com/google/android/apps/example/lib2/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.android.libraries.example.lib2")
                            .setResourceJar(LibraryArtifact.builder()
                                              .setJar(genRoot("java/com/google/android/apps/example/lib2/lib2_resources.jar"))
                                              .setRuntimeJar(genRoot("java/com/google/android/apps/example/lib2/lib2_resources.jar"))))
          .setBuildFile(sourceRoot("java/com/google/android/apps/example/lib2/BUILD"))
          .setJavaInfo(JavaRuleIdeInfo.builder()
                         .addJar(LibraryArtifact.builder()
                                   .setJar(genRoot("java/com/google/android/apps/example/lib2/lib2.jar"))
                                   .setRuntimeJar(genRoot("java/com/google/android/apps/example/lib2/lib2.jar")))
                         .addJar(LibraryArtifact.builder()
                                   .setJar(genRoot("java/com/google/android/apps/example/lib2/lib2_resources.jar"))
                                   .setRuntimeJar(genRoot("java/com/google/android/apps/example/lib2/lib2_resources.jar")))))
      .addRule(
        RuleIdeInfo.builder()
          .setLabel("//java/com/google/android/apps/example:example_debug")
          .setKind("android_binary")
          .setBuildFile(sourceRoot("java/com/google/android/apps/example/BUILD"))
          .addSource(sourceRoot("java/com/google/android/apps/example/MainActivity.java"))
          .setAndroidInfo(AndroidRuleIdeInfo.builder()
                            .setManifestFile(sourceRoot("java/com/google/android/apps/example/AndroidManifest.xml"))
                            .addResource(sourceRoot("java/com/google/android/apps/example/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.android.apps.example"))
          .addDependency("//java/com/google/android/apps/example/lib0:lib0")
          .setJavaInfo(JavaRuleIdeInfo.builder()
                         .addJar(LibraryArtifact.builder()
                                   .setJar(genRoot("java/com/google/android/apps/example/example_debug.jar"))
                                   .setRuntimeJar(genRoot("java/com/google/android/apps/example/example_debug.jar")))));

    BlazeAndroidImportResult result = importWorkspace(
      workspaceRoot,
      response,
      projectView
    );
    errorCollector.assertNoIssues();

    assertThat(result.androidResourceModules).containsExactly(
      AndroidResourceModule.builder(new Label("//java/com/google/android/apps/example:example_debug"))
        .addResourceAndTransitiveResource(sourceRoot("java/com/google/android/apps/example/res"))
        .addTransitiveResource(sourceRoot("java/com/google/android/apps/example/lib1/res"))
        .addTransitiveResource(sourceRoot("java/com/google/android/apps/example/lib2/res"))
        .addTransitiveResourceDependency("//java/com/google/android/apps/example/lib0:lib0")
        .addTransitiveResourceDependency("//java/com/google/android/apps/example/lib1:lib1")
        .addTransitiveResourceDependency("//java/com/google/android/apps/example/lib2:lib2")
        .build(),
      AndroidResourceModule.builder(new Label("//java/com/google/android/apps/example/lib1:lib1"))
        .addResourceAndTransitiveResource(sourceRoot("java/com/google/android/apps/example/lib1/res"))
        .build(),
      AndroidResourceModule.builder(new Label("//java/com/google/android/apps/example/lib2:lib2"))
        .addResourceAndTransitiveResource(sourceRoot("java/com/google/android/apps/example/lib2/res"))
        .build()
    );

    assertEquals(1, result.libraries.size());
    BlazeLibrary library = result.libraries.iterator().next();
    assertEquals("lib0_resources.jar", library.getLibraryArtifact().jar.getFile().getName());
  }

  @Test
  public void testIdlClassJarIsAddedAsLibrary() {
    ProjectView projectView = ProjectView.builder()
      .put(ListSection.builder(DirectorySection.KEY)
             .add(DirectoryEntry.include(new WorkspacePath("example"))))
      .build();

    RuleMapBuilder ruleMapBuilder = RuleMapBuilder.builder()
      .addRule(
        RuleIdeInfo.builder()
          .setLabel("//example:lib")
          .setBuildFile(sourceRoot("example/BUILD"))
          .setKind("android_binary")
          .addSource(sourceRoot("example/MainActivity.java"))
          .setAndroidInfo(AndroidRuleIdeInfo.builder()
                            .setResourceJavaPackage("example")
                            .setIdlJar(LibraryArtifact.builder()
                                         .setJar(genRoot("example/libidl.jar"))
                                         .setSourceJar(genRoot("example/libidl.srcjar"))
                                         .build())
                            .setHasIdlSources(true)));

    BlazeAndroidImportResult result = importWorkspace(
      workspaceRoot,
      ruleMapBuilder,
      projectView
    );
    errorCollector.assertNoIssues();

    assertEquals(1, result.libraries.size());

    BlazeLibrary library = result.libraries.iterator().next();
    assertEquals("libidl.jar", library.getLibraryArtifact().jar.getFile().getName());
    assertEquals("libidl.srcjar", library.getLibraryArtifact().sourceJar.getFile().getName());
  }

  @Test
  public void testAndroidResourceImport() {
    ProjectView projectView = ProjectView.builder()
      .put(ListSection.builder(DirectorySection.KEY)
             .add(DirectoryEntry.include(new WorkspacePath("java/com/google/android/example"))))
      .build();

    RuleMapBuilder ruleMapBuilder = RuleMapBuilder.builder()
      .addRule(
        RuleIdeInfo.builder()
          .setLabel("//java/com/google/android/example:lib")
          .setBuildFile(sourceRoot("java/com/google/android/example/BUILD"))
          .setKind("android_library")
          .setAndroidInfo(AndroidRuleIdeInfo.builder()
                            .setLegacyResources(new Label("//java/com/google/android/example:resources"))
                            .setManifestFile(sourceRoot("java/com/google/android/example/AndroidManifest.xml"))
                            .addResource(sourceRoot("java/com/google/android/example/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.android.example"))
        .build())
      .addRule(
        RuleIdeInfo.builder()
          .setLabel("//java/com/google/android/example:resources")
          .setBuildFile(sourceRoot("java/com/google/android/example/BUILD"))
          .setKind("android_resources")
          .setAndroidInfo(AndroidRuleIdeInfo.builder()
                            .setManifestFile(sourceRoot("java/com/google/android/example/AndroidManifest.xml"))
                            .addResource(sourceRoot("java/com/google/android/example/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.android.example"))
          .build());

    BlazeAndroidImportResult result = importWorkspace(
      workspaceRoot,
      ruleMapBuilder,
      projectView
    );
    errorCollector.assertNoIssues();
    assertThat(result.androidResourceModules).containsExactly(
      AndroidResourceModule.builder(new Label("//java/com/google/android/example:resources"))
        .addResourceAndTransitiveResource(sourceRoot("java/com/google/android/example/res"))
        .build()
    );
  }

  @Test
  public void testResourceImportOutsideSourceFilterIsAddedToResourceLibrary() {
    ProjectView projectView = ProjectView.builder()
      .put(ListSection.builder(DirectorySection.KEY)
             .add(DirectoryEntry.include(new WorkspacePath("java/com/google/android/example"))))
      .build();

    RuleMapBuilder ruleMapBuilder = RuleMapBuilder.builder()
      .addRule(
        RuleIdeInfo.builder()
          .setLabel("//java/com/google/android/example:lib")
          .setBuildFile(sourceRoot("java/com/google/android/example/BUILD"))
          .setKind("android_library")
          .setAndroidInfo(AndroidRuleIdeInfo.builder()
                            .setManifestFile(sourceRoot("java/com/google/android/example/AndroidManifest.xml"))
                            .addResource(sourceRoot("java/com/google/android/example/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.android.example"))
          .addDependency("//java/com/google/android/example2:resources")
          .build())
      .addRule(
        RuleIdeInfo.builder()
          .setLabel("//java/com/google/android/example2:resources")
          .setBuildFile(sourceRoot("java/com/google/android/example2/BUILD"))
          .setKind("android_library")
          .setAndroidInfo(AndroidRuleIdeInfo.builder()
                            .setManifestFile(sourceRoot("java/com/google/android/example2/AndroidManifest.xml"))
                            .addResource(sourceRoot("java/com/google/android/example2/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.android.example2"))
          .build());

    BlazeAndroidImportResult result = importWorkspace(
      workspaceRoot,
      ruleMapBuilder,
      projectView
    );
    errorCollector.assertNoIssues();
    BlazeLibrary library = result.libraries.stream()
      .filter(item -> item.getKey().equals(LibraryKey.forResourceLibrary()))
      .findAny()
      .orElse(null);
    assertThat(library).isNotNull();
    assertThat(library.getSources()).containsExactly(
      new File("/root/java/com/google/android/example2/res")
    );
  }

  @Test
  public void testConflictingResourceRClasses() {
    ProjectView projectView = ProjectView.builder()
      .put(ListSection.builder(DirectorySection.KEY)
             .add(DirectoryEntry.include(new WorkspacePath("java/com/google/android/example"))))
      .build();

    RuleMapBuilder ruleMapBuilder = RuleMapBuilder.builder()
      .addRule(
        RuleIdeInfo.builder()
          .setLabel("//java/com/google/android/example:lib")
          .setBuildFile(sourceRoot("java/com/google/android/example/BUILD"))
          .setKind("android_library")
          .setAndroidInfo(AndroidRuleIdeInfo.builder()
                            .setManifestFile(sourceRoot("java/com/google/android/example/AndroidManifest.xml"))
                            .addResource(sourceRoot("java/com/google/android/example/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.android.example"))
          .addDependency("//java/com/google/android/example2:resources")
          .build())
      .addRule(
        RuleIdeInfo.builder()
          .setLabel("//java/com/google/android/example:lib2")
          .setBuildFile(sourceRoot("java/com/google/android/example2/BUILD"))
          .setKind("android_library")
          .setAndroidInfo(AndroidRuleIdeInfo.builder()
                            .setManifestFile(sourceRoot("java/com/google/android/example2/AndroidManifest.xml"))
                            .addResource(sourceRoot("java/com/google/android/example/res2"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.android.example"))
          .build());

    BlazeAndroidImportResult result = importWorkspace(
      workspaceRoot,
      ruleMapBuilder,
      projectView
    );
    errorCollector.assertIssueContaining("Multiple R classes generated");

    assertThat(result.androidResourceModules).containsExactly(
      AndroidResourceModule.builder(new Label("//java/com/google/android/example:lib"))
        .addResourceAndTransitiveResource(sourceRoot("java/com/google/android/example/res"))
      .build()
    );
  }

  private ArtifactLocation sourceRoot(String relativePath) {
    return ArtifactLocation.builder()
      .setRootPath(FAKE_ROOT)
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
}
