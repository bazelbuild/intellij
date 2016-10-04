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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.google.idea.blaze.base.async.executor.MockBlazeExecutor;
import com.google.idea.blaze.base.ideinfo.AndroidRuleIdeInfo;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.JavaRuleIdeInfo;
import com.google.idea.blaze.base.ideinfo.JavaToolchainIdeInfo;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.ideinfo.ProtoLibraryLegacyInfo;
import com.google.idea.blaze.base.ideinfo.RuleIdeInfo;
import com.google.idea.blaze.base.ideinfo.RuleMapBuilder;
import com.google.idea.blaze.base.ideinfo.Tags;
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.prefetch.MockPrefetchService;
import com.google.idea.blaze.base.prefetch.PrefetchService;
import com.google.idea.blaze.base.projectview.ProjectView;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.Glob;
import com.google.idea.blaze.base.projectview.section.ListSection;
import com.google.idea.blaze.base.projectview.section.sections.DirectoryEntry;
import com.google.idea.blaze.base.projectview.section.sections.DirectorySection;
import com.google.idea.blaze.base.projectview.section.sections.ExcludeTargetSection;
import com.google.idea.blaze.base.projectview.section.sections.ImportTargetOutputSection;
import com.google.idea.blaze.base.projectview.section.sections.TestSourceSection;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.ErrorCollector;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.settings.Blaze.BuildSystem;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.sync.workspace.BlazeRoots;
import com.google.idea.blaze.base.sync.workspace.WorkingSet;
import com.google.idea.blaze.java.sync.BlazeJavaSyncAugmenter;
import com.google.idea.blaze.java.sync.jdeps.JdepsMap;
import com.google.idea.blaze.java.sync.model.BlazeContentEntry;
import com.google.idea.blaze.java.sync.model.BlazeJarLibrary;
import com.google.idea.blaze.java.sync.model.BlazeJavaImportResult;
import com.google.idea.blaze.java.sync.model.BlazeSourceDirectory;
import com.google.idea.blaze.java.sync.model.LibraryKey;
import com.google.idea.blaze.java.sync.source.JavaSourcePackageReader;
import com.google.idea.blaze.java.sync.source.PackageManifestReader;
import com.google.idea.blaze.java.sync.source.SourceArtifact;
import com.google.idea.blaze.java.sync.workingset.JavaWorkingSet;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for BlazeJavaWorkspaceImporter */
@RunWith(JUnit4.class)
public class BlazeJavaWorkspaceImporterTest extends BlazeTestCase {

  private static final String FAKE_WORKSPACE_ROOT = "/root";
  private WorkspaceRoot workspaceRoot = new WorkspaceRoot(new File(FAKE_WORKSPACE_ROOT));

  private static final String FAKE_GEN_ROOT_EXECUTION_PATH_FRAGMENT =
      "blaze-out/gcc-4.X.Y-crosstool-v17-hybrid-grtev3-k8-fastbuild/bin";

  private static final String FAKE_GEN_ROOT =
      "/path/to/8093958afcfde6c33d08b621dfaa4e09/root/" + FAKE_GEN_ROOT_EXECUTION_PATH_FRAGMENT;

  private static final ArtifactLocationDecoder FAKE_ARTIFACT_DECODER =
      new ArtifactLocationDecoder(
          new BlazeRoots(
              new File("/"),
              ImmutableList.of(),
              new ExecutionRootPath("out/crosstool/bin"),
              new ExecutionRootPath("out/crosstool/gen")),
          null);

  private static final BlazeImportSettings DUMMY_IMPORT_SETTINGS =
      new BlazeImportSettings("", "", "", "", "", BuildSystem.Blaze);
  private ExtensionPointImpl<BlazeJavaSyncAugmenter> augmenters;

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
  private final WorkspaceLanguageSettings workspaceLanguageSettings =
      new WorkspaceLanguageSettings(WorkspaceType.JAVA, ImmutableSet.of(LanguageClass.JAVA));
  private MockExperimentService experimentService;

  @Override
  protected void initTest(
      @NotNull Container applicationServices, @NotNull Container projectServices) {
    experimentService = new MockExperimentService();
    applicationServices.register(ExperimentService.class, experimentService);

    BlazeExecutor blazeExecutor = new MockBlazeExecutor();
    applicationServices.register(BlazeExecutor.class, blazeExecutor);
    projectServices.register(
        BlazeImportSettingsManager.class, new BlazeImportSettingsManager(project));
    BlazeImportSettingsManager.getInstance(getProject()).setImportSettings(DUMMY_IMPORT_SETTINGS);

    // will silently fall back to FilePathJavaPackageReader
    applicationServices.register(
        JavaSourcePackageReader.class,
        new JavaSourcePackageReader() {
          @Nullable
          @Override
          public String getDeclaredPackageOfJavaFile(
              @NotNull BlazeContext context, @NotNull SourceArtifact sourceArtifact) {
            return null;
          }
        });
    applicationServices.register(PackageManifestReader.class, new PackageManifestReader());
    applicationServices.register(PrefetchService.class, new MockPrefetchService());

    context = new BlazeContext();
    context.addOutputSink(IssueOutput.class, errorCollector);

    augmenters =
        registerExtensionPoint(BlazeJavaSyncAugmenter.EP_NAME, BlazeJavaSyncAugmenter.class);
  }

  BlazeJavaImportResult importWorkspace(
      WorkspaceRoot workspaceRoot, RuleMapBuilder ruleMapBuilder, ProjectView projectView) {

    ProjectViewSet projectViewSet = ProjectViewSet.builder().add(projectView).build();

    BlazeJavaWorkspaceImporter blazeWorkspaceImporter =
        new BlazeJavaWorkspaceImporter(
            project,
            context,
            workspaceRoot,
            projectViewSet,
            workspaceLanguageSettings,
            ruleMapBuilder.build(),
            jdepsMap,
            workingSet,
            FAKE_ARTIFACT_DECODER);

    return blazeWorkspaceImporter.importWorkspace(context);
  }

