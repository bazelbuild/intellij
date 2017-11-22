/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.cpp;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
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
import com.intellij.mock.MockPsiManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.impl.ProgressManagerImpl;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.impl.StubVirtualFile;
import com.intellij.psi.PsiManager;
import com.jetbrains.cidr.lang.OCLanguageKind;
import com.jetbrains.cidr.lang.workspace.OCResolveRootAndConfiguration;
import com.jetbrains.cidr.lang.workspace.headerRoots.HeadersSearchRoot;
import com.jetbrains.cidr.lang.workspace.headerRoots.IncludedHeadersRoot;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests that we group equivalent {@link BlazeResolveConfiguration}s. */
@RunWith(JUnit4.class)
public class BlazeResolveConfigurationEquivalenceTest extends BlazeTestCase {
  private final BlazeContext context = new BlazeContext();
  private final ErrorCollector errorCollector = new ErrorCollector();
  private final WorkspaceRoot workspaceRoot = new WorkspaceRoot(new File("/root"));

  private BlazeConfigurationResolver resolver;
  private BlazeConfigurationResolverResult resolverResult;
  private LocalFileSystem mockFileSystem;

  @Override
  protected void initTest(Container applicationServices, Container projectServices) {
    super.initTest(applicationServices, projectServices);
    applicationServices.register(BlazeExecutor.class, new MockBlazeExecutor());
    applicationServices.register(ExperimentService.class, new MockExperimentService());
    applicationServices.register(
        CompilerVersionChecker.class, new MockCompilerVersionChecker("1234"));

    applicationServices.register(ProgressManager.class, new ProgressManagerImpl());
    applicationServices.register(VirtualFileManager.class, mock(VirtualFileManager.class));
    mockFileSystem = mock(LocalFileSystem.class);
    applicationServices.register(
        VirtualFileSystemProvider.class, mock(VirtualFileSystemProvider.class));
    when(VirtualFileSystemProvider.getInstance().getSystem()).thenReturn(mockFileSystem);

    projectServices.register(PsiManager.class, new MockPsiManager(project));
    projectServices.register(BlazeImportSettingsManager.class, new BlazeImportSettingsManager());

    BuildSystemProvider buildSystemProvider = new BazelBuildSystemProvider();
    registerExtensionPoint(BuildSystemProvider.EP_NAME, BuildSystemProvider.class)
        .registerExtension(buildSystemProvider);
    BlazeImportSettingsManager.getInstance(getProject())
        .setImportSettings(
            new BlazeImportSettings("", "", "", "", buildSystemProvider.buildSystem()));

    context.addOutputSink(IssueOutput.class, errorCollector);

    resolver = new BlazeConfigurationResolver(project);
    resolverResult = BlazeConfigurationResolverResult.empty(project);
  }

  @Test
  public void testEmptyConfigurations() {
    ProjectView projectView =
        projectView(
            directories("foo/bar"), targets("//foo/bar:one", "//foo/bar:two", "//foo/bar:three"));
    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(createCcToolchain())
            .addTarget(
                createCcTarget(
                    "//foo/bar:one",
                    Kind.CC_BINARY,
                    sources("foo/bar/one.cc"),
                    defines(),
                    includes()))
            .addTarget(
                createCcTarget(
                    "//foo/bar:two",
                    Kind.CC_BINARY,
                    sources("foo/bar/two.cc"),
                    defines(),
                    includes()))
            .addTarget(
                createCcTarget(
                    "//foo/bar:three",
                    Kind.CC_BINARY,
                    sources("foo/bar/three.cc"),
                    defines(),
                    includes()))
            .build();
    List<BlazeResolveConfiguration> configurations = resolve(projectView, targetMap);
    assertThat(configurations).hasSize(1);
    assertThat(get(configurations, "//foo/bar:one and 2 other target(s)")).isNotNull();
    for (BlazeResolveConfiguration configuration : configurations) {
      assertThat(configuration.getProjectHeadersRoots().getRoots()).isEmpty();
      assertThat(getHeaders(configuration, OCLanguageKind.CPP)).isEmpty();
      assertThat(configuration.getCompilerMacros()).isEqualTo(macros());
    }
  }

