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

import com.google.common.collect.Lists;
import com.google.idea.blaze.android.sync.BlazeAndroidJavaSyncAugmenter;
import com.google.idea.blaze.android.sync.model.AndroidResourceModule;
import com.google.idea.blaze.android.sync.model.BlazeAndroidImportResult;
import com.google.idea.blaze.android.sync.model.BlazeResourceLibrary;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.google.idea.blaze.base.async.executor.MockBlazeExecutor;
import com.google.idea.blaze.base.ideinfo.AndroidRuleIdeInfo;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.JavaRuleIdeInfo;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.ideinfo.RuleIdeInfo;
import com.google.idea.blaze.base.ideinfo.RuleKey;
import com.google.idea.blaze.base.ideinfo.RuleMap;
import com.google.idea.blaze.base.ideinfo.RuleMapBuilder;
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
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
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

  private static final BlazeImportSettings DUMMY_IMPORT_SETTINGS =
      new BlazeImportSettings("", "", "", "", "", BuildSystem.Blaze);

  private BlazeContext context;
  private ErrorCollector errorCollector = new ErrorCollector();

  @Override
  protected void initTest(
      @NotNull Container applicationServices, @NotNull Container projectServices) {
    MockExperimentService mockExperimentService = new MockExperimentService();
    applicationServices.register(ExperimentService.class, mockExperimentService);

    BlazeExecutor blazeExecutor = new MockBlazeExecutor();
    applicationServices.register(BlazeExecutor.class, blazeExecutor);

    projectServices.register(
        BlazeImportSettingsManager.class, new BlazeImportSettingsManager(project));
    BlazeImportSettingsManager.getInstance(getProject()).setImportSettings(DUMMY_IMPORT_SETTINGS);

    context = new BlazeContext();
    context.addOutputSink(IssueOutput.class, errorCollector);
  }

  BlazeAndroidImportResult importWorkspace(
      WorkspaceRoot workspaceRoot, RuleMapBuilder ruleMapBuilder, ProjectView projectView) {

    ProjectViewSet projectViewSet = ProjectViewSet.builder().add(projectView).build();

    BlazeAndroidWorkspaceImporter workspaceImporter =
        new BlazeAndroidWorkspaceImporter(
            project, context, workspaceRoot, projectViewSet, ruleMapBuilder.build());

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
    RuleMapBuilder ruleMapBuilder =
        RuleMapBuilder.builder()
            .addRule(
                RuleIdeInfo.builder()
                    .setLabel("//java/apps/example/lib0:lib0")
                    .setKind("android_library")
                    .setBuildFile(source("java/apps/example/lib0/BUILD"))
                    .addSource(source("java/apps/example/lib0/SharedActivity.java"))
                    .setAndroidInfo(
                        AndroidRuleIdeInfo.builder()
                            .setManifestFile(source("java/apps/example/lib0/AndroidManifest.xml"))
                            .addResource(source("java/apps/example/lib0/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.android.apps.example.lib0"))
                    .addDependency("//java/apps/example/lib1:lib1")
                    .setJavaInfo(
                        JavaRuleIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("java/apps/example/lib0/lib0.jar"))
                                    .setClassJar(gen("java/apps/example/lib0/lib0.jar")))))
            .addRule(
                RuleIdeInfo.builder()
                    .setLabel("//java/apps/example/lib1:lib1")
                    .setKind("android_library")
                    .setBuildFile(source("java/apps/example/lib1/BUILD"))
                    .addSource(source("java/apps/example/lib1/SharedActivity.java"))
                    .setAndroidInfo(
                        AndroidRuleIdeInfo.builder()
                            .setManifestFile(source("java/apps/example/lib1/AndroidManifest.xml"))
                            .addResource(source("java/apps/example/lib1/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.android.apps.example.lib1"))
                    .addDependency("//java/libraries/shared:shared")
                    .setJavaInfo(
                        JavaRuleIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("java/apps/example/lib1/lib1.jar"))
                                    .setClassJar(gen("java/apps/example/lib1/lib1.jar")))))
            .addRule(
                RuleIdeInfo.builder()
                    .setLabel("//java/apps/example:example_debug")
                    .setKind("android_binary")
                    .setBuildFile(source("java/apps/example/BUILD"))
                    .addSource(source("java/apps/example/MainActivity.java"))
                    .setAndroidInfo(
                        AndroidRuleIdeInfo.builder()
                            .setManifestFile(source("java/apps/example/AndroidManifest.xml"))
                            .addResource(source("java/apps/example/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.android.apps.example"))
                    .addDependency("//java/apps/example/lib0:lib0")
                    .addDependency("//java/libraries/shared:shared")
                    .setJavaInfo(
                        JavaRuleIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("java/apps/example/example_debug.jar"))
                                    .setClassJar(gen("java/apps/example/example_debug.jar")))))
            .addRule(
                RuleIdeInfo.builder()
                    .setLabel("//java/libraries/shared:shared")
                    .setBuildFile(source("java/libraries/shared/BUILD"))
                    .setKind("android_library")
                    .addSource(source("java/libraries/shared/SharedActivity.java"))
                    .setAndroidInfo(
                        AndroidRuleIdeInfo.builder()
                            .setManifestFile(source("java/libraries/shared/AndroidManifest.xml"))
                            .addResource(source("java/libraries/shared/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.android.libraries.shared"))
                    .setBuildFile(source("java/libraries/shared/BUILD"))
                    .setJavaInfo(
                        JavaRuleIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("java/libraries/shared.jar"))
                                    .setClassJar(gen("java/libraries/shared.jar")))));

    BlazeAndroidImportResult result = importWorkspace(workspaceRoot, ruleMapBuilder, projectView);
    errorCollector.assertNoIssues();

    assertThat(result.androidResourceModules)
        .containsExactly(
            AndroidResourceModule.builder(
                    RuleKey.forPlainTarget(new Label("//java/apps/example:example_debug")))
                .addResourceAndTransitiveResource(source("java/apps/example/res"))
                .addTransitiveResource(source("java/apps/example/lib0/res"))
                .addTransitiveResource(source("java/apps/example/lib1/res"))
                .addTransitiveResource(source("java/libraries/shared/res"))
                .addTransitiveResourceDependency("//java/apps/example/lib0:lib0")
                .addTransitiveResourceDependency("//java/apps/example/lib1:lib1")
                .addTransitiveResourceDependency("//java/libraries/shared:shared")
                .build(),
            AndroidResourceModule.builder(
                    RuleKey.forPlainTarget(new Label("//java/apps/example/lib0:lib0")))
                .addResourceAndTransitiveResource(source("java/apps/example/lib0/res"))
                .addTransitiveResource(source("java/apps/example/lib1/res"))
                .addTransitiveResource(source("java/libraries/shared/res"))
                .addTransitiveResourceDependency("//java/apps/example/lib1:lib1")
                .addTransitiveResourceDependency("//java/libraries/shared:shared")
                .build(),
            AndroidResourceModule.builder(
                    RuleKey.forPlainTarget(new Label("//java/apps/example/lib1:lib1")))
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
    RuleMapBuilder response =
        RuleMapBuilder.builder()
            .addRule(
                RuleIdeInfo.builder()
                    .setLabel("//java/apps/example/lib0:lib0")
                    .setKind("android_library")
                    .setBuildFile(source("java/apps/example/lib0/BUILD"))
                    .addSource(source("java/apps/example/lib0/SharedActivity.java"))
                    .setAndroidInfo(
                        AndroidRuleIdeInfo.builder()
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
                        JavaRuleIdeInfo.builder()
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
            .addRule(
                RuleIdeInfo.builder()
                    .setLabel("//java/apps/example/lib1:lib1")
                    .setKind("android_library")
                    .setBuildFile(source("java/apps/example/lib1/BUILD"))
                    .addSource(source("java/apps/example/lib1/SharedActivity.java"))
                    .setAndroidInfo(
                        AndroidRuleIdeInfo.builder()
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
                        JavaRuleIdeInfo.builder()
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
            .addRule(
                RuleIdeInfo.builder()
                    .setLabel("//java/apps/example/lib2:lib2")
                    .setBuildFile(source("java/apps/example/lib2/BUILD"))
                    .setKind("android_library")
                    .addSource(source("java/apps/example/lib2/SharedActivity.java"))
                    .setAndroidInfo(
                        AndroidRuleIdeInfo.builder()
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
                        JavaRuleIdeInfo.builder()
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
            .addRule(
                RuleIdeInfo.builder()
                    .setLabel("//java/apps/example:example_debug")
                    .setKind("android_binary")
                    .setBuildFile(source("java/apps/example/BUILD"))
                    .addSource(source("java/apps/example/MainActivity.java"))
                    .setAndroidInfo(
                        AndroidRuleIdeInfo.builder()
                            .setManifestFile(source("java/apps/example/AndroidManifest.xml"))
                            .addResource(source("java/apps/example/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.android.apps.example"))
                    .addDependency("//java/apps/example/lib0:lib0")
                    .setJavaInfo(
                        JavaRuleIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("java/apps/example/example_debug.jar"))
                                    .setClassJar(gen("java/apps/example/example_debug.jar")))));

    RuleMap ruleMap = response.build();
    BlazeAndroidJavaSyncAugmenter syncAugmenter = new BlazeAndroidJavaSyncAugmenter();
    List<BlazeJarLibrary> jars = Lists.newArrayList();
    List<BlazeJarLibrary> genJars = Lists.newArrayList();
    ImportRoots importRoots =
        ImportRoots.builder(workspaceRoot, BuildSystem.Blaze)
            .add(ProjectViewSet.builder().add(projectView).build())
            .build();
    for (RuleIdeInfo rule : ruleMap.rules()) {
      if (importRoots.importAsSource(rule.label)) {
        syncAugmenter.addJarsForSourceRule(rule, jars, genJars);
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

    RuleMapBuilder ruleMapBuilder =
        RuleMapBuilder.builder()
            .addRule(
                RuleIdeInfo.builder()
                    .setLabel("//example:lib")
                    .setBuildFile(source("example/BUILD"))
                    .setKind("android_binary")
                    .addSource(source("example/MainActivity.java"))
                    .setAndroidInfo(
                        AndroidRuleIdeInfo.builder()
                            .setResourceJavaPackage("example")
                            .setIdlJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("example/libidl.jar"))
                                    .setSourceJar(gen("example/libidl.srcjar"))
                                    .build())
                            .setHasIdlSources(true)));

    RuleMap ruleMap = ruleMapBuilder.build();
    BlazeAndroidJavaSyncAugmenter syncAugmenter = new BlazeAndroidJavaSyncAugmenter();
    List<BlazeJarLibrary> jars = Lists.newArrayList();
    List<BlazeJarLibrary> genJars = Lists.newArrayList();
    ImportRoots importRoots =
        ImportRoots.builder(workspaceRoot, BuildSystem.Blaze)
            .add(ProjectViewSet.builder().add(projectView).build())
            .build();
    for (RuleIdeInfo rule : ruleMap.rules()) {
      if (importRoots.importAsSource(rule.label)) {
        syncAugmenter.addJarsForSourceRule(rule, jars, genJars);
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

    RuleMapBuilder ruleMapBuilder =
        RuleMapBuilder.builder()
            .addRule(
                RuleIdeInfo.builder()
                    .setLabel("//java/example:lib")
                    .setBuildFile(source("java/example/BUILD"))
                    .setKind("android_library")
                    .setAndroidInfo(
                        AndroidRuleIdeInfo.builder()
                            .setLegacyResources(new Label("//java/example:resources"))
                            .setManifestFile(source("java/example/AndroidManifest.xml"))
                            .addResource(source("java/example/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.android.example"))
                    .build())
            .addRule(
                RuleIdeInfo.builder()
                    .setLabel("//java/example:resources")
                    .setBuildFile(source("java/example/BUILD"))
                    .setKind("android_resources")
                    .setAndroidInfo(
                        AndroidRuleIdeInfo.builder()
                            .setManifestFile(source("java/example/AndroidManifest.xml"))
                            .addResource(source("java/example/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.android.example"))
                    .build());

    BlazeAndroidImportResult result = importWorkspace(workspaceRoot, ruleMapBuilder, projectView);
    errorCollector.assertNoIssues();
    assertThat(result.androidResourceModules)
        .containsExactly(
            AndroidResourceModule.builder(
                    RuleKey.forPlainTarget(new Label("//java/example:resources")))
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

    RuleMapBuilder ruleMapBuilder =
        RuleMapBuilder.builder()
            .addRule(
                RuleIdeInfo.builder()
                    .setLabel("//java/example:lib")
                    .setBuildFile(source("java/example/BUILD"))
                    .setKind("android_library")
                    .setAndroidInfo(
                        AndroidRuleIdeInfo.builder()
                            .setManifestFile(source("java/example/AndroidManifest.xml"))
                            .addResource(source("java/example/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.android.example"))
                    .addDependency("//java/example2:resources")
                    .build())
            .addRule(
                RuleIdeInfo.builder()
                    .setLabel("//java/example2:resources")
                    .setBuildFile(source("java/example2/BUILD"))
                    .setKind("android_library")
                    .setAndroidInfo(
                        AndroidRuleIdeInfo.builder()
                            .setManifestFile(source("java/example2/AndroidManifest.xml"))
                            .addResource(source("java/example2/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.android.example2"))
                    .build());

    BlazeAndroidImportResult result = importWorkspace(workspaceRoot, ruleMapBuilder, projectView);
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

    RuleMapBuilder ruleMapBuilder =
        RuleMapBuilder.builder()
            .addRule(
                RuleIdeInfo.builder()
                    .setLabel("//java/example:lib")
                    .setBuildFile(source("java/example/BUILD"))
                    .setKind("android_library")
                    .setAndroidInfo(
                        AndroidRuleIdeInfo.builder()
                            .setManifestFile(source("java/example/AndroidManifest.xml"))
                            .addResource(source("java/example/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.android.example"))
                    .addDependency("//java/example2:resources")
                    .build())
            .addRule(
                RuleIdeInfo.builder()
                    .setLabel("//java/example:lib2")
                    .setBuildFile(source("java/example2/BUILD"))
                    .setKind("android_library")
                    .setAndroidInfo(
                        AndroidRuleIdeInfo.builder()
                            .setManifestFile(source("java/example2/AndroidManifest.xml"))
                            .addResource(source("java/example/res2"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.android.example"))
                    .build());

    BlazeAndroidImportResult result = importWorkspace(workspaceRoot, ruleMapBuilder, projectView);
    errorCollector.assertIssueContaining("Multiple R classes generated");

    assertThat(result.androidResourceModules)
        .containsExactly(
            AndroidResourceModule.builder(RuleKey.forPlainTarget(new Label("//java/example:lib")))
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

    RuleMapBuilder ruleMapBuilder =
        RuleMapBuilder.builder()
            .addRule(
                RuleIdeInfo.builder()
                    .setLabel("//java/example:lib")
                    .setBuildFile(source("java/example/BUILD"))
                    .setKind("android_library")
                    .setAndroidInfo(
                        AndroidRuleIdeInfo.builder()
                            .setManifestFile(source("java/example/AndroidManifest.xml"))
                            .addResource(source("java/example/res"))
                            .addResource(gen("java/example/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.android.example"))
                    .build());

    importWorkspace(workspaceRoot, ruleMapBuilder, projectView);
    errorCollector.assertIssueContaining("Dropping generated resource");
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
