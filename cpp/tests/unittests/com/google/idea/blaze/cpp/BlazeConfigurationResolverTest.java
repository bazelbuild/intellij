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
package com.google.idea.blaze.cpp;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.google.idea.blaze.base.async.executor.MockBlazeExecutor;
import com.google.idea.blaze.base.bazel.BazelBuildSystemProvider;
import com.google.idea.blaze.base.bazel.BuildSystemProvider;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.CIdeInfo;
import com.google.idea.blaze.base.ideinfo.CToolchainIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.ideinfo.TargetMapBuilder;
import com.google.idea.blaze.base.io.VirtualFileSystemProvider;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectView;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.ListSection;
import com.google.idea.blaze.base.projectview.section.sections.DirectoryEntry;
import com.google.idea.blaze.base.projectview.section.sections.DirectorySection;
import com.google.idea.blaze.base.projectview.section.sections.TargetSection;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.ErrorCollector;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.impl.ProgressManagerImpl;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link BlazeConfigurationResolver}. */
@RunWith(JUnit4.class)
public class BlazeConfigurationResolverTest extends BlazeTestCase {
  private final BlazeContext context = new BlazeContext();
  private final ErrorCollector errorCollector = new ErrorCollector();
  private final WorkspaceRoot workspaceRoot = new WorkspaceRoot(new File("/root"));

  private BlazeConfigurationResolver resolver;
  private BlazeConfigurationResolverResult resolverResult;
  private MockCompilerVersionChecker compilerVersionChecker;
  private LocalFileSystem mockFileSystem;

  @Override
  protected void initTest(Container applicationServices, Container projectServices) {
    super.initTest(applicationServices, projectServices);
    applicationServices.register(BlazeExecutor.class, new MockBlazeExecutor());
    applicationServices.register(ExperimentService.class, new MockExperimentService());
    compilerVersionChecker = new MockCompilerVersionChecker("1234");
    applicationServices.register(CompilerVersionChecker.class, compilerVersionChecker);
    applicationServices.register(ProgressManager.class, new ProgressManagerImpl());
    applicationServices.register(VirtualFileManager.class, mock(VirtualFileManager.class));
    mockFileSystem = mock(LocalFileSystem.class);
    applicationServices.register(
        VirtualFileSystemProvider.class, mock(VirtualFileSystemProvider.class));
    when(VirtualFileSystemProvider.getInstance().getSystem()).thenReturn(mockFileSystem);

    projectServices.register(BlazeImportSettingsManager.class, new BlazeImportSettingsManager());
    BuildSystemProvider buildSystemProvider = new BazelBuildSystemProvider();
    registerExtensionPoint(BuildSystemProvider.EP_NAME, BuildSystemProvider.class)
        .registerExtension(buildSystemProvider);
    BlazeImportSettingsManager.getInstance(getProject())
        .setImportSettings(
            new BlazeImportSettings("", "", "", "", buildSystemProvider.buildSystem()));

    registerExtensionPoint(
        BlazeCompilerFlagsProcessor.EP_NAME, BlazeCompilerFlagsProcessor.Provider.class);

    context.addOutputSink(IssueOutput.class, errorCollector);

    resolver = new BlazeConfigurationResolver(project);
    resolverResult = BlazeConfigurationResolverResult.empty();
  }

  @Test
  public void testEmptyProject() {
    ProjectView projectView = projectView(directories(), targets());
    TargetMap targetMap = TargetMapBuilder.builder().build();
    assertThatResolving(projectView, targetMap).producesNoConfigurations();
  }

  @Test
  public void testTargetWithoutSources() {
    ProjectView projectView = projectView(directories("foo/bar"), targets("//foo/bar:library"));
    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(createCcToolchain())
            .addTarget(createCcTarget("//foo/bar:library", Kind.CC_LIBRARY, ImmutableList.of()))
            .build();
    assertThatResolving(projectView, targetMap).producesNoConfigurations();
  }

  @Test
  public void testTargetWithGeneratedSources() {
    ProjectView projectView = projectView(directories("foo/bar"), targets("//foo/bar:library"));
    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(createCcToolchain())
            .addTarget(
                createCcTarget(
                    "//foo/bar:library",
                    Kind.CC_LIBRARY,
                    ImmutableList.of(gen("foo/bar/library.cc"))))
            .build();
    assertThatResolving(projectView, targetMap).producesNoConfigurations();
  }