  @Test
  public void testDefines() {
    ProjectView projectView =
        projectView(
            directories("foo/bar"), targets("//foo/bar:one", "//foo/bar:two", "//foo/bar:three"));
    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(createCcToolchain())
            .addTarget(
                createCcTarget(
                    "//foo/bar:one",
                    Kind.CC_BINARY,
                    sources("foo/bar/one.cc"),
                    defines("SAME=1"),
                    includes()))
            .addTarget(
                createCcTarget(
                    "//foo/bar:two",
                    Kind.CC_BINARY,
                    sources("foo/bar/two.cc"),
                    defines("SAME=1"),
                    includes()))
            .addTarget(
                createCcTarget(
                    "//foo/bar:three",
                    Kind.CC_BINARY,
                    sources("foo/bar/three.cc"),
                    defines("DIFFERENT=1"),
                    includes()))
            .build();
    List<BlazeResolveConfiguration> configurations = resolve(projectView, targetMap);
    assertThat(configurations).hasSize(2);
    assertThat(get(configurations, "//foo/bar:one and 1 other target(s)").getCompilerMacros())
        .isEqualTo(macros("SAME=1"));
    assertThat(get(configurations, "//foo/bar:three").getCompilerMacros())
        .isEqualTo(macros("DIFFERENT=1"));
    for (BlazeResolveConfiguration configuration : configurations) {
      assertThat(configuration.getProjectHeadersRoots().getRoots()).isEmpty();
      assertThat(getHeaders(configuration, OCLanguageKind.CPP)).isEmpty();
    }
  }

  @Test
  public void testIncludes() {
    ProjectView projectView =
        projectView(
            directories("foo/bar"), targets("//foo/bar:one", "//foo/bar:two", "//foo/bar:three"));
    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(createCcToolchain())
            .addTarget(
                createCcTarget(
                    "//foo/bar:one",
                    Kind.CC_BINARY,
                    sources("foo/bar/one.cc"),
                    defines(),
                    includes("foo/same")))
            .addTarget(
                createCcTarget(
                    "//foo/bar:two",
                    Kind.CC_BINARY,
                    sources("foo/bar/two.cc"),
                    defines(),
                    includes("foo/same")))
            .addTarget(
                createCcTarget(
                    "//foo/bar:three",
                    Kind.CC_BINARY,
                    sources("foo/bar/three.cc"),
                    defines(),
                    includes("foo/different")))
            .build();
    VirtualFile includeSame = createVirtualFile("/root/foo/same");
    VirtualFile includeDifferent = createVirtualFile("/root/foo/different");
    List<BlazeResolveConfiguration> configurations = resolve(projectView, targetMap);
    assertThat(configurations).hasSize(2);
    assertThat(
            getHeaders(
                get(configurations, "//foo/bar:one and 1 other target(s)"), OCLanguageKind.CPP))
        .containsExactly(header(includeSame));
    assertThat(getHeaders(get(configurations, "//foo/bar:three"), OCLanguageKind.CPP))
        .containsExactly(header(includeDifferent));
    for (BlazeResolveConfiguration configuration : configurations) {
      assertThat(configuration.getProjectHeadersRoots().getRoots()).isEmpty();
      assertThat(configuration.getCompilerMacros()).isEqualTo(macros());
    }
  }

  // Test a series of permutations of labels a, b, c, d.
  // Initial state is {a=1, b=1, c=1, d=0}, and we flip some of the 1 to 0.
  private TargetMap incrementalUpdateTestCaseInitialTargetMap() {
    return TargetMapBuilder.builder()
        .addTarget(createCcToolchain())
        .addTarget(
            createCcTarget(
                "//foo/bar:a",
                Kind.CC_BINARY,
                sources("foo/bar/a.cc"),
                defines("SAME=1"),
                includes()))
        .addTarget(
            createCcTarget(
                "//foo/bar:b",
                Kind.CC_BINARY,
                sources("foo/bar/b.cc"),
                defines("SAME=1"),
                includes()))
        .addTarget(
            createCcTarget(
                "//foo/bar:c",
                Kind.CC_BINARY,
                sources("foo/bar/c.cc"),
                defines("SAME=1"),
                includes()))
        .addTarget(
            createCcTarget(
                "//foo/bar:d",
                Kind.CC_BINARY,
                sources("foo/bar/d.cc"),
                defines("DIFFERENT=1"),
                includes()))
        .build();
  }

