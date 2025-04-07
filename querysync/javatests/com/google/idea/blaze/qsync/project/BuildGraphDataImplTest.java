/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.qsync.project;

import static com.google.common.truth.Truth.assertThat;
import static com.google.idea.blaze.qsync.QuerySyncTestUtils.NOOP_CONTEXT;
import static com.google.idea.blaze.qsync.QuerySyncTestUtils.getQuerySummary;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.truth.Expect;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.qsync.BlazeQueryParser;
import com.google.idea.blaze.qsync.testdata.BuildGraphs;
import com.google.idea.blaze.qsync.testdata.TestData;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class BuildGraphDataImplTest {
  private static final Path TEST_ROOT =
      Path.of("querysync/javatests/com/google/idea/blaze/qsync");

  private static final Path TESTDATA_ROOT = TEST_ROOT.resolve("testdata");

  @Rule
  public Expect expect = Expect.create();

  @Test
  public void pathToLabel() {
    BuildGraphDataImpl.Storage.Builder builder = BuildGraphDataImpl.builder();
    builder.sourceFileLabelsBuilder()
      .add(Label.of("//:BUILD"))
      .add(Label.of("//nested:BUILD"))
      .add(Label.of("//nested:file.txt"))
      .add(Label.of("//nested/inner:BUILD"))
      .add(Label.of("//nested/inner:deep/file.txt"));

    builder.allTargetLabelsBuilder()
      .add(Label.of("//:target"))
      .add(Label.of("//nested:nested"))
      .add(Label.of("//nested/inner:inner"));

    builder.projectDeps(ImmutableSet.of());
    BuildGraphData graph = builder.build();
    expect.that(graph.pathToLabel(Path.of("abc.txt"))).isEqualTo(Optional.of(Label.of("//:abc.txt")));
    expect.that(graph.pathToLabel(Path.of("BUILD"))).isEqualTo(Optional.of(Label.of("//:BUILD")));
    expect.that(graph.pathToLabel(Path.of("nested/abc.txt"))).isEqualTo(Optional.of(Label.of("//nested:abc.txt")));
    expect.that(graph.pathToLabel(Path.of("nested/file.txt"))).isEqualTo(Optional.of(Label.of("//nested:file.txt")));
    expect.that(graph.pathToLabel(Path.of("nested/BUILD"))).isEqualTo(Optional.of(Label.of("//nested:BUILD")));
    expect.that(graph.pathToLabel(Path.of("nested/inner/abc.txt"))).isEqualTo(Optional.of(Label.of("//nested/inner:abc.txt")));
    expect.that(graph.pathToLabel(Path.of("nested/inner/deep/file.txt"))).isEqualTo(Optional.of(Label.of("//nested/inner:deep/file.txt")));
    expect.that(graph.pathToLabel(Path.of("nested/inner/BUILD"))).isEqualTo(Optional.of(Label.of("//nested/inner:BUILD")));
    expect.that(graph.pathToLabel(Path.of("other/abc.txt"))).isEqualTo(Optional.of(Label.of("//:other/abc.txt")));
    expect.that(graph.pathToLabel(Path.of("other/BUILD"))).isEqualTo(Optional.of(Label.of("//:other/BUILD")));
    expect.that(graph.pathToLabel(Path.of("other/inner/abc.txt"))).isEqualTo(Optional.of(Label.of("//:other/inner/abc.txt")));
    expect.that(graph.pathToLabel(Path.of("other/inner/BUILD"))).isEqualTo(Optional.of(Label.of("//:other/inner/BUILD")));
  }

  @Test
  public void sourceFileToLabel() {
    BuildGraphDataImpl.Storage.Builder builder = BuildGraphDataImpl.builder();
    builder.sourceFileLabelsBuilder()
      .add(Label.of("//:BUILD"))
      .add(Label.of("//nested:BUILD"))
      .add(Label.of("//nested:file.txt"))
      .add(Label.of("//nested/inner:BUILD"))
      .add(Label.of("//nested/inner:deep/file.txt"));

    builder.allTargetLabelsBuilder()
      .add(Label.of("//:target"))
      .add(Label.of("//nested:nested"))
      .add(Label.of("//nested/inner:inner"));

    builder.projectDeps(ImmutableSet.of());
    BuildGraphData graph = builder.build();
    expect.that(graph.sourceFileToLabel(Path.of("abc.txt"))).isEqualTo(Optional.empty());
    expect.that(graph.sourceFileToLabel(Path.of("BUILD"))).isEqualTo(Optional.of(Label.of("//:BUILD")));
    expect.that(graph.sourceFileToLabel(Path.of("nested/abc.txt"))).isEqualTo(Optional.empty());
    expect.that(graph.sourceFileToLabel(Path.of("nested/file.txt"))).isEqualTo(Optional.of(Label.of("//nested:file.txt")));
    expect.that(graph.sourceFileToLabel(Path.of("nested/BUILD"))).isEqualTo(Optional.of(Label.of("//nested:BUILD")));
    expect.that(graph.sourceFileToLabel(Path.of("nested/inner/abc.txt"))).isEqualTo(Optional.empty());
    expect.that(graph.sourceFileToLabel(Path.of("nested/inner/deep/file.txt"))).isEqualTo(Optional.of(Label.of("//nested/inner:deep/file.txt")));
    expect.that(graph.sourceFileToLabel(Path.of("nested/inner/BUILD"))).isEqualTo(Optional.of(Label.of("//nested/inner:BUILD")));
    expect.that(graph.sourceFileToLabel(Path.of("other/abc.txt"))).isEqualTo(Optional.empty());
    expect.that(graph.sourceFileToLabel(Path.of("other/BUILD"))).isEqualTo(Optional.empty());
    expect.that(graph.sourceFileToLabel(Path.of("other/inner/abc.txt"))).isEqualTo(Optional.empty());
    expect.that(graph.sourceFileToLabel(Path.of("other/inner/BUILD"))).isEqualTo(Optional.empty());
  }

  @Test
  public void testJavaLibraryNoDeps() throws Exception {
    BuildGraphDataImpl graph =
        new BlazeQueryParser(
                getQuerySummary(TestData.JAVA_LIBRARY_NO_DEPS_QUERY),
                NOOP_CONTEXT,
                ImmutableSet.of())
            .parseForTesting();
    var expectedPackagePath = TESTDATA_ROOT.resolve("nodeps");
    assertThat(graph.allTargets())
        .containsExactly(Label.fromWorkspacePackageAndName("", expectedPackagePath, "nodeps"));
    assertThat(graph.storage().sourceFileLabels())
        .containsExactly(
            Label.fromWorkspacePackageAndName("", expectedPackagePath, "TestClassNoDeps.java"),
                     Label.fromWorkspacePackageAndName("", expectedPackagePath, "BUILD"));
    assertThat(graph.getJavaSourceFiles())
        .containsExactly(TESTDATA_ROOT.resolve("nodeps/TestClassNoDeps.java"));
    assertThat(graph.getAndroidSourceFiles()).isEmpty();
    assertThat(graph.getSourceFileOwners(TESTDATA_ROOT.resolve("nodeps/TestClassNoDeps.java")))
        .containsExactly(Label.fromWorkspacePackageAndName("", expectedPackagePath, "nodeps"));
    assertThat(graph.getExternalDependencies(ImmutableList.of(Label.of("//" + TESTDATA_ROOT + "/nodeps:nodeps"))))
        .isEmpty();
    assertThat(graph.getProjectTarget(Label.fromWorkspacePackageAndName("", expectedPackagePath, "nodeps")).languages())
        .containsExactly(QuerySyncLanguage.JVM);
  }

  @Test
  public void testJavaLibraryExternalDep() throws Exception {
    BuildGraphDataImpl graph =
        new BlazeQueryParser(
                getQuerySummary(TestData.JAVA_LIBRARY_EXTERNAL_DEP_QUERY),
                NOOP_CONTEXT,
                ImmutableSet.of())
            .parseForTesting();
    assertThat(
            graph.getExternalDependencies(ImmutableList.of(Label.of("//" + TESTDATA_ROOT.resolve("externaldep:externaldep")))))
        .containsExactly(TestData.JAVA_LIBRARY_NO_DEPS_QUERY.getAssumedOnlyLabel());
  }

  @Test
  public void testJavaLibraryInternalDep() throws Exception {
    BuildGraphDataImpl graph =
        new BlazeQueryParser(
                getQuerySummary(TestData.JAVA_LIBRARY_INTERNAL_DEP_QUERY),
                NOOP_CONTEXT,
                ImmutableSet.of())
            .parseForTesting();
    // Sanity check:
    assertThat(graph.storage().sourceFileLabels())
        .contains(Label.fromWorkspacePackageAndName("", TESTDATA_ROOT.resolve("nodeps"), "TestClassNoDeps.java"));
    assertThat(
            graph.getExternalDependencies(ImmutableList.of(Label.of("//" + TESTDATA_ROOT.resolve("internaldep:internaldep")))))
        .isEmpty();
  }

  @Test
  public void testJavaLibraryTransientDep() throws Exception {
    BuildGraphDataImpl graph =
        new BlazeQueryParser(
                getQuerySummary(TestData.JAVA_LIBRARY_TRANSITIVE_DEP_QUERY),
                NOOP_CONTEXT,
                ImmutableSet.of())
            .parseForTesting();
    // Sanity check:
    assertThat(graph.storage().sourceFileLabels())
        .contains(Label.fromWorkspacePackageAndName("", TESTDATA_ROOT.resolve("externaldep"), "TestClassExternalDep.java"));
    assertThat(
            graph.getExternalDependencies(ImmutableList.of(Label.of("//" + TESTDATA_ROOT.resolve("transitivedep:transitivedep")))))
        .containsExactly(TestData.JAVA_LIBRARY_NO_DEPS_QUERY.getAssumedOnlyLabel());
  }

  @Test
  public void testJavaLibraryProtoDep() throws Exception {
    BuildGraphDataImpl graph =
        new BlazeQueryParser(
                getQuerySummary(TestData.JAVA_LIBRARY_PROTO_DEP_QUERY),
                NOOP_CONTEXT,
                ImmutableSet.of())
            .parseForTesting();
    Path expectedPackagePath = TESTDATA_ROOT.resolve("protodep");
        assertThat(graph.getExternalDependencies(ImmutableList.of(Label.of("//" + TESTDATA_ROOT.resolve("protodep:protodep")))))
        .containsExactly(
            Label.fromWorkspacePackageAndName("", expectedPackagePath, "proto_java_proto"));
        assertThat(graph.getExternalDependencies(ImmutableList.of(Label.of("//" + TESTDATA_ROOT.resolve("protodep:indirect_protodep")))))
        .containsExactly(
            Label.fromWorkspacePackageAndName("", expectedPackagePath, "indirect_proto_java_proto"));

    Path protoSourceFilePath = expectedPackagePath.resolve("testproto.proto");

    Collection<ProjectTarget> firstConsumingTargets =
        graph.getFirstReverseDepsOfType(protoSourceFilePath, ImmutableSet.of("java_proto_library"));
    assertThat(firstConsumingTargets.stream().map(ProjectTarget::label))
        .containsExactly(
            Label.fromWorkspacePackageAndName("", expectedPackagePath, "proto_java_proto"),
            Label.fromWorkspacePackageAndName("", expectedPackagePath, "indirect_proto_java_proto"));
  }

  @Test
  public void testDoesDependencyPathContainRules() throws Exception {
    BuildGraphDataImpl graph =
        new BlazeQueryParser(
                getQuerySummary(TestData.DOES_DEPENDENCY_PATH_CONTAIN_RULES),
                NOOP_CONTEXT,
                ImmutableSet.of())
            .parseForTesting();

    assertThat(
        graph.doesDependencyPathContainRules(
            TESTDATA_ROOT.resolve("deppathkinds/testproto.proto"),
            TESTDATA_ROOT.resolve("deppathkinds/TestClassProtoDep.java"),
            ImmutableSet.of("java_proto_library")))
        .isTrue();

    assertThat(
        graph.doesDependencyPathContainRules(
            TESTDATA_ROOT.resolve("deppathkinds/testproto.proto"),
            TESTDATA_ROOT.resolve("deppathkinds/TestClassProtoDep.java"),
            ImmutableSet.of("java_lite_proto_library")))
        .isFalse();

    assertThat(
        graph.doesDependencyPathContainRules(
            TESTDATA_ROOT.resolve("deppathkinds/testproto.proto"),
            TESTDATA_ROOT.resolve("deppathkinds/TestClassProtoDep.java"),
            ImmutableSet.of("java_library")))
        .isTrue();

    assertThat(
        graph.doesDependencyPathContainRules(
            TESTDATA_ROOT.resolve("deppathkinds/testproto.proto"),
            TESTDATA_ROOT.resolve("deppathkinds/TestClassProtoDep.java"),
            ImmutableSet.of("proto_library")))
        .isTrue();
  }

  @Test
  public void testJavaLibraryMultiTargets() throws Exception {
    BuildGraphDataImpl graph =
        new BlazeQueryParser(
                getQuerySummary(TestData.JAVA_LIBRARY_MULTI_TARGETS),
                NOOP_CONTEXT,
                ImmutableSet.of())
            .parseForTesting();
    Path expectedPackagePath = TESTDATA_ROOT.resolve("multitarget");
    assertThat(graph.allTargets())
        .containsExactly(
            Label.fromWorkspacePackageAndName("", expectedPackagePath, "nodeps"),
            Label.fromWorkspacePackageAndName("", expectedPackagePath, "externaldep"));
    // Sanity check:
    assertThat(graph.getJavaSourceFiles())
        .contains(expectedPackagePath.resolve("TestClassSingleTarget.java"));
    assertThat(
            graph.getSourceFileOwners(expectedPackagePath.resolve("TestClassMultiTarget.java")))
        .containsExactly(
            Label.fromWorkspacePackageAndName("", expectedPackagePath, "nodeps"),
            Label.fromWorkspacePackageAndName("", expectedPackagePath, "externaldep"));
    assertThat(
        graph.getExternalDependencies(ImmutableList.of(Label.of("//" + TESTDATA_ROOT.resolve("multitarget:externaldep")))))
        .contains(Label.of("@@rules_jvm_external++maven+com_google_guava_guava//jar:jar"));
    assertThat(
            graph.getExternalDependencies(ImmutableList.of(Label.of("//" + TESTDATA_ROOT.resolve("multitarget:nodeps")))))
        .isEmpty();
  }

  @Test
  public void testJavaLibraryExportingExternalTargets() throws Exception {
    BuildGraphDataImpl graph =
        new BlazeQueryParser(
                getQuerySummary(TestData.JAVA_EXPORTED_DEP_QUERY), NOOP_CONTEXT, ImmutableSet.of())
            .parseForTesting();
    Path sourceFile = TESTDATA_ROOT.resolve("exports/TestClassUsingExport.java");
    assertThat(graph.getJavaSourceFiles()).containsExactly(sourceFile);
    assertThat(graph.getExternalDependencies(ImmutableList.of(Label.of("//" + TESTDATA_ROOT.resolve("exports:exports")))))
        .containsExactly(Label.of("@@rules_jvm_external++maven+com_google_guava_guava//jar:jar"));
  }

  @Test
  public void testFileGroupSource() throws Exception {
    BuildGraphDataImpl graph =
        new BlazeQueryParser(
                getQuerySummary(TestData.FILEGROUP_QUERY), NOOP_CONTEXT, ImmutableSet.of())
            .parseForTesting();
    Path sourceFile = TESTDATA_ROOT.resolve("filegroup/TestFileGroupSource.java");
    Path subgroupSourceFile = TESTDATA_ROOT.resolve("filegroup/TestSubFileGroupSource.java");
    assertThat(graph.storage().projectDeps()).containsExactly(Label.of("@@rules_jvm_external++maven+com_google_guava_guava//jar:jar"));
    assertThat(graph.getJavaSourceFiles()).containsExactly(sourceFile, subgroupSourceFile);
    assertThat(graph.getSourceFileOwners(sourceFile))
        .containsExactly(Label.of("//" + TESTDATA_ROOT + "/filegroup:filegroup"));
    assertThat(graph.getSourceFileOwners(subgroupSourceFile))
        .containsExactly(Label.of("//" + TESTDATA_ROOT + "/filegroup:filegroup"));
    assertThat(graph.getExternalDependencies(ImmutableList.of(Label.of("//" + TESTDATA_ROOT + "/filegroup:filegroup"))))
        .containsExactly(Label.of("@@rules_jvm_external++maven+com_google_guava_guava//jar:jar"));
  }

  @Test
  public void testCcLibrary() throws Exception {
    BuildGraphDataImpl graph =
        new BlazeQueryParser(
                getQuerySummary(TestData.CC_LIBRARY_QUERY), NOOP_CONTEXT, ImmutableSet.of())
            .parseForTesting();
    Path expectedPackagePath = TESTDATA_ROOT.resolve("cc");
    assertThat(graph.storage().sourceFileLabels())
        .containsExactly(
            Label.fromWorkspacePackageAndName("", expectedPackagePath, "TestClass.cc"),
            Label.fromWorkspacePackageAndName("", expectedPackagePath, "TestClass.h"),
            Label.fromWorkspacePackageAndName("", expectedPackagePath, "BUILD"));
    assertThat(graph.getJavaSourceFiles()).isEmpty();
    assertThat(graph.getAndroidSourceFiles()).isEmpty();
    assertThat(graph.getSourceFileOwners(expectedPackagePath.resolve("TestClass.cc")))
        .containsExactly(Label.fromWorkspacePackageAndName("", expectedPackagePath, "cc"));
    assertThat(graph.getExternalDependencies(ImmutableList.of(Label.of("//" + TESTDATA_ROOT + "/cc:cc")))).isEmpty();
    assertThat(graph.getProjectTarget(Label.fromWorkspacePackageAndName("", expectedPackagePath, "cc")).languages())
        .containsExactly(QuerySyncLanguage.CC);
  }

  @Test
  public void testGetSameLanguageTargetsDependingOn_returnsTargetAndDirectDependent()
      throws Exception {
    BuildGraphDataImpl graph =
        BuildGraphs.forTestProject(TestData.JAVA_LIBRARY_TRANSITIVE_INTERNAL_DEP_QUERY);
    assertThat(
            graph.getSameLanguageTargetsDependingOn(
                ImmutableSet.of(Label.fromWorkspacePackageAndName("", TestData.ROOT.resolve("nodeps"), "nodeps"))))
        .containsExactly(
            Label.fromWorkspacePackageAndName("", TestData.ROOT.resolve("nodeps"), "nodeps"),
            Label.fromWorkspacePackageAndName("", TestData.ROOT.resolve("internaldep"), "internaldep"));

    assertThat(
            graph.getSameLanguageTargetsDependingOn(
                ImmutableSet.of(Label.fromWorkspacePackageAndName("", TestData.ROOT.resolve("internaldep"), "internaldep"))))
        .containsExactly(
                Label.fromWorkspacePackageAndName("", TestData.ROOT.resolve("internaldep"), "internaldep"),
                Label.fromWorkspacePackageAndName("", TestData.ROOT.resolve("transitiveinternaldep"), "transitiveinternaldep"));

    assertThat(
            graph.getSameLanguageTargetsDependingOn(
                ImmutableSet.of(
                    Label.fromWorkspacePackageAndName(
                        "", TestData.ROOT.resolve("transitiveinternaldep"), "transitiveinternaldep"))))
        .containsExactly(
            Label.fromWorkspacePackageAndName("", TestData.ROOT.resolve("transitiveinternaldep"), "transitiveinternaldep"));
  }

  @Test
  public void testTags() throws Exception {
    BuildGraphDataImpl graph = BuildGraphs.forTestProject(TestData.TAGS_QUERY);
    ProjectTarget testTarget = graph.getProjectTarget(TestData.TAGS_QUERY.getAssumedOnlyLabel());
    assertThat(testTarget.tags()).containsExactly("mytag");
  }

  @Test
  public void computeRequestedTargets_srcFile() throws Exception {
    BuildGraphDataImpl graph =
        new BlazeQueryParser(
                getQuerySummary(TestData.JAVA_LIBRARY_EXTERNAL_DEP_QUERY),
                NOOP_CONTEXT,
                ImmutableSet.of())
            .parseForTesting();
    RequestedTargets targets =
        graph.computeRequestedTargets(
            graph
                .getProjectTargets(
                    NOOP_CONTEXT,
                    TestData.JAVA_LIBRARY_EXTERNAL_DEP_QUERY
                        .getOnlySourcePath()
                        .resolve(Path.of("TestClassExternalDep.java")))
                .getUnambiguousTargets()
                .orElseThrow());
    assertThat(targets.buildTargets())
        .containsExactly(TestData.JAVA_LIBRARY_EXTERNAL_DEP_QUERY.getAssumedOnlyLabel());
    assertThat(targets.expectedDependencyTargets())
        .containsExactly(TestData.JAVA_LIBRARY_NO_DEPS_QUERY.getAssumedOnlyLabel());
  }

  @Test
  public void computeRequestedTargets_buildFile_multiTarget() throws Exception {
    BuildGraphDataImpl graph =
        new BlazeQueryParser(
                getQuerySummary(TestData.JAVA_LIBRARY_MULTI_TARGETS),
                NOOP_CONTEXT,
                ImmutableSet.of())
            .parseForTesting();
    RequestedTargets targets =
        graph.computeRequestedTargets(
            graph
                .getProjectTargets(
                    NOOP_CONTEXT,
                    TestData.JAVA_LIBRARY_MULTI_TARGETS
                        .getOnlySourcePath()
                        .resolve(Path.of("BUILD")))
                .getUnambiguousTargets()
                .orElseThrow());
    assertThat(targets.buildTargets())
        .containsExactly(
            TestData.JAVA_LIBRARY_MULTI_TARGETS
                .getAssumedOnlyLabel()
                .siblingWithName("externaldep"),
            TestData.JAVA_LIBRARY_MULTI_TARGETS.getAssumedOnlyLabel().siblingWithName("nodeps"));
    String expected = "@@rules_jvm_external++maven+com_google_guava_guava//jar:jar";
    expected = "@@rules_jvm_external++maven+com_google_guava_guava//jar:jar";
    assertThat(targets.expectedDependencyTargets()).containsExactly(Label.of(expected));
  }

  @Test
  public void computeRequestedTargets_buildFile_nested() throws Exception {
    BuildGraphDataImpl graph =
        new BlazeQueryParser(
                getQuerySummary(TestData.JAVA_LIBRARY_NESTED_PACKAGE),
                NOOP_CONTEXT,
                ImmutableSet.of())
            .parseForTesting();
    RequestedTargets targets =
        graph.computeRequestedTargets(
            graph
                .getProjectTargets(
                    NOOP_CONTEXT,
                    TestData.JAVA_LIBRARY_NESTED_PACKAGE
                        .getOnlySourcePath()
                        .resolve(Path.of("BUILD")))
                .getUnambiguousTargets()
                .orElseThrow());
    assertThat(targets.buildTargets())
        .containsExactly(TestData.JAVA_LIBRARY_NESTED_PACKAGE.getAssumedOnlyLabel());
    assertThat(targets.expectedDependencyTargets())
        .containsExactly(Label.of("@@rules_jvm_external++maven+com_google_guava_guava//jar:jar"));
  }

  @Test
  public void computeRequestedTargets_directory() throws Exception {
    BuildGraphDataImpl graph =
        new BlazeQueryParser(
                getQuerySummary(TestData.JAVA_LIBRARY_NESTED_PACKAGE),
                NOOP_CONTEXT,
                ImmutableSet.of())
            .parseForTesting();
    RequestedTargets targets =
        graph.computeRequestedTargets(
            graph
                .getProjectTargets(
                    NOOP_CONTEXT, TestData.JAVA_LIBRARY_NESTED_PACKAGE.getOnlySourcePath())
                .getUnambiguousTargets()
                .orElseThrow());
    assertThat(targets.buildTargets())
        .containsExactly(
            TestData.JAVA_LIBRARY_NESTED_PACKAGE.getAssumedOnlyLabel(),
            TestData.JAVA_LIBRARY_NESTED_PACKAGE
                .getAssumedOnlyLabel()
                .siblingWithPathAndName("inner:inner"));
    assertThat(targets.expectedDependencyTargets())
        .containsExactly(
            Label.of("@@rules_jvm_external++maven+com_google_guava_guava//jar:jar"),
            Label.of("@@rules_jvm_external++maven+com_google_code_gson_gson//jar:jar"));
  }

  @Test
  public void computeRequestedTargets_cc_srcFile() throws Exception {
    BuildGraphDataImpl graph =
        new BlazeQueryParser(
                getQuerySummary(TestData.CC_EXTERNAL_DEP_QUERY), NOOP_CONTEXT, ImmutableSet.of())
            .parseForTesting();
    RequestedTargets targets =
        graph.computeRequestedTargets(
            graph
                .getProjectTargets(
                    NOOP_CONTEXT,
                    TestData.CC_EXTERNAL_DEP_QUERY.getOnlySourcePath().resolve("TestClass.cc"))
                .getUnambiguousTargets()
                .orElseThrow());
    assertThat(targets.buildTargets())
        .containsExactly(TestData.CC_EXTERNAL_DEP_QUERY.getAssumedOnlyLabel());
    assertThat(targets.expectedDependencyTargets()).isEmpty();
  }

  @Test
  public void reverseDeps() throws IOException {
    BuildGraphDataImpl graph =
        new BlazeQueryParser(
                getQuerySummary(TestData.JAVA_LIBRARY_NO_DEPS_QUERY),
                NOOP_CONTEXT,
                ImmutableSet.of())
            .parseForTesting();
    assertThat(
            graph
                .getReverseDepsForSource(
                    TestData.JAVA_LIBRARY_NO_DEPS_QUERY
                        .getOnlySourcePath()
                        .resolve("TestClassNoDeps.java"))
                .stream()
                .map(ProjectTarget::label))
        .containsExactlyElementsIn(TestData.JAVA_LIBRARY_NO_DEPS_QUERY.getAssumedLabels());
  }
}