  @Test
  public void testTargetWithMixedSources() {
    ProjectView projectView = projectView(directories("foo/bar"), targets("//foo/bar:binary"));
    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(createCcToolchain())
            .addTarget(
                createCcTarget(
                    "//foo/bar:binary",
                    Kind.CC_BINARY,
                    ImmutableList.of(src("foo/bar/binary.cc"), gen("foo/bar/generated.cc"))))
            .build();
    assertThatResolving(projectView, targetMap).producesConfigurationsFor("//foo/bar:binary");
  }

  @Test
  public void testSingleSourceTarget() {
    ProjectView projectView = projectView(directories("foo/bar"), targets("//foo/bar:binary"));
    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(createCcToolchain())
            .addTarget(
                createCcTarget(
                    "//foo/bar:binary", Kind.CC_BINARY, ImmutableList.of(src("foo/bar/binary.cc"))))
            .build();
    assertThatResolving(projectView, targetMap).producesConfigurationsFor("//foo/bar:binary");
  }

  @Test
  public void testSingleSourceTargetWithLibraryDependencies() {
    ProjectView projectView = projectView(directories("foo/bar"), targets("//foo/bar:binary"));
    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(createCcToolchain())
            .addTarget(
                createCcTarget(
                        "//foo/bar:binary",
                        Kind.CC_BINARY,
                        ImmutableList.of(src("foo/bar/binary.cc")))
                    .addDependency("//bar/baz:library")
                    .addDependency("//third_party:library"))
            .addTarget(
                createCcTarget(
                    "//bar/baz:library",
                    Kind.CC_LIBRARY,
                    ImmutableList.of(src("bar/baz/library.cc"))))
            .addTarget(
                createCcTarget(
                    "//third_party:library",
                    Kind.CC_LIBRARY,
                    ImmutableList.of(src("third_party/library.cc"))))
            .build();
    assertThatResolving(projectView, targetMap).producesConfigurationsFor("//foo/bar:binary");
  }

  @Test
  public void testSingleSourceTargetWithSourceDependencies() {
    ProjectView projectView = projectView(directories("foo/bar"), targets("//foo/bar:binary"));
    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(createCcToolchain())
            .addTarget(
                createCcTarget(
                        "//foo/bar:binary",
                        Kind.CC_BINARY,
                        ImmutableList.of(src("foo/bar/binary.cc")))
                    .addDependency("//foo/bar:library")
                    .addDependency("//third_party:library"))
            .addTarget(
                createCcTarget(
                    "//foo/bar:library",
                    Kind.CC_LIBRARY,
                    ImmutableList.of(src("foo/bar/library.cc")),
                    ImmutableList.of("SOME_DEFINE=1")))
            .addTarget(
                createCcTarget(
                    "//third_party:library",
                    Kind.CC_LIBRARY,
                    ImmutableList.of(src("third_party/library.cc"))))
            .build();
    assertThatResolving(projectView, targetMap)
        .producesConfigurationsFor("//foo/bar:binary", "//foo/bar:library");
  }