  // TODO(jvoung): This could be a separate Parameterized test.
  private static final Map<List<String>, ReusedConfigurationExpectations>
      permutationsAndExpectations =
          ImmutableMap.<List<String>, ReusedConfigurationExpectations>builder()
              .put(
                  ImmutableList.of("a"),
                  // Since we already had a config at 1 and one at 0, flipping any 1 to 0 will
                  // always
                  // result in reuse. The old configurations will get renamed.
                  new ReusedConfigurationExpectations(
                      ImmutableList.of(
                          "//foo/bar:a and 1 other target(s)", "//foo/bar:b and 1 other target(s)"),
                      ImmutableList.of()))
              .put(
                  ImmutableList.of("b"),
                  new ReusedConfigurationExpectations(
                      ImmutableList.of(
                          "//foo/bar:a and 1 other target(s)", "//foo/bar:b and 1 other target(s)"),
                      ImmutableList.of()))
              .put(
                  ImmutableList.of("c"),
                  new ReusedConfigurationExpectations(
                      ImmutableList.of(
                          "//foo/bar:a and 1 other target(s)", "//foo/bar:c and 1 other target(s)"),
                      ImmutableList.of()))
              .put(
                  ImmutableList.of("a", "b"),
                  new ReusedConfigurationExpectations(
                      ImmutableList.of("//foo/bar:a and 2 other target(s)", "//foo/bar:c"),
                      ImmutableList.of()))
              .put(
                  ImmutableList.of("b", "c"),
                  new ReusedConfigurationExpectations(
                      ImmutableList.of("//foo/bar:a", "//foo/bar:b and 2 other target(s)"),
                      ImmutableList.of()))
              .put(
                  ImmutableList.of("a", "c"),
                  new ReusedConfigurationExpectations(
                      ImmutableList.of("//foo/bar:a and 2 other target(s)", "//foo/bar:b"),
                      ImmutableList.of()))
              .put(
                  ImmutableList.of("a", "b", "c"),
                  new ReusedConfigurationExpectations(
                      ImmutableList.of("//foo/bar:a and 3 other target(s)"), ImmutableList.of()))
              .build();

  @Test
  public void changeDefines_testIncrementalUpdate_0() {
    Map.Entry<List<String>, ReusedConfigurationExpectations> testCase =
        Iterables.get(permutationsAndExpectations.entrySet(), 0);
    do_changeDefines_testIncrementalUpdate(testCase.getKey(), testCase.getValue());
  }

  @Test
  public void changeDefines_testIncrementalUpdate_1() {
    Map.Entry<List<String>, ReusedConfigurationExpectations> testCase =
        Iterables.get(permutationsAndExpectations.entrySet(), 1);
    do_changeDefines_testIncrementalUpdate(testCase.getKey(), testCase.getValue());
  }

  @Test
  public void changeDefines_testIncrementalUpdate_2() {
    Map.Entry<List<String>, ReusedConfigurationExpectations> testCase =
        Iterables.get(permutationsAndExpectations.entrySet(), 2);
    do_changeDefines_testIncrementalUpdate(testCase.getKey(), testCase.getValue());
  }

  @Test
  public void changeDefines_testIncrementalUpdate_3() {
    Map.Entry<List<String>, ReusedConfigurationExpectations> testCase =
        Iterables.get(permutationsAndExpectations.entrySet(), 3);
    do_changeDefines_testIncrementalUpdate(testCase.getKey(), testCase.getValue());
  }

  @Test
  public void changeDefines_testIncrementalUpdate_4() {
    Map.Entry<List<String>, ReusedConfigurationExpectations> testCase =
        Iterables.get(permutationsAndExpectations.entrySet(), 4);
    do_changeDefines_testIncrementalUpdate(testCase.getKey(), testCase.getValue());
  }

  @Test
  public void changeDefines_testIncrementalUpdate_5() {
    Map.Entry<List<String>, ReusedConfigurationExpectations> testCase =
        Iterables.get(permutationsAndExpectations.entrySet(), 5);
    do_changeDefines_testIncrementalUpdate(testCase.getKey(), testCase.getValue());
  }

  @Test
  public void changeDefines_testIncrementalUpdate_6() {
    Map.Entry<List<String>, ReusedConfigurationExpectations> testCase =
        Iterables.get(permutationsAndExpectations.entrySet(), 6);
    do_changeDefines_testIncrementalUpdate(testCase.getKey(), testCase.getValue());
    assertThat(permutationsAndExpectations.size()).isEqualTo(7);
  }

