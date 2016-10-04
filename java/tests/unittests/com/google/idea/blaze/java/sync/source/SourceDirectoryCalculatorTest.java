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
package com.google.idea.blaze.java.sync.source;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.google.idea.blaze.base.async.executor.MockBlazeExecutor;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.io.FileAttributeProvider;
import com.google.idea.blaze.base.io.InputStreamProvider;
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.prefetch.MockPrefetchService;
import com.google.idea.blaze.base.prefetch.PrefetchService;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.ErrorCollector;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.sync.projectview.SourceTestConfig;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.sync.workspace.BlazeRoots;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverImpl;
import com.google.idea.blaze.java.sync.model.BlazeContentEntry;
import com.google.idea.blaze.java.sync.model.BlazeSourceDirectory;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import com.google.repackaged.devtools.build.lib.ideinfo.androidstudio.PackageManifestOuterClass;
import com.google.repackaged.devtools.build.lib.ideinfo.androidstudio.PackageManifestOuterClass.JavaSourcePackage;
import com.google.repackaged.devtools.build.lib.ideinfo.androidstudio.PackageManifestOuterClass.PackageManifest;
import com.intellij.util.containers.HashMap;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test cases for {@link SourceDirectoryCalculator}. */
@RunWith(JUnit4.class)
public class SourceDirectoryCalculatorTest extends BlazeTestCase {

  private static final ImmutableMap<Label, ArtifactLocation> NO_MANIFESTS = ImmutableMap.of();
  private static final Label LABEL = new Label("//fake:label");

  private MockInputStreamProvider mockInputStreamProvider;
  private SourceDirectoryCalculator sourceDirectoryCalculator;

  private BlazeContext context = new BlazeContext();
  private ErrorCollector issues = new ErrorCollector();
  private MockExperimentService experimentService;

  private WorkspaceRoot workspaceRoot = new WorkspaceRoot(new File("/root"));
  private ArtifactLocationDecoder decoder =
      new ArtifactLocationDecoder(
          new BlazeRoots(
              new File("/"),
              Lists.newArrayList(new File("/usr/local/code")),
              new ExecutionRootPath("out/crosstool/bin"),
              new ExecutionRootPath("out/crosstool/gen")),
          null);

  static final class TestSourceImportConfig extends SourceTestConfig {
    final boolean isTest;

    public TestSourceImportConfig(boolean isTest) {
      super(ProjectViewSet.builder().build());
      this.isTest = isTest;
    }

    @Override
    public boolean isTestSource(String relativePath) {
      return isTest;
    }
  }

  @Override
  protected void initTest(
      @NotNull Container applicationServices, @NotNull Container projectServices) {
    super.initTest(applicationServices, projectServices);

    mockInputStreamProvider = new MockInputStreamProvider();
    applicationServices.register(InputStreamProvider.class, mockInputStreamProvider);
    applicationServices.register(JavaSourcePackageReader.class, new JavaSourcePackageReader());
    applicationServices.register(PackageManifestReader.class, new PackageManifestReader());
    applicationServices.register(FileAttributeProvider.class, new MockFileAttributeProvider());

    context.addOutputSink(IssueOutput.class, issues);
    sourceDirectoryCalculator = new SourceDirectoryCalculator();

    BlazeExecutor blazeExecutor = new MockBlazeExecutor();
    applicationServices.register(BlazeExecutor.class, blazeExecutor);

    experimentService = new MockExperimentService();
    applicationServices.register(ExperimentService.class, experimentService);

    applicationServices.register(PrefetchService.class, new MockPrefetchService());
  }

  @Test
  public void testWorkspacePathIsAddedWithoutSources() throws Exception {
    List<SourceArtifact> sourceArtifacts = ImmutableList.of();
    ImmutableList<BlazeContentEntry> result =
        sourceDirectoryCalculator.calculateContentEntries(
            project,
            context,
            workspaceRoot,
            new TestSourceImportConfig(false /* isTest */),
            decoder,
            ImmutableList.of(new WorkspacePath("java/com/google/app")),
            sourceArtifacts,
            NO_MANIFESTS);
    issues.assertNoIssues();
    assertThat(result)
        .containsExactly(
            BlazeContentEntry.builder("/root/java/com/google/app")
                .addSource(
                    BlazeSourceDirectory.builder("/root/java/com/google/app")
                        .setPackagePrefix("com.google.app")
                        .build())
                .build());
  }