  @Test
  public void testComplexProject() {
    ProjectView projectView =
        projectView(
            directories("foo/bar", "foo/baz"),
            targets("//foo:test", "//foo/bar:binary", "//foo/baz:test"));
    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(createCcToolchain())
            .addTarget(
                createCcTarget("//foo:test", Kind.CC_TEST, ImmutableList.of(src("foo/test.cc")))
                    .addDependency("//foo:library")
                    .addDependency("//foo/bar:library")
                    .addDependency("//third_party:library"))
            .addTarget(
                createCcTarget(
                    "//foo:library", Kind.CC_TEST, ImmutableList.of(src("foo/library.cc"))))
            .addTarget(
                createCcTarget(
                        "//foo/bar:binary",
                        Kind.CC_BINARY,
                        ImmutableList.of(src("foo/bar/binary.cc")),
                        ImmutableList.of("SOME_DEFINE=1"))
                    .addDependency("//foo/bar:library")
                    .addDependency("//foo/bar:empty")
                    .addDependency("//foo/bar:generated")
                    .addDependency("//foo/bar:mixed")
                    .addDependency("//third_party:library"))
            .addTarget(
                createCcTarget(
                    "//foo/bar:library",
                    Kind.CC_LIBRARY,
                    ImmutableList.of(src("foo/bar/library.cc")),
                    ImmutableList.of("SOME_DEFINE=2")))
            .addTarget(createCcTarget("//foo/bar:empty", Kind.CC_LIBRARY, ImmutableList.of()))
            .addTarget(
                createCcTarget(
                    "//foo/bar:generated",
                    Kind.CC_LIBRARY,
                    ImmutableList.of(gen("foo/bar/generated.cc"))))
            .addTarget(
                createCcTarget(
                    "//foo/bar:mixed",
                    Kind.CC_LIBRARY,
                    ImmutableList.of(src("foo/bar/mixed_src.cc"), gen("foo/bar/mixed_gen.cc")),
                    ImmutableList.of("SOME_DEFINE=3")))
            .addTarget(
                createCcTarget(
                        "//foo/baz:test",
                        Kind.CC_TEST,
                        ImmutableList.of(src("foo/baz/test.cc")),
                        ImmutableList.of("SOME_DEFINE=4"))
                    .addDependency("//foo/baz:binary")
                    .addDependency("//foo/baz:library")
                    .addDependency("//foo/qux:library"))
            .addTarget(
                createCcTarget(
                    "//foo/baz:binary",
                    Kind.CC_BINARY,
                    ImmutableList.of(src("foo/baz/binary.cc")),
                    ImmutableList.of("SOME_DEFINE=5")))
            .addTarget(
                createCcTarget(
                    "//foo/baz:library",
                    Kind.CC_LIBRARY,
                    ImmutableList.of(src("foo/baz/library.cc")),
                    ImmutableList.of("SOME_DEFINE=6")))
            .addTarget(
                createCcTarget(
                    "//foo/qux:library",
                    Kind.CC_LIBRARY,
                    ImmutableList.of(src("foo/qux/library.cc"))))
            .addTarget(
                createCcTarget(
                    "//third_party:library",
                    Kind.CC_LIBRARY,
                    ImmutableList.of(src("third_party/library.cc"))))
            .build();
    assertThatResolving(projectView, targetMap)
        .producesConfigurationsFor(
            "//foo/bar:binary",
            "//foo/bar:library",
            "//foo/bar:mixed",
            "//foo/baz:test",
            "//foo/baz:binary",
            "//foo/baz:library");
  }

  @Test
  public void firstResolve_testNotIncremental() {
    ProjectView projectView = projectView(directories("foo/bar"), targets("//foo/bar:binary"));
    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(createCcToolchain())
            .addTarget(
                createCcTarget(
                    "//foo/bar:binary", Kind.CC_BINARY, ImmutableList.of(src("foo/bar/binary.cc"))))
            .build();
    ImmutableList<BlazeResolveConfiguration> noReusedConfigurations = ImmutableList.of();
    assertThatResolving(projectView, targetMap)
        .reusedConfigurations(noReusedConfigurations, "//foo/bar:binary");
  }

  @Test
  public void identicalTargets_testIncrementalUpdateFullReuse() {
    ProjectView projectView = projectView(directories("foo/bar"), targets("//foo/bar:binary"));
    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(createCcToolchain())
            .addTarget(
                createCcTarget(
                    "//foo/bar:binary", Kind.CC_BINARY, ImmutableList.of(src("foo/bar/binary.cc"))))
            .build();

    assertThatResolving(projectView, targetMap).producesConfigurationsFor("//foo/bar:binary");
    Collection<BlazeResolveConfiguration> initialConfigurations =
        resolverResult.getAllConfigurations();

    assertThatResolving(projectView, targetMap).reusedConfigurations(initialConfigurations);
  }

  @Test
  public void newTarget_testIncrementalUpdatePartlyReused() {
    ProjectView projectView = projectView(directories("foo/bar"), targets("//foo/bar:*"));
    TargetMapBuilder targetMapBuilder =
        TargetMapBuilder.builder()
            .addTarget(createCcToolchain())
            .addTarget(
                createCcTarget(
                    "//foo/bar:binary",
                    Kind.CC_BINARY,
                    ImmutableList.of(src("foo/bar/binary.cc"))));
    assertThatResolving(projectView, targetMapBuilder.build())
        .producesConfigurationsFor("//foo/bar:binary");
    Collection<BlazeResolveConfiguration> initialConfigurations =
        resolverResult.getAllConfigurations();

    targetMapBuilder.addTarget(
        createCcTarget(
            "//foo/bar:library",
            Kind.CC_LIBRARY,
            ImmutableList.of(src("foo/bar/library.cc")),
            ImmutableList.of("OTHER=1")));

    assertThatResolving(projectView, targetMapBuilder.build())
        .reusedConfigurations(initialConfigurations, "//foo/bar:library");
  }