  private void do_changeDefines_testIncrementalUpdate(
      List<String> labelsToFlip, ReusedConfigurationExpectations expectation) {
    ProjectView projectView = projectView(directories("foo/bar"), targets("//foo/bar:...:all"));
    List<BlazeResolveConfiguration> configurations =
        resolve(projectView, incrementalUpdateTestCaseInitialTargetMap());
    assertThat(configurations).hasSize(2);
    assertThat(get(configurations, "//foo/bar:a and 2 other target(s)")).isNotNull();
    assertThat(get(configurations, "//foo/bar:d")).isNotNull();

    TargetMapBuilder targetMapBuilder = TargetMapBuilder.builder().addTarget(createCcToolchain());
    for (String target : ImmutableList.of("a", "b", "c")) {
      if (labelsToFlip.contains(target)) {
        targetMapBuilder.addTarget(
            createCcTarget(
                String.format("//foo/bar:%s", target),
                Kind.CC_BINARY,
                sources(String.format("foo/bar/%s.cc", target)),
                defines("DIFFERENT=1"),
                includes()));
      } else {
        targetMapBuilder.addTarget(
            createCcTarget(
                String.format("//foo/bar:%s", target),
                Kind.CC_BINARY,
                sources(String.format("foo/bar/%s.cc", target)),
                defines("SAME=1"),
                includes()));
      }
    }
    targetMapBuilder.addTarget(
        createCcTarget(
            "//foo/bar:d",
            Kind.CC_BINARY,
            sources("foo/bar/d.cc"),
            defines("DIFFERENT=1"),
            includes()));
    List<BlazeResolveConfiguration> newConfigurations =
        resolve(projectView, targetMapBuilder.build());
    assertReusedConfigs(configurations, newConfigurations, expectation);
  }

  @Test
  public void changeDefinesWithSameStructure_testIncrementalUpdate() {
    ProjectView projectView = projectView(directories("foo/bar"), targets("//foo/bar:...:all"));
    TargetMap targetMap = incrementalUpdateTestCaseInitialTargetMap();
    List<BlazeResolveConfiguration> configurations = resolve(projectView, targetMap);
    assertThat(configurations).hasSize(2);
    assertThat(get(configurations, "//foo/bar:a and 2 other target(s)")).isNotNull();
    assertThat(get(configurations, "//foo/bar:d")).isNotNull();

    targetMap =
        TargetMapBuilder.builder()
            .addTarget(createCcToolchain())
            .addTarget(
                createCcTarget(
                    "//foo/bar:a",
                    Kind.CC_BINARY,
                    sources("foo/bar/a.cc"),
                    defines("CHANGED=1"),
                    includes()))
            .addTarget(
                createCcTarget(
                    "//foo/bar:b",
                    Kind.CC_BINARY,
                    sources("foo/bar/b.cc"),
                    defines("CHANGED=1"),
                    includes()))
            .addTarget(
                createCcTarget(
                    "//foo/bar:c",
                    Kind.CC_BINARY,
                    sources("foo/bar/c.cc"),
                    defines("CHANGED=1"),
                    includes()))
            .addTarget(
                createCcTarget(
                    "//foo/bar:d",
                    Kind.CC_BINARY,
                    sources("foo/bar/d.cc"),
                    defines("DIFFERENT=1"),
                    includes()))
            .build();
    List<BlazeResolveConfiguration> newConfigurations = resolve(projectView, targetMap);
    assertThat(newConfigurations).hasSize(2);
    assertReusedConfigs(
        configurations,
        newConfigurations,
        new ReusedConfigurationExpectations(
            ImmutableList.of("//foo/bar:d"),
            ImmutableList.of("//foo/bar:a and 2 other target(s)")));
  }

  @Test
  public void changeDefinesMakeAllSame_testIncrementalUpdate() {
    ProjectView projectView = projectView(directories("foo/bar"), targets("//foo/bar:...:all"));
    TargetMap targetMap = incrementalUpdateTestCaseInitialTargetMap();
    List<BlazeResolveConfiguration> configurations = resolve(projectView, targetMap);
    assertThat(configurations).hasSize(2);
    assertThat(get(configurations, "//foo/bar:a and 2 other target(s)")).isNotNull();
    assertThat(get(configurations, "//foo/bar:d")).isNotNull();

    targetMap =
        TargetMapBuilder.builder()
            .addTarget(createCcToolchain())
            .addTarget(
                createCcTarget(
                    "//foo/bar:a",
                    Kind.CC_BINARY,
                    sources("foo/bar/a.cc"),
                    defines("SAME=1"),
                    includes()))
            .addTarget(
                createCcTarget(
                    "//foo/bar:b",
                    Kind.CC_BINARY,
                    sources("foo/bar/b.cc"),
                    defines("SAME=1"),
                    includes()))
            .addTarget(
                createCcTarget(
                    "//foo/bar:c",
                    Kind.CC_BINARY,
                    sources("foo/bar/c.cc"),
                    defines("SAME=1"),
                    includes()))
            .addTarget(
                createCcTarget(
                    "//foo/bar:d",
                    Kind.CC_BINARY,
                    sources("foo/bar/d.cc"),
                    defines("SAME=1"),
                    includes()))
            .build();
    List<BlazeResolveConfiguration> newConfigurations = resolve(projectView, targetMap);
    assertThat(newConfigurations).hasSize(1);
    // What used to be "//foo/bar:a and 2 other target(s)" will be renamed to
    // "//foo/bar:a and 3 other target(s)" and reused.
    assertReusedConfigs(
        configurations,
        newConfigurations,
        new ReusedConfigurationExpectations(
            ImmutableList.of("//foo/bar:a and 3 other target(s)"), ImmutableList.of()));
  }