  @Test
  public void testCalculatesPackageForSimpleCase() throws Exception {
    mockInputStreamProvider.addFile(
        "/root/java/com/google/Bla.java", "package com.google;\n public class Bla {}");
    List<SourceArtifact> sourceArtifacts =
        ImmutableList.of(
            SourceArtifact.builder(LABEL)
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("java/com/google/Bla.java")
                        .setRootPath("/root")
                        .setIsSource(true))
                .build());
    ImmutableList<BlazeContentEntry> result =
        sourceDirectoryCalculator.calculateContentEntries(
            project,
            context,
            workspaceRoot,
            new TestSourceImportConfig(false),
            decoder,
            ImmutableList.of(new WorkspacePath("java/com/google")),
            sourceArtifacts,
            NO_MANIFESTS);
    assertThat(result)
        .containsExactly(
            BlazeContentEntry.builder("/root/java/com/google")
                .addSource(
                    BlazeSourceDirectory.builder("/root/java/com/google")
                        .setPackagePrefix("com.google")
                        .build())
                .build());
    issues.assertNoIssues();
  }

  @Test
  public void testSourcesToSourceDirectories_testReturnsTest() throws Exception {
    mockInputStreamProvider.addFile(
        "/root/java/com/google/Bla.java", "package com.google;\n public class Bla {}");
    List<SourceArtifact> sourceArtifacts =
        ImmutableList.of(
            SourceArtifact.builder(LABEL)
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("java/com/google/Bla.java")
                        .setRootPath("/root")
                        .setIsSource(true))
                .build());
    ImmutableList<BlazeContentEntry> result =
        sourceDirectoryCalculator.calculateContentEntries(
            project,
            context,
            workspaceRoot,
            new TestSourceImportConfig(true),
            decoder,
            ImmutableList.of(new WorkspacePath("java/com/google")),
            sourceArtifacts,
            NO_MANIFESTS);
    issues.assertNoIssues();
    assertThat(result)
        .containsExactly(
            BlazeContentEntry.builder("/root/java/com/google")
                .addSource(
                    BlazeSourceDirectory.builder("/root/java/com/google")
                        .setPackagePrefix("com.google")
                        .setTest(true)
                        .build())
                .build());
  }

  @Test
  public void testSourcesToSourceDirectories_multipleMatchingPackagesAreMerged() throws Exception {
    mockInputStreamProvider
        .addFile("/root/java/com/google/Bla.java", "package com.google;\n public class Bla {}")
        .addFile(
            "/root/java/com/google/subpackage/Bla.java",
            "package com.google.subpackage;\n public class Bla {}");
    List<SourceArtifact> sourceArtifacts =
        ImmutableList.of(
            SourceArtifact.builder(LABEL)
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("java/com/google/Bla.java")
                        .setRootPath("/root")
                        .setIsSource(true))
                .build(),
            SourceArtifact.builder(LABEL)
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("java/com/google/subpackage/Bla.java")
                        .setRootPath("/root")
                        .setIsSource(true))
                .build());
    ImmutableList<BlazeContentEntry> result =
        sourceDirectoryCalculator.calculateContentEntries(
            project,
            context,
            workspaceRoot,
            new TestSourceImportConfig(false),
            decoder,
            ImmutableList.of(new WorkspacePath("java/com/google")),
            sourceArtifacts,
            NO_MANIFESTS);
    issues.assertNoIssues();
    assertThat(result)
        .containsExactly(
            BlazeContentEntry.builder("/root/java/com/google")
                .addSource(
                    BlazeSourceDirectory.builder("/root/java/com/google")
                        .setPackagePrefix("com.google")
                        .build())
                .build());
  }

  @Test
  public void testMultipleDirectoriesAreMergedWithDirectoryRootAsWorkspaceRoot() throws Exception {
    mockInputStreamProvider
        .addFile(
            "/root/java/com/google/idea/blaze/plugin/run/Run.java",
            "package com.google.idea.blaze.plugin.run;\n public class run {}")
        .addFile(
            "/root/java/com/google/idea/blaze/plugin/sync/Sync.java",
            "package com.google.idea.blaze.plugin.sync;\n public class Sync {}")
        .addFile(
            "/root/java/com/google/idea/blaze/plugin/Plugin.java",
            "package com.google.idea.blaze.plugin;\n public class Plugin {}");
    List<SourceArtifact> sourceArtifacts =
        ImmutableList.of(
            SourceArtifact.builder(LABEL)
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("java/com/google/idea/blaze/plugin/run/Run.java")
                        .setRootPath("/root")
                        .setIsSource(true))
                .build(),
            SourceArtifact.builder(LABEL)
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("java/com/google/idea/blaze/plugin/sync/Sync.java")
                        .setRootPath("/root")
                        .setIsSource(true))
                .build(),
            SourceArtifact.builder(LABEL)
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("java/com/google/idea/blaze/plugin/Plugin.java")
                        .setRootPath("/root")
                        .setIsSource(true))
                .build());
    ImmutableList<BlazeContentEntry> result =
        sourceDirectoryCalculator.calculateContentEntries(
            project,
            context,
            workspaceRoot,
            new TestSourceImportConfig(false),
            decoder,
            ImmutableList.of(new WorkspacePath("")),
            sourceArtifacts,
            NO_MANIFESTS);
    issues.assertNoIssues();
    assertThat(result)
        .containsExactly(
            BlazeContentEntry.builder("/root")
                .addSource(BlazeSourceDirectory.builder("/root").setPackagePrefix("").build())
                .addSource(BlazeSourceDirectory.builder("/root/java").setPackagePrefix("").build())
                .build());
  }

  @Test
  public void testIncorrectPackageInMiddleOfTreeCausesMergePointHigherUp() throws Exception {
    mockInputStreamProvider
        .addFile(
            "/root/java/com/google/idea/blaze/plugin/run/Run.java",
            "package com.google.idea.blaze.plugin.run;\n public class run {}")
        .addFile(
            "/root/java/com/google/idea/blaze/plugin/sync/Sync.java",
            "package com.google.idea.blaze.plugin.sync;\n public class Sync {}")
        .addFile(
            "/root/java/com/google/idea/blaze/Incorrect.java",
            "package com.google.idea.blaze.incorrect;\n public class Incorrect {}");
    List<SourceArtifact> sourceArtifacts =
        ImmutableList.of(
            SourceArtifact.builder(LABEL)
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("java/com/google/idea/blaze/plugin/run/Run.java")
                        .setRootPath("/root")
                        .setIsSource(true))
                .build(),
            SourceArtifact.builder(LABEL)
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("java/com/google/idea/blaze/plugin/sync/Sync.java")
                        .setRootPath("/root")
                        .setIsSource(true))
                .build(),
            SourceArtifact.builder(LABEL)
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("java/com/google/idea/blaze/Incorrect.java")
                        .setRootPath("/root")
                        .setIsSource(true))
                .build());
    ImmutableList<BlazeContentEntry> result =
        sourceDirectoryCalculator.calculateContentEntries(
            project,
            context,
            workspaceRoot,
            new TestSourceImportConfig(false),
            decoder,
            ImmutableList.of(new WorkspacePath("")),
            sourceArtifacts,
            NO_MANIFESTS);
    issues.assertNoIssues();
    assertThat(result)
        .containsExactly(
            BlazeContentEntry.builder("/root")
                .addSource(BlazeSourceDirectory.builder("/root").setPackagePrefix("").build())
                .addSource(BlazeSourceDirectory.builder("/root/java").setPackagePrefix("").build())
                .addSource(
                    BlazeSourceDirectory.builder("/root/java/com/google/idea/blaze")
                        .setPackagePrefix("com.google.idea.blaze.incorrect")
                        .build())
                .addSource(
                    BlazeSourceDirectory.builder("/root/java/com/google/idea/blaze/plugin")
                        .setPackagePrefix("com.google.idea.blaze.plugin")
                        .build())
                .build());
  }

  @Test
  public void testSourcesToSourceDirectories_multipleNonMatchingPackagesAreNotMerged()
      throws Exception {
    mockInputStreamProvider
        .addFile("/root/java/com/google/Bla.java", "package com.google;\n public class Bla {}")
        .addFile(
            "/root/java/com/google/subpackage/Bla.java",
            "package com.google.different;\n public class Bla {}");
    List<SourceArtifact> sourceArtifacts =
        ImmutableList.of(
            SourceArtifact.builder(LABEL)
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("java/com/google/Bla.java")
                        .setRootPath("/root")
                        .setIsSource(true))
                .build(),
            SourceArtifact.builder(LABEL)
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("java/com/google/subpackage/Bla.java")
                        .setRootPath("/root")
                        .setIsSource(true))
                .build());
    ImmutableList<BlazeContentEntry> result =
        sourceDirectoryCalculator.calculateContentEntries(
            project,
            context,
            workspaceRoot,
            new TestSourceImportConfig(false),
            decoder,
            ImmutableList.of(new WorkspacePath("java/com/google")),
            sourceArtifacts,
            NO_MANIFESTS);
    issues.assertNoIssues();
    assertThat(result)
        .containsExactly(
            BlazeContentEntry.builder("/root/java/com/google")
                .addSource(
                    BlazeSourceDirectory.builder("/root/java/com/google")
                        .setPackagePrefix("com.google")
                        .build())
                .addSource(
                    BlazeSourceDirectory.builder("/root/java/com/google/subpackage")
                        .setPackagePrefix("com.google.different")
                        .build())
                .build());
  }

  @Test
  public void testSourcesToSourceDirectories_childMatchesPathButParentDoesnt() throws Exception {
    mockInputStreamProvider
        .addFile("/root/java/com/google/Bla.java", "package com.facebook;\n public class Bla {}")
        .addFile(
            "/root/java/com/google/subpackage/Bla.java",
            "package com.google.subpackage;\n public class Bla {}");
    List<SourceArtifact> sourceArtifacts =
        ImmutableList.of(
            SourceArtifact.builder(LABEL)
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("java/com/google/Bla.java")
                        .setRootPath("/root")
                        .setIsSource(true))
                .build(),
            SourceArtifact.builder(LABEL)
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("java/com/google/subpackage/Bla.java")
                        .setRootPath("/root")
                        .setIsSource(true))
                .build());
    ImmutableList<BlazeContentEntry> result =
        sourceDirectoryCalculator.calculateContentEntries(
            project,
            context,
            workspaceRoot,
            new TestSourceImportConfig(false),
            decoder,
            ImmutableList.of(new WorkspacePath("java/com/google")),
            sourceArtifacts,
            NO_MANIFESTS);
    issues.assertNoIssues();
    assertThat(result)
        .containsExactly(
            BlazeContentEntry.builder("/root/java/com/google")
                .addSource(
                    BlazeSourceDirectory.builder("/root/java/com/google")
                        .setPackagePrefix("com.facebook")
                        .build())
                .addSource(
                    BlazeSourceDirectory.builder("/root/java/com/google/subpackage")
                        .setPackagePrefix("com.google.subpackage")
                        .build())
                .build());
  }

  @Test
  public void testSourcesToSourceDirectories_orderIsIrrelevant() throws Exception {
    mockInputStreamProvider
        .addFile("/root/java/com/google/Bla.java", "package com.google;\n public class Bla {}")
        .addFile(
            "/root/java/com/google/subpackage/Bla.java",
            "package com.google.different;\n public class Bla {}");
    List<SourceArtifact> sourceArtifacts =
        ImmutableList.of(
            SourceArtifact.builder(LABEL)
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("java/com/google/subpackage/Bla.java")
                        .setRootPath("/root")
                        .setIsSource(true))
                .build(),
            SourceArtifact.builder(LABEL)
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("java/com/google/Bla.java")
                        .setRootPath("/root")
                        .setIsSource(true))
                .build());
    ImmutableList<BlazeContentEntry> result =
        sourceDirectoryCalculator.calculateContentEntries(
            project,
            context,
            workspaceRoot,
            new TestSourceImportConfig(false),
            decoder,
            ImmutableList.of(new WorkspacePath("java/com/google")),
            sourceArtifacts,
            NO_MANIFESTS);
    issues.assertNoIssues();
    assertThat(result)
        .containsExactly(
            BlazeContentEntry.builder("/root/java/com/google")
                .addSource(
                    BlazeSourceDirectory.builder("/root/java/com/google")
                        .setPackagePrefix("com.google")
                        .build())
                .addSource(
                    BlazeSourceDirectory.builder("/root/java/com/google/subpackage")
                        .setPackagePrefix("com.google.different")
                        .build())
                .build());
  }

  @Test
  public void testSourcesToSourceDirectories_packagesMatchPath() throws Exception {
    mockInputStreamProvider.addFile(
        "/root/java/com/google/Bla.java", "package com.google;\n public class Bla {}");
    List<SourceArtifact> sourceArtifacts =
        ImmutableList.of(
            SourceArtifact.builder(LABEL)
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("java/com/google/Bla.java")
                        .setRootPath("/root")
                        .setIsSource(true))
                .build());
    ImmutableList<BlazeContentEntry> result =
        sourceDirectoryCalculator.calculateContentEntries(
            project,
            context,
            workspaceRoot,
            new TestSourceImportConfig(false),
            decoder,
            ImmutableList.of(new WorkspacePath("java/com/google")),
            sourceArtifacts,
            NO_MANIFESTS);
    issues.assertNoIssues();
    assertThat(result)
        .containsExactly(
            BlazeContentEntry.builder("/root/java/com/google")
                .addSource(
                    BlazeSourceDirectory.builder("/root/java/com/google")
                        .setPackagePrefix("com.google")
                        .build())
                .build());
  }

  @Test
  public void testSourcesToSourceDirectories_packagesDoNotMatchPath() throws Exception {
    mockInputStreamProvider.addFile(
        "/root/java/com/google/Bla.java", "package com.facebook;\n public class Bla {}");
    List<SourceArtifact> sourceArtifacts =
        ImmutableList.of(
            SourceArtifact.builder(LABEL)
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("java/com/google/Bla.java")
                        .setRootPath("/root")
                        .setIsSource(true))
                .build());
    ImmutableList<BlazeContentEntry> result =
        sourceDirectoryCalculator.calculateContentEntries(
            project,
            context,
            workspaceRoot,
            new TestSourceImportConfig(false),
            decoder,
            ImmutableList.of(new WorkspacePath("java/com/google")),
            sourceArtifacts,
            NO_MANIFESTS);
    issues.assertNoIssues();
    assertThat(result)
        .containsExactly(
            BlazeContentEntry.builder("/root/java/com/google")
                .addSource(
                    BlazeSourceDirectory.builder("/root/java/com/google")
                        .setPackagePrefix("com.facebook")
                        .build())
                .build());
  }

  @Test
  public void testSourcesToSourceDirectories_completePackagePathMismatch() throws Exception {
    mockInputStreamProvider.addFile(
        "/root/java/com/org/foo/Bla.java", "package com.facebook;\n public class Bla {}");
    List<SourceArtifact> sourceArtifacts =
        ImmutableList.of(
            SourceArtifact.builder(LABEL)
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("java/com/org/foo/Bla.java")
                        .setRootPath("/root")
                        .setIsSource(true))
                .build());
    ImmutableList<BlazeContentEntry> result =
        sourceDirectoryCalculator.calculateContentEntries(
            project,
            context,
            workspaceRoot,
            new TestSourceImportConfig(false),
            decoder,
            ImmutableList.of(new WorkspacePath("java/com/org")),
            sourceArtifacts,
            NO_MANIFESTS);
    assertThat(result)
        .containsExactly(
            BlazeContentEntry.builder("/root/java/com/org")
                .addSource(
                    BlazeSourceDirectory.builder("/root/java/com/org")
                        .setPackagePrefix("com.org")
                        .build())
                .addSource(
                    BlazeSourceDirectory.builder("/root/java/com/org/foo")
                        .setPackagePrefix("com.facebook")
                        .build())
                .build());
  }

  @Test
  public void testSourcesToSourceDirectories_sourcesOutsideOfModuleGeneratesIssue()
      throws Exception {
    mockInputStreamProvider.addFile(
        "/root/java/com/facebook/Bla.java", "package com.facebook;\n public class Bla {}");
    List<SourceArtifact> sourceArtifacts =
        ImmutableList.of(
            SourceArtifact.builder(LABEL)
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("java/com/facebook/Bla.java")
                        .setRootPath("/root")
                        .setIsSource(true))
                .build());
    ImmutableList<BlazeContentEntry> result =
        sourceDirectoryCalculator.calculateContentEntries(
            project,
            context,
            workspaceRoot,
            new TestSourceImportConfig(false),
            decoder,
            ImmutableList.of(new WorkspacePath("java/com/google")),
            sourceArtifacts,
            NO_MANIFESTS);

    issues.assertIssueContaining("Did not add");
  }

  @Test
  public void testSourcesToSourceDirectories_generatedSourcesOutsideOfModuleGeneratesNoIssue()
      throws Exception {
    mockInputStreamProvider.addFile(
        "/root/java/com/facebook/Bla.java", "package com.facebook;\n public class Bla {}");
    List<SourceArtifact> sourceArtifacts =
        ImmutableList.of(
            SourceArtifact.builder(LABEL)
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("java/com/facebook/Bla.java")
                        .setRootPath("/root")
                        .setIsSource(false))
                .build());
    ImmutableList<BlazeContentEntry> result =
        sourceDirectoryCalculator.calculateContentEntries(
            project,
            context,
            workspaceRoot,
            new TestSourceImportConfig(false),
            decoder,
            ImmutableList.of(new WorkspacePath("java/com/google/my")),
            sourceArtifacts,
            NO_MANIFESTS);
    issues.assertNoIssues();
  }

  @Test
  public void testSourcesToSourceDirectories_missingPackageDeclaration() throws Exception {
    mockInputStreamProvider.addFile("/root/java/com/google/Bla.java", "public class Bla {}");
    List<SourceArtifact> sourceArtifacts =
        ImmutableList.of(
            SourceArtifact.builder(LABEL)
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("java/com/google/Bla.java")
                        .setRootPath("/root")
                        .setIsSource(true))
                .build());
    ImmutableList<BlazeContentEntry> result =
        sourceDirectoryCalculator.calculateContentEntries(
            project,
            context,
            workspaceRoot,
            new TestSourceImportConfig(false),
            decoder,
            ImmutableList.of(new WorkspacePath("java/com/google")),
            sourceArtifacts,
            NO_MANIFESTS);

    issues.assertIssueContaining("No package name string found");
  }

  @Test
  public void testCompetingPackageDeclarationPicksMajority() throws Exception {
    mockInputStreamProvider
        .addFile(
            "/root/java/com/google/Foo.java", "package com.google.different;\n public class Foo {}")
        .addFile("/root/java/com/google/Bla.java", "package com.google;\n public class Bla {}")
        .addFile("/root/java/com/google/Bla2.java", "package com.google;\n public class Bla2 {}")
        .addFile("/root/java/com/google/Bla3.java", "package com.google;\n public class Bla3 {}");
    List<SourceArtifact> sourceArtifacts =
        ImmutableList.of(
            SourceArtifact.builder(LABEL)
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("java/com/google/Bla.java")
                        .setRootPath("/root")
                        .setIsSource(true))
                .build(),
            SourceArtifact.builder(LABEL)
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("java/com/google/Bla2.java")
                        .setRootPath("/root")
                        .setIsSource(true))
                .build(),
            SourceArtifact.builder(LABEL)
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("java/com/google/Bla3.java")
                        .setRootPath("/root")
                        .setIsSource(true))
                .build(),
            SourceArtifact.builder(LABEL)
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("java/com/google/Foo.java")
                        .setRootPath("/root")
                        .setIsSource(true))
                .build());
    ImmutableList<BlazeContentEntry> result =
        sourceDirectoryCalculator.calculateContentEntries(
            project,
            context,
            workspaceRoot,
            new TestSourceImportConfig(false),
            decoder,
            ImmutableList.of(new WorkspacePath("java/com/google")),
            sourceArtifacts,
            NO_MANIFESTS);
    issues.assertNoIssues();
    assertThat(result)
        .containsExactly(
            BlazeContentEntry.builder("/root/java/com/google")
                .addSource(
                    BlazeSourceDirectory.builder("/root/java/com/google")
                        .setPackagePrefix("com.google")
                        .build())
                .build());
  }

  @Test
  public void testSourcesToSourceDirectories_packagesMatchPathButNotAtRoot() throws Exception {
    mockInputStreamProvider
        .addFile(
            "/root/java/com/google/Bla.java", "package com.google.different;\n public class Bla {}")
        .addFile(
            "/root/java/com/google/subpackage/Bla.java",
            "package com.google.subpackage;\n public class Bla {}")
        .addFile(
            "/root/java/com/google/subpackage/subsubpackage/Bla.java",
            "package com.google.subpackage.subsubpackage;\n public class Bla {}");
    List<SourceArtifact> sourceArtifacts =
        ImmutableList.of(
            SourceArtifact.builder(LABEL)
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("java/com/google/Bla.java")
                        .setRootPath("/root")
                        .setIsSource(true))
                .build(),
            SourceArtifact.builder(LABEL)
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("java/com/google/subpackage/Bla.java")
                        .setRootPath("/root")
                        .setIsSource(true))
                .build(),
            SourceArtifact.builder(LABEL)
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("java/com/google/subpackage/subsubpackage/Bla.java")
                        .setRootPath("/root")
                        .setIsSource(true))
                .build());
    ImmutableList<BlazeContentEntry> result =
        sourceDirectoryCalculator.calculateContentEntries(
            project,
            context,
            workspaceRoot,
            new TestSourceImportConfig(false),
            decoder,
            ImmutableList.of(new WorkspacePath("java/com/google")),
            sourceArtifacts,
            NO_MANIFESTS);
    issues.assertNoIssues();
    assertThat(result)
        .containsExactly(
            BlazeContentEntry.builder("/root/java/com/google")
                .addSource(
                    BlazeSourceDirectory.builder("/root/java/com/google")
                        .setPackagePrefix("com.google.different")
                        .build())
                .addSource(
                    BlazeSourceDirectory.builder("/root/java/com/google/subpackage")
                        .setPackagePrefix("com.google.subpackage")
                        .build())
                .build());
  }

  @Test
  public void testSourcesToSourceDirectories_multipleSubdirectoriesAreNotMerged() throws Exception {
    mockInputStreamProvider
        .addFile(
            "/root/java/com/google/package0/Bla.java",
            "package com.google.packagewrong0;\n public class Bla {}")
        .addFile(
            "/root/java/com/google/package1/Bla.java",
            "package com.google.packagewrong1;\n public class Bla {}");
    List<SourceArtifact> sourceArtifacts =
        ImmutableList.of(
            SourceArtifact.builder(LABEL)
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("java/com/google/package0/Bla.java")
                        .setRootPath("/root")
                        .setIsSource(true))
                .build(),
            SourceArtifact.builder(LABEL)
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("java/com/google/package1/Bla.java")
                        .setRootPath("/root")
                        .setIsSource(true))
                .build());
    ImmutableList<BlazeContentEntry> result =
        sourceDirectoryCalculator.calculateContentEntries(
            project,
            context,
            workspaceRoot,
            new TestSourceImportConfig(false),
            decoder,
            ImmutableList.of(new WorkspacePath("java/com/google")),
            sourceArtifacts,
            NO_MANIFESTS);
    issues.assertNoIssues();
    assertThat(result)
        .containsExactly(
            BlazeContentEntry.builder("/root/java/com/google")
                .addSource(
                    BlazeSourceDirectory.builder("/root/java/com/google")
                        .setPackagePrefix("com.google")
                        .build())
                .addSource(
                    BlazeSourceDirectory.builder("/root/java/com/google/package0")
                        .setPackagePrefix("com.google.packagewrong0")
                        .build())
                .addSource(
                    BlazeSourceDirectory.builder("/root/java/com/google/package1")
                        .setPackagePrefix("com.google.packagewrong1")
                        .build())
                .build());
  }

  @Test
  public void testLowestDirectoryIsPrioritised() throws Exception {
    mockInputStreamProvider
        .addFile(
            "/root/java/com/google/android/chimera/internal/Preconditions.java",
            "package com.google.android.chimera.container.internal;\n "
                + "public class Preconditions {}")
        .addFile(
            "/root/java/com/google/android/chimera/container/FileApkUtils.java",
            "package com.google.android.chimera.container;\n public class FileApkUtils {}");
    List<SourceArtifact> sourceArtifacts =
        ImmutableList.of(
            SourceArtifact.builder(LABEL)
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath(
                            "java/com/google/android/chimera/internal/Preconditions.java")
                        .setRootPath("/root")
                        .setIsSource(true))
                .build(),
            SourceArtifact.builder(LABEL)
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath(
                            "java/com/google/android/chimera/container/FileApkUtils.java")
                        .setRootPath("/root")
                        .setIsSource(true))
                .build());
    ImmutableList<BlazeContentEntry> result =
        sourceDirectoryCalculator.calculateContentEntries(
            project,
            context,
            workspaceRoot,
            new TestSourceImportConfig(false),
            decoder,
            ImmutableList.of(new WorkspacePath("java/com/google/android")),
            sourceArtifacts,
            NO_MANIFESTS);
    issues.assertNoIssues();
    assertThat(result)
        .containsExactly(
            BlazeContentEntry.builder("/root/java/com/google/android")
                .addSource(
                    BlazeSourceDirectory.builder("/root/java/com/google/android")
                        .setPackagePrefix("com.google.android")
                        .build())
                .addSource(
                    BlazeSourceDirectory.builder("/root/java/com/google/android/chimera/internal")
                        .setPackagePrefix("com.google.android.chimera.container.internal")
                        .build())
                .build());
  }

  @Test
  public void testNewFormatManifest() throws Exception {
    setNewFormatPackageManifest(
        "/root/java/com/test.manifest",
        ImmutableList.of(
            PackageManifestOuterClass.ArtifactLocation.newBuilder()
                .setRelativePath("java/com/google/Bla.java")
                .setIsSource(true)
                .build()),
        ImmutableList.of("com.google"));
    ImmutableMap<Label, ArtifactLocation> manifests =
        ImmutableMap.<Label, ArtifactLocation>builder()
            .put(
                LABEL,
                ArtifactLocation.builder()
                    .setRelativePath("java/com/test.manifest")
                    .setRootPath("/root")
                    .setIsSource(true)
                    .build())
            .build();
    Map<Label, Map<String, String>> manifestMap =
        readPackageManifestFiles(manifests, getDecoder("/root"));

    assertThat(manifestMap.get(LABEL))
        .containsEntry("/root/java/com/google/Bla.java", "com.google");
  }

  @Test
  public void testManifestSingleFile() throws Exception {
    setPackageManifest(
        "/root/java/com/test.manifest",
        ImmutableList.of("java/com/google/Bla.java"),
        ImmutableList.of("com.google"));
    ImmutableMap<Label, ArtifactLocation> manifests =
        ImmutableMap.<Label, ArtifactLocation>builder()
            .put(
                LABEL,
                ArtifactLocation.builder()
                    .setRelativePath("java/com/test.manifest")
                    .setRootPath("/root")
                    .setIsSource(true)
                    .build())
            .build();
    Map<Label, Map<String, String>> manifestMap =
        readPackageManifestFiles(manifests, getDecoder("/root"));

    assertThat(manifestMap.get(LABEL))
        .containsEntry("/root/java/com/google/Bla.java", "com.google");
  }

  @Test
  public void testManifestRepeatedSources() throws Exception {
    setPackageManifest(
        "/root/java/com/test.manifest",
        ImmutableList.of("java/com/google/Bla.java", "java/com/google/Foo.java"),
        ImmutableList.of("com.google", "com.google.subpackage"));
    setPackageManifest(
        "/root/java/com/test2.manifest",
        ImmutableList.of("java/com/google/Bla.java", "java/com/google/other/Temp.java"),
        ImmutableList.of("com.google", "com.google.other"));
    ImmutableMap<Label, ArtifactLocation> manifests =
        ImmutableMap.<Label, ArtifactLocation>builder()
            .put(
                new Label("//a:a"),
                ArtifactLocation.builder()
                    .setRelativePath("java/com/test.manifest")
                    .setRootPath("/root")
                    .setIsSource(true)
                    .build())
            .put(
                new Label("//b:b"),
                ArtifactLocation.builder()
                    .setRelativePath("java/com/test2.manifest")
                    .setRootPath("/root")
                    .setIsSource(true)
                    .build())
            .build();
    Map<Label, Map<String, String>> manifestMap =
        readPackageManifestFiles(manifests, getDecoder("/root"));

    assertThat(manifestMap).hasSize(2);

    assertThat(manifestMap.get(new Label("//a:a")))
        .containsEntry("/root/java/com/google/Bla.java", "com.google");
    assertThat(manifestMap.get(new Label("//a:a")))
        .containsEntry("/root/java/com/google/Foo.java", "com.google.subpackage");
    assertThat(manifestMap.get(new Label("//b:b")))
        .containsEntry("/root/java/com/google/other/Temp.java", "com.google.other");
  }

  @Test
  public void testManifestMissingSourcesFallback() throws Exception {
    setPackageManifest(
        "/root/java/com/test.manifest",
        ImmutableList.of("java/com/google/Bla.java", "java/com/google/Foo.java"),
        ImmutableList.of("com.google", "com.google"));

    mockInputStreamProvider.addFile(
        "/root/java/com/google/subpackage/Bla.java",
        "package com.google.different;\n public class Bla {}");

    ImmutableMap<Label, ArtifactLocation> manifests =
        ImmutableMap.<Label, ArtifactLocation>builder()
            .put(
                LABEL,
                ArtifactLocation.builder()
                    .setRelativePath("java/com/test.manifest")
                    .setRootPath("/root")
                    .setIsSource(true)
                    .build())
            .build();

    List<SourceArtifact> sourceArtifacts =
        ImmutableList.of(
            SourceArtifact.builder(LABEL)
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("java/com/google/Bla.java")
                        .setRootPath("/root")
                        .setIsSource(true))
                .build(),
            SourceArtifact.builder(LABEL)
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("java/com/google/Foo.java")
                        .setRootPath("/root")
                        .setIsSource(true))
                .build(),
            SourceArtifact.builder(LABEL)
                .setArtifactLocation(
                    ArtifactLocation.builder()
                        .setRelativePath("java/com/google/subpackage/Bla.java")
                        .setRootPath("/root")
                        .setIsSource(true))
                .build());

    ImmutableList<BlazeContentEntry> result =
        sourceDirectoryCalculator.calculateContentEntries(
            project,
            context,
            workspaceRoot,
            new TestSourceImportConfig(false),
            getDecoder("/root"),
            ImmutableList.of(new WorkspacePath("java/com/google")),
            sourceArtifacts,
            manifests);

    issues.assertNoIssues();
    assertThat(result)
        .containsExactly(
            BlazeContentEntry.builder("/root/java/com/google")
                .addSource(
                    BlazeSourceDirectory.builder("/root/java/com/google")
                        .setPackagePrefix("com.google")
                        .build())
                .addSource(
                    BlazeSourceDirectory.builder("/root/java/com/google/subpackage")
                        .setPackagePrefix("com.google.different")
                        .build())
                .build());
  }

  private void setPackageManifest(
      String manifestPath,
      List<String> sourceRelativePaths,
      List<String> packages) {
    PackageManifest.Builder manifest = PackageManifest.newBuilder();
    for (int i = 0; i < sourceRelativePaths.size(); i++) {
      String sourceRelativePath = sourceRelativePaths.get(i);
      PackageManifestOuterClass.ArtifactLocation source =
          PackageManifestOuterClass.ArtifactLocation.newBuilder()
              .setRelativePath(sourceRelativePath)
              .setIsSource(true)
              .build();
      manifest.addSources(
          JavaSourcePackage.newBuilder()
              .setArtifactLocation(source)
              .setPackageString(packages.get(i)));
    }
    mockInputStreamProvider.addFile(manifestPath, manifest.build().toByteArray());
  }

  private void setNewFormatPackageManifest(
      String manifestPath,
      List<PackageManifestOuterClass.ArtifactLocation> sources,
      List<String> packages) {
    PackageManifest.Builder manifest = PackageManifest.newBuilder();
    for (int i = 0; i < sources.size(); i++) {
      manifest.addSources(
          JavaSourcePackage.newBuilder()
              .setArtifactLocation(sources.get(i))
              .setPackageString(packages.get(i)));
    }
    mockInputStreamProvider.addFile(manifestPath, manifest.build().toByteArray());
  }

  private static ArtifactLocationDecoder getDecoder(String rootPath) {
    File root = new File(rootPath);
    WorkspaceRoot workspaceRoot = new WorkspaceRoot(root);
    BlazeRoots roots =
        new BlazeRoots(
            root,
            ImmutableList.of(root),
            new ExecutionRootPath("out/crosstool/bin"),
            new ExecutionRootPath("out/crosstool/gen"));
    return new ArtifactLocationDecoder(roots, new WorkspacePathResolverImpl(workspaceRoot, roots));
  }

  private static class MockInputStreamProvider implements InputStreamProvider {

    private final Map<String, InputStream> javaFiles = new HashMap<String, InputStream>();

    public MockInputStreamProvider addFile(String filePath, String javaSrc) {
      try {
        addFile(filePath, javaSrc.getBytes("UTF-8"));
      } catch (UnsupportedEncodingException e) {
        fail(e.getMessage());
      }
      return this;
    }

    public MockInputStreamProvider addFile(String filePath, byte[] contents) {
      javaFiles.put(filePath, new ByteArrayInputStream(contents));
      return this;
    }

    @Override
    public InputStream getFile(@NotNull File path) throws FileNotFoundException {
      final InputStream inputStream = javaFiles.get(path.getPath());
      if (inputStream == null) {
        throw new FileNotFoundException(
            path + " has not been mapped into MockInputStreamProvider.");
      }
      return inputStream;
    }
  }

  private Map<Label, Map<String, String>> readPackageManifestFiles(
      Map<Label, ArtifactLocation> manifests, ArtifactLocationDecoder decoder) {
    return PackageManifestReader.getInstance()
        .readPackageManifestFiles(
            project, context, decoder, manifests, MoreExecutors.sameThreadExecutor());
  }

  static class MockFileAttributeProvider extends FileAttributeProvider {
    @Override
    public long getFileModifiedTime(@NotNull File file) {
      return 1;
    }
  }
}