  @Test
  public void completelyDifferentTargetsSameProjectView_testIncrementalUpdateNoReuse() {
    ProjectView projectView = projectView(directories("foo/bar"), targets("//foo/bar:*"));
    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(createCcToolchain())
            .addTarget(
                createCcTarget(
                    "//foo/bar:binary", Kind.CC_BINARY, ImmutableList.of(src("foo/bar/binary.cc"))))
            .build();
    ImmutableList<BlazeResolveConfiguration> noReusedConfigurations = ImmutableList.of();
    assertThatResolving(projectView, targetMap)
        .reusedConfigurations(noReusedConfigurations, "//foo/bar:binary");

    TargetMap targetMap2 =
        TargetMapBuilder.builder()
            .addTarget(createCcToolchain())
            .addTarget(
                createCcTarget(
                    "//foo/bar:library",
                    Kind.CC_LIBRARY,
                    ImmutableList.of(src("foo/bar/library.cc")),
                    ImmutableList.of("OTHER=1")))
            .build();
    assertThatResolving(projectView, targetMap2)
        .reusedConfigurations(noReusedConfigurations, "//foo/bar:library");
  }

  @Test
  public void completelyDifferentTargetsDifferentProjectView_testIncrementalUpdateNoReuse() {
    ProjectView projectView = projectView(directories("foo/bar"), targets("//foo/bar:binary"));
    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(createCcToolchain())
            .addTarget(
                createCcTarget(
                    "//foo/bar:binary", Kind.CC_BINARY, ImmutableList.of(src("foo/bar/binary.cc"))))
            .build();
    ImmutableList<BlazeResolveConfiguration> noReusedConfigurations = ImmutableList.of();
    assertThatResolving(projectView, targetMap)
        .reusedConfigurations(noReusedConfigurations, "//foo/bar:binary");

    ProjectView projectView2 = projectView(directories("foo/zoo"), targets("//foo/zoo:library"));
    TargetMap targetMap2 =
        TargetMapBuilder.builder()
            .addTarget(createCcToolchain())
            .addTarget(
                createCcTarget(
                    "//foo/zoo:library",
                    Kind.CC_LIBRARY,
                    ImmutableList.of(src("foo/zoo/library.cc")),
                    ImmutableList.of("OTHER=1")))
            .build();
    assertThatResolving(projectView2, targetMap2)
        .reusedConfigurations(noReusedConfigurations, "//foo/zoo:library");
  }

  @Test
  public void changeCompilerVersion_testIncrementalUpdateNoReuse() {
    ProjectView projectView = projectView(directories("foo/bar"), targets("//foo/bar:binary"));
    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(createCcToolchain())
            .addTarget(
                createCcTarget(
                    "//foo/bar:binary", Kind.CC_BINARY, ImmutableList.of(src("foo/bar/binary.cc"))))
            .build();

    ImmutableList<BlazeResolveConfiguration> noReusedConfigurations = ImmutableList.of();
    assertThatResolving(projectView, targetMap)
        .reusedConfigurations(noReusedConfigurations, "//foo/bar:binary");

    compilerVersionChecker.setCompilerVersion("cc modified version");
    assertThatResolving(projectView, targetMap)
        .reusedConfigurations(noReusedConfigurations, "//foo/bar:binary");
  }

  @Test
  public void brokenCompiler_collectsIssues() {
    ProjectView projectView = projectView(directories("foo/bar"), targets("//foo/bar:*"));
    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(createCcToolchain())
            .addTarget(
                createCcTarget(
                    "//foo/bar:binary", Kind.CC_BINARY, ImmutableList.of(src("foo/bar/binary.cc"))))
            .build();
    createVirtualFile("/root/foo/bar/binary.cc");

    compilerVersionChecker.setInjectFault(true);

    computeResolverResult(projectView, targetMap);
    errorCollector.assertIssueContaining(
        "Unable to check compiler version for \"/root/cc\".\n"
            + "injected fault\n"
            + "Check if running the compiler with --version works on the cmdline.");
  }

  private static ArtifactLocation src(String path) {
    return ArtifactLocation.builder().setRelativePath(path).setIsSource(true).build();
  }

  private static ArtifactLocation gen(String path) {
    return ArtifactLocation.builder().setRelativePath(path).setIsSource(false).build();
  }

  private static TargetIdeInfo.Builder createCcTarget(
      String label, Kind kind, ImmutableList<ArtifactLocation> sources) {
    return createCcTarget(label, kind, sources, ImmutableList.of());
  }