  private static List<ArtifactLocation> sources(String... paths) {
    return Arrays.stream(paths)
        .map(path -> ArtifactLocation.builder().setRelativePath(path).setIsSource(true).build())
        .collect(Collectors.toList());
  }

  private static List<String> defines(String... defines) {
    return Arrays.asList(defines);
  }

  private static List<ExecutionRootPath> includes(String... paths) {
    return Arrays.stream(paths).map(ExecutionRootPath::new).collect(Collectors.toList());
  }

  private static TargetIdeInfo.Builder createCcTarget(
      String label,
      Kind kind,
      List<ArtifactLocation> sources,
      List<String> defines,
      List<ExecutionRootPath> includes) {
    TargetIdeInfo.Builder targetInfo =
        TargetIdeInfo.builder().setLabel(label).setKind(kind).addDependency("//:toolchain");
    sources.forEach(targetInfo::addSource);
    return targetInfo.setCInfo(
        CIdeInfo.builder()
            .addSources(sources)
            .addLocalDefines(defines)
            .addLocalIncludeDirectories(includes));
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

  private List<BlazeResolveConfiguration> resolve(ProjectView projectView, TargetMap targetMap) {
    resolverResult =
        resolver.update(
            context,
            workspaceRoot,
            ProjectViewSet.builder().add(projectView).build(),
            MockBlazeProjectDataBuilder.builder(workspaceRoot).setTargetMap(targetMap).build(),
            resolverResult);
    errorCollector.assertNoIssues();
    return resolverResult.getAllConfigurations();
  }

  private static BlazeResolveConfiguration get(
      List<BlazeResolveConfiguration> configurations, String name) {
    List<BlazeResolveConfiguration> filteredConfigurations =
        configurations
            .stream()
            .filter(c -> c.getDisplayName(false).equals(name))
            .collect(Collectors.toList());
    assertWithMessage(
            String.format(
                "%s contains %s",
                configurations
                    .stream()
                    .map(c -> c.getDisplayName(false))
                    .collect(Collectors.toList()),
                name))
        .that(filteredConfigurations)
        .hasSize(1);
    return filteredConfigurations.get(0);
  }

  private BlazeCompilerMacros macros(String... defines) {
    return new BlazeCompilerMacros(
        project, null, null, ImmutableList.copyOf(defines), ImmutableMap.of());
  }

  private HeadersSearchRoot header(VirtualFile include) {
    return new IncludedHeadersRoot(project, include, false, true);
  }

  private static List<HeadersSearchRoot> getHeaders(
      BlazeResolveConfiguration configuration, OCLanguageKind languageKind) {
    return configuration
        .getLibraryHeadersRoots(new OCResolveRootAndConfiguration(configuration, languageKind))
        .getRoots();
  }

  private VirtualFile createVirtualFile(String path) {
    VirtualFile stub =
        new StubVirtualFile() {
          @Override
          public boolean isValid() {
            return true;
          }
        };
    when(mockFileSystem.findFileByIoFile(new File(path))).thenReturn(stub);
    return stub;
  }

  private static void assertReusedConfigs(
      List<BlazeResolveConfiguration> oldConfigurations,
      List<BlazeResolveConfiguration> newConfigurations,
      ReusedConfigurationExpectations expected) {
    for (String label : expected.reusedLabels) {
      assertWithMessage(String.format("Checking that %s is reused", label))
          .that(get(newConfigurations, label))
          .isSameAs(get(oldConfigurations, label));
    }
    for (String label : expected.notReusedLabels) {
      assertWithMessage(String.format("Checking that %s is NOT reused", label))
          .that(get(newConfigurations, label))
          .isNotSameAs(get(oldConfigurations, label));
    }
  }

  private static class ReusedConfigurationExpectations {
    final ImmutableCollection<String> reusedLabels;
    final ImmutableCollection<String> notReusedLabels;

    ReusedConfigurationExpectations(
        ImmutableCollection<String> reusedLabels, ImmutableCollection<String> notReusedLabels) {
      this.reusedLabels = reusedLabels;
      this.notReusedLabels = notReusedLabels;
    }
  }
}