  /** Ensure an empty response results in an empty import result. */
  @Test
  public void testEmptyProject() {
    BlazeJavaImportResult result =
        importWorkspace(workspaceRoot, RuleMapBuilder.builder(), ProjectView.builder().build());
    errorCollector.assertNoIssues();
    assertTrue(result.contentEntries.isEmpty());
  }

  @Test
  public void testSingleModule() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("java/apps/example"))))
            .build();

    RuleMapBuilder ruleMapBuilder =
        RuleMapBuilder.builder()
            .addRule(
                RuleIdeInfo.builder()
                    .setLabel("//java/apps/example:example_debug")
                    .setBuildFile(source("java/apps/example/BUILD"))
                    .setKind("android_binary")
                    .addSource(source("java/apps/example/MainActivity.java"))
                    .addSource(source("java/apps/example/subdir/SubdirHelper.java"))
                    .setAndroidInfo(
                        AndroidRuleIdeInfo.builder()
                            .setManifestFile(source("java/apps/example/AndroidManifest.xml"))
                            .addResource(source("java/apps/example/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.android.apps.example"))
                    .setJavaInfo(
                        JavaRuleIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(
                                        gen("java/apps/example/example_debug-ijar.jar"))
                                    .setClassJar(gen("java/apps/example/example_debug.jar")))));

    BlazeJavaImportResult result = importWorkspace(workspaceRoot, ruleMapBuilder, projectView);
    errorCollector.assertNoIssues();

    assertEquals(1, result.buildOutputJars.size());
    File compilerOutputLib = result.buildOutputJars.iterator().next();
    assertNotNull(compilerOutputLib);
    assertTrue(compilerOutputLib.getPath().endsWith("example_debug.jar"));

    assertThat(result.contentEntries)
        .containsExactly(
            BlazeContentEntry.builder("/root/java/apps/example")
                .addSource(
                    BlazeSourceDirectory.builder("/root/java/apps/example")
                        .setPackagePrefix("apps.example")
                        .build())
                .build());

    assertThat(result.javaSourceFiles)
        .containsExactly(
            source("java/apps/example/MainActivity.java").getFile(),
            source("java/apps/example/subdir/SubdirHelper.java").getFile());
  }

  @Test
  public void testGeneratedLibrariesIncluded() {
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
                    .setKind("java_library")
                    .addSource(source("java/example/Test.java"))
                    .setJavaInfo(
                        JavaRuleIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("java/example/lib-ijar.jar"))
                                    .setClassJar(gen("java/example/lib.jar")))
                            .addGeneratedJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("java/example/lib-gen.jar"))
                                    .setClassJar(gen("java/example/lib-gen.jar")))));

    BlazeJavaImportResult result = importWorkspace(workspaceRoot, ruleMapBuilder, projectView);
    assertThat(
            result
                .libraries
                .values()
                .stream()
                .map(BlazeJavaWorkspaceImporterTest::libraryFileName)
                .collect(Collectors.toList()))
        .containsExactly("lib-gen.jar");
  }

  /** Imports two binaries and a library. Only one binary should pass the package filter. */
  @Test
  public void testImportFilter() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("java/apps/example"))))
            .build();

    RuleMapBuilder ruleMapBuilder =
        RuleMapBuilder.builder()
            .addRule(
                RuleIdeInfo.builder()
                    .setLabel("//java/apps/example:example_debug")
                    .setBuildFile(source("java/apps/example/BUILD"))
                    .setKind("android_binary")
                    .addSource(source("java/apps/example/MainActivity.java"))
                    .setAndroidInfo(
                        AndroidRuleIdeInfo.builder()
                            .setManifestFile(source("java/apps/example/AndroidManifest.xml"))
                            .addResource(source("java/apps/example/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.android.apps.example"))
                    .addDependency("//java/libraries/example:example")
                    .setJavaInfo(
                        JavaRuleIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("java/apps/example/example_debug.jar"))
                                    .setClassJar(gen("java/apps/example/example_debug.jar")))))
            .addRule(
                RuleIdeInfo.builder()
                    .setLabel("//java/libraries/example:example")
                    .setBuildFile(source("java/libraries/example/BUILD"))
                    .setKind("java_library")
                    .addSource(source("java/libraries/example/SharedActivity.java"))
                    .setAndroidInfo(
                        AndroidRuleIdeInfo.builder()
                            .setManifestFile(source("java/libraries/example/AndroidManifest.xml"))
                            .addResource(source("java/libraries/example/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.android.libraries.example"))
                    .setJavaInfo(
                        JavaRuleIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("java/libraries/example/example.jar"))
                                    .setClassJar(gen("java/libraries/example/example.jar")))))
            .addRule(
                RuleIdeInfo.builder()
                    .setLabel("//java/com/dontimport:example_debug")
                    .setBuildFile(source("java/com/dontimport/BUILD"))
                    .setKind("android_binary")
                    .addSource(source("java/com/dontimport/MainActivity.java"))
                    .setAndroidInfo(
                        AndroidRuleIdeInfo.builder()
                            .setManifestFile(source("java/com/dontimport/AndroidManifest.xml"))
                            .addResource(source("java/com/dontimport/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.dontimport"))
                    .addDependency("//java/com/dontimport:sometarget")
                    .setJavaInfo(
                        JavaRuleIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("java/com/dontimport/example_debug.jar"))
                                    .setClassJar(gen("java/com/dontimport/example_debug.jar")))));

    BlazeJavaImportResult result = importWorkspace(workspaceRoot, ruleMapBuilder, projectView);
    errorCollector.assertNoIssues();

    assertThat(result.contentEntries)
        .containsExactly(
            BlazeContentEntry.builder("/root/java/apps/example")
                .addSource(
                    BlazeSourceDirectory.builder("/root/java/apps/example")
                        .setPackagePrefix("apps.example")
                        .build())
                .build());
    assertThat(result.javaSourceFiles)
        .containsExactly(source("java/apps/example/MainActivity.java").getFile());
  }

  /** Import a project and its tests */
  @Test
  public void testProjectAndTests() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("java/apps/example")))
                    .add(DirectoryEntry.include(new WorkspacePath("javatests/apps/example"))))
            .add(ListSection.builder(TestSourceSection.KEY).add(new Glob("javatests/*")))
            .build();

    RuleMapBuilder ruleMapBuilder =
        RuleMapBuilder.builder()
            .addRule(
                RuleIdeInfo.builder()
                    .setLabel("//java/apps/example:example_debug")
                    .setBuildFile(source("java/apps/example/BUILD"))
                    .setKind("android_binary")
                    .addSource(source("java/apps/example/MainActivity.java"))
                    .addSource(source("java/apps/example/subdir/SubdirHelper.java"))
                    .setAndroidInfo(
                        AndroidRuleIdeInfo.builder()
                            .setManifestFile(source("java/apps/example/AndroidManifest.xml"))
                            .addResource(source("java/apps/example/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.android.apps.example"))
                    .setJavaInfo(
                        JavaRuleIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("java/apps/example/example_debug.jar"))
                                    .setClassJar(gen("java/apps/example/example_debug.jar")))))
            .addRule(
                RuleIdeInfo.builder()
                    .setLabel("//javatests/apps/example:example")
                    .setBuildFile(source("javatests/apps/example/BUILD"))
                    .setKind("android_test")
                    .addSource(source("javatests/apps/example/ExampleTests.java"))
                    .setAndroidInfo(
                        AndroidRuleIdeInfo.builder()
                            .setResourceJavaPackage("com.google.android.apps.example"))
                    .addDependency("//java/apps/example:example_debug")
                    .setJavaInfo(
                        JavaRuleIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("javatests/apps/example/example.jar"))
                                    .setClassJar(gen("javatests/apps/example/example.jar")))));

    BlazeJavaImportResult result = importWorkspace(workspaceRoot, ruleMapBuilder, projectView);
    errorCollector.assertNoIssues();

    assertThat(result.contentEntries)
        .containsExactly(
            BlazeContentEntry.builder("/root/java/apps/example")
                .addSource(
                    BlazeSourceDirectory.builder("/root/java/apps/example")
                        .setPackagePrefix("apps.example")
                        .build())
                .build(),
            BlazeContentEntry.builder("/root/javatests/apps/example")
                .addSource(
                    BlazeSourceDirectory.builder("/root/javatests/apps/example")
                        .setPackagePrefix("apps.example")
                        .setTest(true)
                        .build())
                .build());
  }

  /** Test library with a source jar */
  @Test
  public void testLibraryWithSourceJar() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("java/apps/example")))
                    .add(DirectoryEntry.include(new WorkspacePath("javatests/apps/example"))))
            .build();

    RuleMapBuilder ruleMapBuilder =
        RuleMapBuilder.builder()
            .addRule(
                RuleIdeInfo.builder()
                    .setLabel("//java/apps/example:example_debug")
                    .setBuildFile(source("java/apps/example/BUILD"))
                    .setKind("android_binary")
                    .addSource(source("java/apps/example/MainActivity.java"))
                    .addSource(source("java/apps/example/subdir/SubdirHelper.java"))
                    .setAndroidInfo(
                        AndroidRuleIdeInfo.builder()
                            .setManifestFile(source("java/apps/example/AndroidManifest.xml"))
                            .addResource(gen("java/apps/example/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.android.apps.example"))
                    .addDependency("//thirdparty/some/library:library")
                    .setJavaInfo(
                        JavaRuleIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("java/apps/example/example_debug.jar"))
                                    .setClassJar(gen("java/apps/example/example_debug.jar")))))
            .addRule(
                RuleIdeInfo.builder()
                    .setLabel("//thirdparty/some/library:library")
                    .setBuildFile(source("/thirdparty/some/library/BUILD"))
                    .setKind("java_import")
                    .setJavaInfo(
                        JavaRuleIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("thirdparty/some/library.jar"))
                                    .setClassJar(gen("thirdparty/some/library.jar"))
                                    .setSourceJar(gen("thirdparty/some/library.srcjar")))));

    BlazeJavaImportResult result = importWorkspace(workspaceRoot, ruleMapBuilder, projectView);
    errorCollector.assertNoIssues();

    BlazeJarLibrary library = findLibrary(result.libraries, "library.jar");
    assertNotNull(library);
    assertNotNull(library.libraryArtifact.sourceJar);
  }

  /** Test a project with a java test rule */
  @Test
  public void testJavaTestRule() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("java/apps/example")))
                    .add(DirectoryEntry.include(new WorkspacePath("javatests/apps/example"))))
            .add(ListSection.builder(TestSourceSection.KEY).add(new Glob("javatests/*")))
            .build();

    RuleMapBuilder ruleMapBuilder =
        RuleMapBuilder.builder()
            .addRule(
                RuleIdeInfo.builder()
                    .setLabel("//java/apps/example:example_debug")
                    .setBuildFile(source("java/apps/example/BUILD"))
                    .setKind("android_binary")
                    .addSource(source("java/apps/example/MainActivity.java"))
                    .addSource(source("java/apps/example/subdir/SubdirHelper.java"))
                    .setAndroidInfo(
                        AndroidRuleIdeInfo.builder()
                            .setManifestFile(source("java/apps/example/AndroidManifest.xml"))
                            .addResource(source("java/apps/example/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.android.apps.example"))
                    .setJavaInfo(
                        JavaRuleIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("java/apps/example/example_debug.jar"))
                                    .setClassJar(gen("java/apps/example/example_debug.jar")))))
            .addRule(
                RuleIdeInfo.builder()
                    .setLabel("//javatests/apps/example:example")
                    .setBuildFile(source("javatests/apps/example/BUILD"))
                    .setKind("java_test")
                    .addSource(source("javatests/apps/example/ExampleTests.java"))
                    .addDependency("//java/apps/example:example_debug")
                    .setJavaInfo(
                        JavaRuleIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("javatests/apps/example/example.jar"))
                                    .setClassJar(gen("javatests/apps/example/example.jar")))));

    BlazeJavaImportResult result = importWorkspace(workspaceRoot, ruleMapBuilder, projectView);
    errorCollector.assertNoIssues();

    assertThat(result.contentEntries)
        .containsExactly(
            BlazeContentEntry.builder("/root/java/apps/example")
                .addSource(
                    BlazeSourceDirectory.builder("/root/java/apps/example")
                        .setPackagePrefix("apps.example")
                        .build())
                .build(),
            BlazeContentEntry.builder("/root/javatests/apps/example")
                .addSource(
                    BlazeSourceDirectory.builder("/root/javatests/apps/example")
                        .setPackagePrefix("apps.example")
                        .setTest(true)
                        .build())
                .build());
  }

  /*
   * Test that the non-android libraries can be imported.
   */
  @Test
  public void testNormalJavaLibraryPackage() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("java/apps/example")))
                    .add(DirectoryEntry.include(new WorkspacePath("java/library/something"))))
            .build();

    RuleMapBuilder ruleMapBuilder =
        RuleMapBuilder.builder()
            .addRule(
                RuleIdeInfo.builder()
                    .setLabel("//java/apps/example:example_debug")
                    .setBuildFile(source("java/apps/example/BUILD"))
                    .setKind("android_binary")
                    .addSource(source("java/apps/example/MainActivity.java"))
                    .addSource(source("java/apps/example/subdir/SubdirHelper.java"))
                    .setJavaInfo(JavaRuleIdeInfo.builder())
                    .setAndroidInfo(
                        AndroidRuleIdeInfo.builder()
                            .setManifestFile(source("java/apps/example/AndroidManifest.xml"))
                            .addResource(source("java/apps/example/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.android.apps.example"))
                    .addDependency("//java/library/something:something"))
            .addRule(
                RuleIdeInfo.builder()
                    .setLabel("//java/library/something:something")
                    .setBuildFile(source("java/library/something/BUILD"))
                    .setKind("java_library")
                    .addSource(source("java/library/something/SomeJavaFile.java"))
                    .setJavaInfo(
                        JavaRuleIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("java/library/something/something.jar"))
                                    .setClassJar(gen("java/library/something/something.jar")))));

    BlazeJavaImportResult result = importWorkspace(workspaceRoot, ruleMapBuilder, projectView);
    errorCollector.assertNoIssues();

    assertThat(result.contentEntries)
        .containsExactly(
            BlazeContentEntry.builder("/root/java/apps/example")
                .addSource(
                    BlazeSourceDirectory.builder("/root/java/apps/example")
                        .setPackagePrefix("apps.example")
                        .build())
                .build(),
            BlazeContentEntry.builder("/root/java/library/something")
                .addSource(
                    BlazeSourceDirectory.builder("/root/java/library/something")
                        .setPackagePrefix("library.something")
                        .build())
                .build());
  }

  @Test
  public void testImportTargetOutputTag() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("lib")))
                    .add(DirectoryEntry.include(new WorkspacePath("lib2"))))
            .build();

    RuleMapBuilder response =
        RuleMapBuilder.builder()
            .addRule(
                RuleIdeInfo.builder()
                    .setLabel("//lib:lib")
                    .setBuildFile(source("lib/BUILD"))
                    .setKind("java_library")
                    .addSource(source("lib/Lib.java"))
                    .addDependency("//lib2:lib2")
                    .setJavaInfo(
                        JavaRuleIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("lib/lib.jar"))
                                    .setClassJar(gen("lib/lib.jar")))))
            .addRule(
                RuleIdeInfo.builder()
                    .setLabel("//lib2:lib2")
                    .setBuildFile(source("lib2/BUILD"))
                    .setKind("java_library")
                    .addSource(source("lib2/Lib2.java"))
                    .addTag("intellij-import-target-output")
                    .setJavaInfo(
                        JavaRuleIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("lib2/lib2.jar"))
                                    .setClassJar(gen("lib2/lib2.jar")))));

    BlazeJavaImportResult result = importWorkspace(workspaceRoot, response, projectView);
    errorCollector.assertNoIssues();
    assertEquals(1, result.libraries.size());
  }

  @Test
  public void testImportAsLibraryTagLegacy() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("lib")))
                    .add(DirectoryEntry.include(new WorkspacePath("lib2"))))
            .build();

    RuleMapBuilder response =
        RuleMapBuilder.builder()
            .addRule(
                RuleIdeInfo.builder()
                    .setLabel("//lib:lib")
                    .setBuildFile(source("lib/BUILD"))
                    .setKind("java_library")
                    .addSource(source("lib/Lib.java"))
                    .addDependency("//lib2:lib2")
                    .setJavaInfo(
                        JavaRuleIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("lib/lib.jar"))
                                    .setClassJar(gen("lib/lib.jar")))))
            .addRule(
                RuleIdeInfo.builder()
                    .setLabel("//lib2:lib2")
                    .setBuildFile(source("lib2/BUILD"))
                    .setKind("java_library")
                    .addSource(source("lib2/Lib2.java"))
                    .addTag("aswb-import-as-library")
                    .setJavaInfo(
                        JavaRuleIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("lib2/lib2.jar"))
                                    .setClassJar(gen("lib2/lib2.jar")))));

    BlazeJavaImportResult result = importWorkspace(workspaceRoot, response, projectView);
    errorCollector.assertNoIssues();

    assertEquals(1, result.libraries.size());
  }

  @Test
  public void testMultipleImportOfJarsGetMerged() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("lib"))))
            .build();

    RuleMapBuilder response =
        RuleMapBuilder.builder()
            .addRule(
                RuleIdeInfo.builder()
                    .setLabel("//lib:libsource")
                    .setBuildFile(source("lib/BUILD"))
                    .setKind("java_library")
                    .addSource(source("lib/Source.java"))
                    .setJavaInfo(JavaRuleIdeInfo.builder())
                    .addDependency("//lib:lib0")
                    .addDependency("//lib:lib1"))
            .addRule(
                RuleIdeInfo.builder()
                    .setLabel("//lib:lib0")
                    .setBuildFile(source("lib/BUILD"))
                    .setKind("java_import")
                    .setJavaInfo(
                        JavaRuleIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(source("lib/lib.jar"))
                                    .setClassJar(source("lib/lib.jar")))))
            .addRule(
                RuleIdeInfo.builder()
                    .setLabel("//lib:lib1")
                    .setBuildFile(source("lib/BUILD"))
                    .setKind("java_import")
                    .setJavaInfo(
                        JavaRuleIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(source("lib/lib.jar"))
                                    .setClassJar(source("lib/lib.jar")))));

    BlazeJavaImportResult result = importWorkspace(workspaceRoot, response, projectView);
    errorCollector.assertNoIssues();
    assertEquals(1, result.libraries.size()); // The libraries were merged
  }

  @Test
  public void testRuleWithOnlyGeneratedSourcesIsAddedAsLibrary() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("import"))))
            .build();

    RuleMapBuilder response =
        RuleMapBuilder.builder()
            .addRule(
                RuleIdeInfo.builder()
                    .setLabel("//import:lib")
                    .setBuildFile(source("import/BUILD"))
                    .setKind("android_library")
                    .addSource(source("import/Lib.java"))
                    .setJavaInfo(JavaRuleIdeInfo.builder())
                    .addDependency("//import:import")
                    .addDependency("//import:import_android"))
            .addRule(
                RuleIdeInfo.builder()
                    .setLabel("//import:import")
                    .setBuildFile(source("import/BUILD"))
                    .setKind("java_library")
                    .addSource(gen("import/GenSource.java"))
                    .setJavaInfo(
                        JavaRuleIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("import/import.jar"))
                                    .setClassJar(gen("import/import.jar")))))
            .addRule(
                RuleIdeInfo.builder()
                    .setLabel("//import:import_android")
                    .setBuildFile(source("import/BUILD"))
                    .setKind("android_library")
                    .addSource(gen("import/GenSource.java"))
                    .setJavaInfo(
                        JavaRuleIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("import/import_android.jar"))
                                    .setClassJar(gen("import/import_android.jar")))));

    BlazeJavaImportResult result = importWorkspace(workspaceRoot, response, projectView);
    errorCollector.assertNoIssues();

    assertThat(findLibrary(result.libraries, "import.jar")).isNotNull();
    assertThat(findLibrary(result.libraries, "import_android.jar")).isNotNull();
  }

  @Test
  public void testRuleWithMixedGeneratedSourcesAddsFilteredGenJar() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("import"))))
            .build();

    RuleMapBuilder response =
        RuleMapBuilder.builder()
            .addRule(
                RuleIdeInfo.builder()
                    .setLabel("//import:lib")
                    .setBuildFile(source("import/BUILD"))
                    .setKind("java_library")
                    .addSource(source("import/Import.java"))
                    .addSource(gen("import/Import.java"))
                    .setJavaInfo(
                        JavaRuleIdeInfo.builder()
                            .setFilteredGenJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("import/filtered-gen.jar")))));

    BlazeJavaImportResult result = importWorkspace(workspaceRoot, response, projectView);
    errorCollector.assertNoIssues();
    assertThat(findLibrary(result.libraries, "filtered-gen.jar")).isNotNull();
  }

  @Test
  public void testRuleWithOnlySourceJarAsSourceAddedAsLibrary() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("import"))))
            .build();

    RuleMapBuilder response =
        RuleMapBuilder.builder()
            .addRule(
                RuleIdeInfo.builder()
                    .setLabel("//import:lib")
                    .setBuildFile(source("import/BUILD"))
                    .setKind("android_library")
                    .addSource(source("import/Lib.java"))
                    .setJavaInfo(JavaRuleIdeInfo.builder())
                    .addDependency("//import:import")
                    .addDependency("//import:import_android"))
            .addRule(
                RuleIdeInfo.builder()
                    .setLabel("//import:import")
                    .setBuildFile(source("import/BUILD"))
                    .setKind("java_library")
                    .addSource(gen("import/gen-src.jar"))
                    .setJavaInfo(
                        JavaRuleIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("import/import.jar"))
                                    .setClassJar(gen("import/import.jar")))));

    BlazeJavaImportResult result = importWorkspace(workspaceRoot, response, projectView);
    errorCollector.assertNoIssues();

    assertThat(findLibrary(result.libraries, "import.jar")).isNotNull();
  }

  @Test
  public void testImportTargetOutput() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("import"))))
            .add(
                ListSection.builder(ImportTargetOutputSection.KEY)
                    .add(new Label("//import:import")))
            .build();

    RuleMapBuilder response =
        RuleMapBuilder.builder()
            .addRule(
                RuleIdeInfo.builder()
                    .setLabel("//import:lib")
                    .setBuildFile(source("import/BUILD"))
                    .setKind("java_library")
                    .addSource(source("import/Lib.java"))
                    .setJavaInfo(JavaRuleIdeInfo.builder())
                    .addDependency("//import:import"))
            .addRule(
                RuleIdeInfo.builder()
                    .setLabel("//import:import")
                    .setBuildFile(source("import/BUILD"))
                    .setKind("java_library")
                    .addSource(source("import/Import.java"))
                    .setJavaInfo(
                        JavaRuleIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("import/import.jar"))
                                    .setClassJar(gen("import/import.jar")))));

    BlazeJavaImportResult result = importWorkspace(workspaceRoot, response, projectView);
    errorCollector.assertNoIssues();

    assertThat(result.libraries).isNotEmpty();
  }

  private RuleMapBuilder ruleMapForJdepsSuite() {
    return RuleMapBuilder.builder()
        .addRule(
            RuleIdeInfo.builder()
                .setLabel("//java/apps/example:example_debug")
                .setBuildFile(source("java/apps/example/BUILD"))
                .setKind("java_library")
                .addSource(source("java/apps/example/Test.java"))
                .setJavaInfo(JavaRuleIdeInfo.builder())
                .addDependency("//thirdparty/a:a"))
        .addRule(
            RuleIdeInfo.builder()
                .setLabel("//thirdparty/a:a")
                .setKind("java_library")
                .addSource(source("thirdparty/a/A.java"))
                .setBuildFile(source("third_party/a/BUILD"))
                .addDependency("//thirdparty/b:b")
                .setJavaInfo(
                    JavaRuleIdeInfo.builder()
                        .addJar(
                            LibraryArtifact.builder()
                                .setInterfaceJar(gen("thirdparty/a.jar"))
                                .setClassJar(gen("thirdparty/a.jar"))
                                .setSourceJar(gen("thirdparty/a.srcjar")))))
        .addRule(
            RuleIdeInfo.builder()
                .setLabel("//thirdparty/b:b")
                .setKind("java_library")
                .addSource(source("thirdparty/b/B.java"))
                .setBuildFile(source("third_party/b/BUILD"))
                .addDependency("//thirdparty/c:c")
                .setJavaInfo(
                    JavaRuleIdeInfo.builder()
                        .addJar(
                            LibraryArtifact.builder()
                                .setInterfaceJar(gen("thirdparty/b.jar"))
                                .setClassJar(gen("thirdparty/b.jar"))
                                .setSourceJar(gen("thirdparty/b.srcjar")))))
        .addRule(
            RuleIdeInfo.builder()
                .setLabel("//thirdparty/c:c")
                .setKind("java_library")
                .addSource(source("thirdparty/c/C.java"))
                .setBuildFile(source("third_party/c/BUILD"))
                .setJavaInfo(
                    JavaRuleIdeInfo.builder()
                        .addJar(
                            LibraryArtifact.builder()
                                .setInterfaceJar(gen("thirdparty/c.jar"))
                                .setClassJar(gen("thirdparty/c.jar"))
                                .setSourceJar(gen("thirdparty/c.srcjar")))));
  }

  @Test
  public void testLibraryDependenciesWithJdepsSet() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("java/apps/example")))
                    .add(DirectoryEntry.include(new WorkspacePath("javatests/apps/example"))))
            .build();
    RuleMapBuilder ruleMapBuilder = ruleMapForJdepsSuite();
    jdepsMap.put(
        new Label("//java/apps/example:example_debug"),
        Lists.newArrayList(jdepsPath("thirdparty/a.jar"), jdepsPath("thirdparty/c.jar")));

    BlazeJavaImportResult result = importWorkspace(workspaceRoot, ruleMapBuilder, projectView);
    assertThat(
            result
                .libraries
                .values()
                .stream()
                .map(BlazeJavaWorkspaceImporterTest::libraryFileName)
                .collect(Collectors.toList()))
        .containsExactly("a.jar", "c.jar");
  }

  @Test
  public void
      testLibraryDependenciesWithJdepsReportingNothingShouldStillIncludeDirectDepsIfInWorkingSet() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("java/apps/example")))
                    .add(DirectoryEntry.include(new WorkspacePath("javatests/apps/example"))))
            .build();
    RuleMapBuilder ruleMapBuilder = ruleMapForJdepsSuite();
    workingSet =
        new JavaWorkingSet(
            workspaceRoot,
            new WorkingSet(
                ImmutableList.of(new WorkspacePath("java/apps/example/Test.java")),
                ImmutableList.of(),
                ImmutableList.of()));

    BlazeJavaImportResult result = importWorkspace(workspaceRoot, ruleMapBuilder, projectView);
    assertThat(
            result
                .libraries
                .values()
                .stream()
                .map(BlazeJavaWorkspaceImporterTest::libraryFileName)
                .collect(Collectors.toList()))
        .containsExactly("a.jar");
  }

  @Test
  public void testLibraryDepsWithJdepsReportingZeroShouldNotIncludeDirectDepsIfNotInWorkingSet() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("java/apps/example")))
                    .add(DirectoryEntry.include(new WorkspacePath("javatests/apps/example"))))
            .build();
    RuleMapBuilder ruleMapBuilder = ruleMapForJdepsSuite();
    workingSet =
        new JavaWorkingSet(
            workspaceRoot,
            new WorkingSet(ImmutableList.of(), ImmutableList.of(), ImmutableList.of()));

    BlazeJavaImportResult result = importWorkspace(workspaceRoot, ruleMapBuilder, projectView);
    assertThat(
            result
                .libraries
                .values()
                .stream()
                .map(BlazeJavaWorkspaceImporterTest::libraryFileName)
                .collect(Collectors.toList()))
        .isEmpty();
  }

  /*
   * Test the exclude_target section
   */
  @Test
  public void testExcludeTarget() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("java/apps/example"))))
            .add(
                ListSection.builder(ExcludeTargetSection.KEY)
                    .add(new Label("//java/apps/example:example")))
            .build();

    RuleMapBuilder ruleMapBuilder =
        RuleMapBuilder.builder()
            .addRule(
                RuleIdeInfo.builder()
                    .setLabel("//java/apps/example:example")
                    .setBuildFile(source("java/apps/example/BUILD"))
                    .setKind("java_library")
                    .addSource(source("java/apps/example/Example.java"))
                    .setJavaInfo(
                        JavaRuleIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder().setInterfaceJar(gen("example.jar")))));

    BlazeJavaImportResult result = importWorkspace(workspaceRoot, ruleMapBuilder, projectView);
    errorCollector.assertNoIssues();

    assertThat(result.javaSourceFiles).isEmpty();
  }

  /*
   * Test the intellij-exclude-target tag
   */
  @Test
  public void testExcludeTargetTag() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath("java/apps/example"))))
            .build();

    RuleMapBuilder ruleMapBuilder =
        RuleMapBuilder.builder()
            .addRule(
                RuleIdeInfo.builder()
                    .setLabel("//java/apps/example:example")
                    .addTag(Tags.RULE_TAG_EXCLUDE_TARGET)
                    .setBuildFile(source("java/apps/example/BUILD"))
                    .setKind("java_library")
                    .addSource(source("java/apps/example/Example.java"))
                    .setJavaInfo(
                        JavaRuleIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder().setInterfaceJar(gen("example.jar")))));

    BlazeJavaImportResult result = importWorkspace(workspaceRoot, ruleMapBuilder, projectView);
    errorCollector.assertNoIssues();

    assertThat(result.javaSourceFiles).isEmpty();
  }

  /** Test legacy proto_library jars, complete with overrides and everything. */
  @Test
  public void testLegacyProtoLibraryInfo() {
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
                    .setLabel("//java/example:liba")
                    .setBuildFile(source("java/example/BUILD"))
                    .setKind("java_library")
                    .addSource(source("java/example/Liba.java"))
                    .setJavaInfo(JavaRuleIdeInfo.builder())
                    .addDependency("//thirdparty/proto/a:a"))
            .addRule(
                RuleIdeInfo.builder()
                    .setLabel("//java/example:libb")
                    .setBuildFile(source("java/example/BUILD"))
                    .setKind("java_library")
                    .addSource(source("java/example/Libb.java"))
                    .setJavaInfo(JavaRuleIdeInfo.builder())
                    .addDependency("//thirdparty/proto/b:b"))
            .addRule(
                RuleIdeInfo.builder()
                    .setLabel("//thirdparty/proto/a:a")
                    .setBuildFile(source("/thirdparty/a/BUILD"))
                    .setKind("proto_library")
                    .setProtoLibraryLegacyInfo(
                        ProtoLibraryLegacyInfo.builder(ProtoLibraryLegacyInfo.ApiFlavor.IMMUTABLE)
                            .addJarV1(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("thirdparty/proto/a/liba-1-ijar.jar")))
                            .addJarImmutable(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("thirdparty/proto/a/liba-ijar.jar"))))
                    .addDependency("//thirdparty/proto/b:b")
                    .addDependency("//thirdparty/proto/c:c"))
            .addRule(
                RuleIdeInfo.builder()
                    .setLabel("//thirdparty/proto/b:b")
                    .setBuildFile(source("/thirdparty/b/BUILD"))
                    .setKind("proto_library")
                    .setProtoLibraryLegacyInfo(
                        ProtoLibraryLegacyInfo.builder(ProtoLibraryLegacyInfo.ApiFlavor.VERSION_1)
                            .addJarV1(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("thirdparty/proto/b/libb-ijar.jar")))
                            .addJarImmutable(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("thirdparty/proto/b/libb-2-ijar.jar"))))
                    .addDependency("//thirdparty/proto/d:d"))
            .addRule(
                RuleIdeInfo.builder()
                    .setLabel("//thirdparty/proto/c:c")
                    .setBuildFile(source("/thirdparty/c/BUILD"))
                    .setKind("proto_library")
                    .setProtoLibraryLegacyInfo(
                        ProtoLibraryLegacyInfo.builder(ProtoLibraryLegacyInfo.ApiFlavor.IMMUTABLE)
                            .addJarV1(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("thirdparty/proto/c/libc-1-ijar.jar")))
                            .addJarImmutable(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("thirdparty/proto/c/libc-ijar.jar"))))
                    .addDependency("//thirdparty/proto/d:d"))
            .addRule(
                RuleIdeInfo.builder()
                    .setLabel("//thirdparty/proto/d:d")
                    .setBuildFile(source("/thirdparty/d/BUILD"))
                    .setKind("proto_library")
                    .setProtoLibraryLegacyInfo(
                        ProtoLibraryLegacyInfo.builder(ProtoLibraryLegacyInfo.ApiFlavor.VERSION_1)
                            .addJarV1(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("thirdparty/proto/d/libd-ijar.jar")))
                            .addJarImmutable(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("thirdparty/proto/d/libd-2-ijar.jar")))));

    workingSet =
        new JavaWorkingSet(
            workspaceRoot,
            new WorkingSet(ImmutableList.of(), ImmutableList.of(), ImmutableList.of()));

    // First test - make sure that jdeps is working
    jdepsMap.put(
        new Label("//java/example:liba"),
        Lists.newArrayList(jdepsPath("thirdparty/proto/a/liba-ijar.jar")));
    BlazeJavaImportResult result = importWorkspace(workspaceRoot, ruleMapBuilder, projectView);
    errorCollector.assertNoIssues();
    assertThat(result.libraries).hasSize(1);
    assertThat(findLibrary(result.libraries, "liba-ijar.jar")).isNotNull();

    // Second test
    // Put everything in the working set, which should expand to the full transitive closure
    workingSet =
        new JavaWorkingSet(
            workspaceRoot,
            new WorkingSet(
                ImmutableList.of(new WorkspacePath("java/example/BUILD")),
                ImmutableList.of(),
                ImmutableList.of()));

    result = importWorkspace(workspaceRoot, ruleMapBuilder, projectView);
    errorCollector.assertNoIssues();

    assertThat(result.libraries).hasSize(6);
    assertThat(findLibrary(result.libraries, "liba-ijar.jar")).isNotNull();
    assertThat(findLibrary(result.libraries, "libb-ijar.jar")).isNotNull();
    assertThat(findLibrary(result.libraries, "libb-2-ijar.jar")).isNotNull();
    assertThat(findLibrary(result.libraries, "libc-ijar.jar")).isNotNull();
    assertThat(findLibrary(result.libraries, "libd-ijar.jar")).isNotNull();
    assertThat(findLibrary(result.libraries, "libd-2-ijar.jar")).isNotNull();
  }

  /** Test that the non-android libraries can be imported. */
  @Test
  public void testImporterWorksWithWorkspaceRootDirectoryIncluded() {
    ProjectView projectView =
        ProjectView.builder()
            .add(
                ListSection.builder(DirectorySection.KEY)
                    .add(DirectoryEntry.include(new WorkspacePath(""))))
            .build();

    RuleMapBuilder ruleMapBuilder =
        RuleMapBuilder.builder()
            .addRule(
                RuleIdeInfo.builder()
                    .setLabel("//java/apps/example:example_debug")
                    .setBuildFile(source("java/apps/example/BUILD"))
                    .setKind("android_binary")
                    .addSource(source("java/apps/example/MainActivity.java"))
                    .addSource(source("java/apps/example/subdir/SubdirHelper.java"))
                    .setJavaInfo(JavaRuleIdeInfo.builder())
                    .setAndroidInfo(
                        AndroidRuleIdeInfo.builder()
                            .setManifestFile(source("java/apps/example/AndroidManifest.xml"))
                            .addResource(source("java/apps/example/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.android.apps.example"))
                    .addDependency("//java/library/something:something"))
            .addRule(
                RuleIdeInfo.builder()
                    .setLabel("//java/library/something:something")
                    .setBuildFile(source("java/library/something/BUILD"))
                    .setKind("java_library")
                    .addSource(source("java/library/something/SomeJavaFile.java"))
                    .setJavaInfo(
                        JavaRuleIdeInfo.builder()
                            .addJar(
                                LibraryArtifact.builder()
                                    .setInterfaceJar(gen("java/library/something/something.jar"))
                                    .setClassJar(gen("java/library/something/something.jar")))));

    BlazeJavaImportResult result = importWorkspace(workspaceRoot, ruleMapBuilder, projectView);
    errorCollector.assertNoIssues();

    assertThat(result.contentEntries)
        .containsExactly(
            BlazeContentEntry.builder("/root")
                .addSource(BlazeSourceDirectory.builder("/root").build())
                .addSource(BlazeSourceDirectory.builder("/root/java").build())
                .build());
  }

  @Test
  public void testLanguageLevelIsReadFromToolchain() {
    ProjectView projectView = ProjectView.builder().build();

    RuleMapBuilder ruleMapBuilder =
        RuleMapBuilder.builder()
            .addRule(
                RuleIdeInfo.builder()
                    .setLabel("//java:toolchain")
                    .setBuildFile(source("java/BUILD"))
                    .setKind("java_toolchain")
                    .setJavaToolchainIdeInfo(
                        JavaToolchainIdeInfo.builder()
                            .setSourceVersion("8")
                            .setTargetVersion("8")));

    BlazeJavaImportResult result = importWorkspace(workspaceRoot, ruleMapBuilder, projectView);
    assertThat(result.sourceVersion).isEqualTo("8");
  }

  @Test
  public void testSyncAugmenter() {
    augmenters.registerExtension(
        new BlazeJavaSyncAugmenter.Adapter() {
          @Override
          public boolean isActive(WorkspaceLanguageSettings workspaceLanguageSettings) {
            return true;
          }

          @Override
          public void addJarsForSourceRule(
              RuleIdeInfo rule,
              Collection<BlazeJarLibrary> jars,
              Collection<BlazeJarLibrary> genJars) {
            if (rule.label.equals(new Label("//java/example:source"))) {
              jars.add(
                  new BlazeJarLibrary(
                      LibraryArtifact.builder().setInterfaceJar(gen("source.jar")).build(),
                      rule.label));
            }
          }
        });

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
                    .setLabel("//java/example:source")
                    .setBuildFile(source("java/example/BUILD"))
                    .setKind("java_library")
                    .addSource(source("Source.java"))
                    .addDependency("//java/lib:lib")
                    .setJavaInfo(JavaRuleIdeInfo.builder()))
            .addRule(
                RuleIdeInfo.builder()
                    .setLabel("//java/lib:lib")
                    .setBuildFile(source("java/lib/BUILD"))
                    .setKind("java_library")
                    .addSource(source("Lib.java"))
                    .setJavaInfo(JavaRuleIdeInfo.builder()));

    BlazeJavaImportResult result = importWorkspace(workspaceRoot, ruleMapBuilder, projectView);
    assertThat(
            result
                .libraries
                .values()
                .stream()
                .map(BlazeJavaWorkspaceImporterTest::libraryFileName)
                .collect(Collectors.toList()))
        .containsExactly("source.jar");
  }

  /* Utility methods */

  private static String libraryFileName(BlazeJarLibrary library) {
    return library.libraryArtifact.jarForIntellijLibrary().getFile().getName();
  }

  @Nullable
  private static BlazeJarLibrary findLibrary(
      Map<LibraryKey, BlazeJarLibrary> libraries, String libraryName) {
    for (BlazeJarLibrary library : libraries.values()) {
      if (library
          .libraryArtifact
          .jarForIntellijLibrary()
          .getFile()
          .getPath()
          .endsWith(libraryName)) {
        return library;
      }
    }
    return null;
  }

  private ArtifactLocation source(String relativePath) {
    return ArtifactLocation.builder()
        .setRootPath(FAKE_WORKSPACE_ROOT)
        .setRelativePath(relativePath)
        .setIsSource(true)
        .build();
  }

  private static ArtifactLocation gen(String relativePath) {
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