  private static TargetIdeInfo.Builder createCcTarget(
      String label,
      Kind kind,
      ImmutableList<ArtifactLocation> sources,
      ImmutableList<String> defines) {
    TargetIdeInfo.Builder targetInfo =
        TargetIdeInfo.builder().setLabel(label).setKind(kind).addDependency("//:toolchain");
    sources.forEach(targetInfo::addSource);
    return targetInfo.setCInfo(CIdeInfo.builder().addSources(sources).addLocalDefines(defines));
  }

  private static TargetIdeInfo.Builder createCcToolchain() {
    return TargetIdeInfo.builder()
        .setLabel("//:toolchain")
        .setKind(Kind.CC_TOOLCHAIN)
        .setCToolchainInfo(
            CToolchainIdeInfo.builder().setCppExecutable(new ExecutionRootPath("cc")));
  }

  private static ListSection<DirectoryEntry> directories(String... directories) {
    return ListSection.builder(DirectorySection.KEY)
        .addAll(
            Arrays.stream(directories)
                .map(directory -> DirectoryEntry.include(WorkspacePath.createIfValid(directory)))
                .collect(Collectors.toList()))
        .build();
  }

  private static ListSection<TargetExpression> targets(String... targets) {
    return ListSection.builder(TargetSection.KEY)
        .addAll(
            Arrays.stream(targets)
                .map(TargetExpression::fromStringSafe)
                .collect(Collectors.toList()))
        .build();
  }

  private static ProjectView projectView(
      ListSection<DirectoryEntry> directories, ListSection<TargetExpression> targets) {
    return ProjectView.builder().add(directories).add(targets).build();
  }

  private VirtualFile createVirtualFile(String path) {
    VirtualFile mockFile = mock(VirtualFile.class);
    when(mockFile.getPath()).thenReturn(path);
    when(mockFile.isValid()).thenReturn(true);
    when(mockFileSystem.findFileByIoFile(new File(path))).thenReturn(mockFile);
    return mockFile;
  }

  private void computeResolverResult(ProjectView projectView, TargetMap targetMap) {
    BlazeProjectData blazeProjectData =
        MockBlazeProjectDataBuilder.builder(workspaceRoot).setTargetMap(targetMap).build();
    resolverResult =
        resolver.update(
            context,
            workspaceRoot,
            ProjectViewSet.builder().add(projectView).build(),
            blazeProjectData,
            resolverResult);
  }

  private Subject assertThatResolving(ProjectView projectView, TargetMap targetMap) {
    computeResolverResult(projectView, targetMap);
    errorCollector.assertNoIssues();
    return new Subject() {
      @Override
      public void producesConfigurationsFor(String... expected) {
        List<String> targets =
            resolverResult
                .getAllConfigurations()
                .stream()
                .map(configuration -> configuration.getDisplayName(false))
                .collect(Collectors.toList());
        assertThat(targets).containsExactly((Object[]) expected);
      }

      @Override
      public void producesNoConfigurations() {
        assertThat(resolverResult.getAllConfigurations()).isEmpty();
      }

      @Override
      public void reusedConfigurations(
          Collection<BlazeResolveConfiguration> expectedReused, String... expectedNotReused) {
        Collection<BlazeResolveConfiguration> currentConfigurations =
            resolverResult.getAllConfigurations();
        assertContainsAllInIdentity(expectedReused, currentConfigurations);
        List<String> notReusedTargets =
            currentConfigurations
                .stream()
                .filter(
                    configuration ->
                        expectedReused
                            .stream()
                            .noneMatch(reusedConfig -> configuration == reusedConfig))
                .map(configuration -> configuration.getDisplayName(false))
                .collect(Collectors.toList());
        assertThat(notReusedTargets).containsExactly((Object[]) expectedNotReused);
      }

      // In newer truth libraries, we could use:
      // assertThat(actual).comparingElementsUsing(IdentityCorrespondence).containsAllIn(expected)
      // but that isn't available in truth 0.30 from older plugin APIs.
      private <T> void assertContainsAllInIdentity(Collection<T> expected, Collection<T> actual) {
        for (T expectedItem : expected) {
          assertThat(actual.stream().anyMatch(actualItem -> actualItem == expectedItem)).isTrue();
        }
      }
    };
  }

  private interface Subject {
    void producesConfigurationsFor(String... expected);

    void producesNoConfigurations();

    void reusedConfigurations(Collection<BlazeResolveConfiguration> reused, String... notReused);
  }
}
