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
import static com.google.common.truth.Truth8.assertThat;
import static com.google.idea.blaze.qsync.QuerySyncTestUtils.NOOP_CONTEXT;
import static com.google.idea.blaze.qsync.QuerySyncTestUtils.getQuerySummary;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.qsync.BlazeQueryParser;
import com.google.idea.blaze.qsync.project.ProjectDefinition.LanguageClass;
import com.google.idea.blaze.qsync.testdata.BuildGraphs;
import com.google.idea.blaze.qsync.testdata.TestData;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class BuildGraphTest {

  private static final Path TEST_ROOT =
      Path.of("querysync/javatests/com/google/idea/blaze/qsync");

  private static final Path TESTDATA_ROOT = TEST_ROOT.resolve("testdata");

  @Test
  public void testJavaLibraryNoDeps() throws Exception {
    BuildGraphData graph =
        new BlazeQueryParser(
                getQuerySummary(TestData.JAVA_LIBRARY_NO_DEPS_QUERY),
                NOOP_CONTEXT,
                ImmutableSet.of(),
                Suppliers.ofInstance(true))
            .parse();
    assertThat(graph.allTargets().toLabelSet())
        .containsExactly(Label.of("//" + TESTDATA_ROOT + "/nodeps:nodeps"));
    assertThat(graph.getAllSourceFiles())
        .containsExactly(
            TESTDATA_ROOT.resolve("nodeps/TestClassNoDeps.java"),
            TESTDATA_ROOT.resolve("nodeps/BUILD"));
    assertThat(graph.getJavaSourceFiles())
        .containsExactly(TESTDATA_ROOT.resolve("nodeps/TestClassNoDeps.java"));
    assertThat(graph.getAndroidSourceFiles()).isEmpty();
    assertThat(graph.getTargetOwners(TESTDATA_ROOT.resolve("nodeps/TestClassNoDeps.java")))
        .containsExactly(Label.of("//" + TESTDATA_ROOT + "/nodeps:nodeps"));
    assertThat(graph.getFileDependencies(TESTDATA_ROOT.resolve("nodeps/TestClassNoDeps.java")))
        .isEmpty();
    assertThat(graph.targetMap().get(Label.of("//" + TESTDATA_ROOT + "/nodeps:nodeps")).languages())
        .containsExactly(LanguageClass.JAVA);
  }

  @Test
  public void testJavaLibraryExternalDep() throws Exception {
    BuildGraphData graph =
        new BlazeQueryParser(
                getQuerySummary(TestData.JAVA_LIBRARY_EXTERNAL_DEP_QUERY),
                NOOP_CONTEXT,
                ImmutableSet.of(),
                Suppliers.ofInstance(true))
            .parse();
    assertThat(
            graph.getFileDependencies(
                TESTDATA_ROOT.resolve("externaldep/TestClassExternalDep.java")))
        .containsExactly(Label.of("@com_google_guava_guava//jar:jar"));
  }

  @Test
  public void testJavaLibraryInternalDep() throws Exception {
    BuildGraphData graph =
        new BlazeQueryParser(
                getQuerySummary(TestData.JAVA_LIBRARY_INTERNAL_DEP_QUERY),
                NOOP_CONTEXT,
                ImmutableSet.of(),
                Suppliers.ofInstance(true))
            .parse();
    // Sanity check:
    assertThat(graph.getAllSourceFiles())
        .contains(TESTDATA_ROOT.resolve("nodeps/TestClassNoDeps.java"));
    assertThat(
            graph.getFileDependencies(
                TESTDATA_ROOT.resolve("internaldep/TestClassInternalDep.java")))
        .isEmpty();
  }

  @Test
  public void testJavaLibraryTransientDep() throws Exception {
    BuildGraphData graph =
        new BlazeQueryParser(
                getQuerySummary(TestData.JAVA_LIBRARY_TRANSITIVE_DEP_QUERY),
                NOOP_CONTEXT,
                ImmutableSet.of(),
                Suppliers.ofInstance(true))
            .parse();
    // Sanity check:
    assertThat(graph.getAllSourceFiles())
        .contains(TESTDATA_ROOT.resolve("externaldep/TestClassExternalDep.java"));
    assertThat(
            graph.getFileDependencies(
                TESTDATA_ROOT.resolve("transitivedep/TestClassTransitiveDep.java")))
        .containsExactly(Label.of("@com_google_guava_guava//jar:jar"));
  }

  @Test
  public void testJavaLibraryProtoDep() throws Exception {
    BuildGraphData graph =
        new BlazeQueryParser(
                getQuerySummary(TestData.JAVA_LIBRARY_PROTO_DEP_QUERY),
                NOOP_CONTEXT,
                ImmutableSet.of(),
                Suppliers.ofInstance(true))
            .parse();
    assertThat(graph.getFileDependencies(TESTDATA_ROOT.resolve("protodep/TestClassProtoDep.java")))
        .containsExactly(
            Label.of("//" + TESTDATA_ROOT + "/protodep:proto_java_proto_lite"),
            Label.of("//tools/proto/toolchains:javalite"));
    assertThat(graph.getProtoSourceFiles())
        .containsExactly(TESTDATA_ROOT.resolve("protodep/testproto.proto"));
  }

  @Test
  public void testJavaLibraryMultiTargets() throws Exception {
    BuildGraphData graph =
        new BlazeQueryParser(
                getQuerySummary(TestData.JAVA_LIBRARY_MULTI_TARGETS),
                NOOP_CONTEXT,
                ImmutableSet.of(),
                Suppliers.ofInstance(true))
            .parse();
    assertThat(graph.allTargets().toLabelSet())
        .containsExactly(
            Label.of("//" + TESTDATA_ROOT + "/multitarget:nodeps"),
            Label.of("//" + TESTDATA_ROOT + "/multitarget:externaldep"));
    // Sanity check:
    assertThat(graph.getJavaSourceFiles())
        .contains(TESTDATA_ROOT.resolve("multitarget/TestClassSingleTarget.java"));
    assertThat(
            graph.getTargetOwners(TESTDATA_ROOT.resolve("multitarget/TestClassMultiTarget.java")))
        .containsExactly(
            Label.of("//" + TESTDATA_ROOT + "/multitarget:nodeps"),
            Label.of("//" + TESTDATA_ROOT + "/multitarget:externaldep"));
    assertThat(
            graph.getFileDependencies(
                TESTDATA_ROOT.resolve("multitarget/TestClassMultiTarget.java")))
        .contains(Label.of("@com_google_guava_guava//jar:jar"));
  }

  @Test
  public void testJavaLibraryExportingExternalTargets() throws Exception {
    BuildGraphData graph =
        new BlazeQueryParser(
                getQuerySummary(TestData.JAVA_EXPORTED_DEP_QUERY),
                NOOP_CONTEXT,
                ImmutableSet.of(),
                Suppliers.ofInstance(true))
            .parse();
    Path sourceFile = TESTDATA_ROOT.resolve("exports/TestClassUsingExport.java");
    assertThat(graph.getJavaSourceFiles()).containsExactly(sourceFile);
    assertThat(graph.getFileDependencies(sourceFile))
        .containsExactly(Label.of("@com_google_guava_guava//jar:jar"));
  }

  @Test
  public void testAndroidLibrary() throws Exception {
    BuildGraphData graph =
        new BlazeQueryParser(
                getQuerySummary(TestData.ANDROID_LIB_QUERY),
                NOOP_CONTEXT,
                ImmutableSet.of(),
                Suppliers.ofInstance(true))
            .parse();
    assertThat(graph.getAllSourceFiles())
        .containsExactly(
            TESTDATA_ROOT.resolve("android/TestAndroidClass.java"),
            TESTDATA_ROOT.resolve("android/BUILD"),
            TESTDATA_ROOT.resolve("android/AndroidManifest.xml"));
    assertThat(graph.getJavaSourceFiles())
        .containsExactly(TESTDATA_ROOT.resolve("android/TestAndroidClass.java"));
    assertThat(graph.getAndroidSourceFiles())
        .containsExactly(TESTDATA_ROOT.resolve("android/TestAndroidClass.java"));
    assertThat(graph.getTargetOwners(TESTDATA_ROOT.resolve("android/TestAndroidClass.java")))
        .containsExactly(Label.of("//" + TESTDATA_ROOT + "/android:android"));
    assertThat(graph.getFileDependencies(TESTDATA_ROOT.resolve("android/TestAndroidClass.java")))
        .containsExactly(
            Label.of(
                "//third_party/bazel_rules/rules_kotlin/toolchains/kotlin_jvm:kt_jvm_toolchain_linux_sts_jdk"));
    assertThat(graph.projectDeps())
        .containsExactly(
            Label.of(
                "//third_party/bazel_rules/rules_kotlin/toolchains/kotlin_jvm:kt_jvm_toolchain_linux_sts_jdk"));
  }

  @Test
  public void testProjectAndroidLibrariesWithAidlSource_areProjectDeps() throws Exception {
    BuildGraphData graph =
        new BlazeQueryParser(
                getQuerySummary(TestData.ANDROID_AIDL_SOURCE_QUERY),
                NOOP_CONTEXT,
                ImmutableSet.of(),
                Suppliers.ofInstance(true))
            .parse();
    assertThat(graph.getAllSourceFiles())
        .containsExactly(
            TESTDATA_ROOT.resolve("aidl/TestAndroidAidlClass.java"),
            TESTDATA_ROOT.resolve("aidl/TestAidlService.aidl"),
            TESTDATA_ROOT.resolve("aidl/BUILD"));
    assertThat(graph.getJavaSourceFiles())
        .containsExactly(TESTDATA_ROOT.resolve("aidl/TestAndroidAidlClass.java"));
    assertThat(graph.getAndroidSourceFiles())
        .containsExactly(TESTDATA_ROOT.resolve("aidl/TestAndroidAidlClass.java"));
    assertThat(graph.projectDeps())
        .containsExactly(
            Label.of(
                "//third_party/bazel_rules/rules_kotlin/toolchains/kotlin_jvm:kt_jvm_toolchain_linux_sts_jdk"),
            Label.of("//" + TESTDATA_ROOT + "/aidl:aidl"));
    assertThat(graph.getFileDependencies(TESTDATA_ROOT.resolve("aidl/TestAndroidAidlClass.java")))
        .containsExactly(
            Label.of(
                "//third_party/bazel_rules/rules_kotlin/toolchains/kotlin_jvm:kt_jvm_toolchain_linux_sts_jdk"),
            Label.of("//" + TESTDATA_ROOT + "/aidl:aidl"));
  }

  @Test
  public void testFileGroupSource() throws Exception {
    BuildGraphData graph =
        new BlazeQueryParser(
                getQuerySummary(TestData.FILEGROUP_QUERY),
                NOOP_CONTEXT,
                ImmutableSet.of(),
                Suppliers.ofInstance(true))
            .parse();
    Path sourceFile = TESTDATA_ROOT.resolve("filegroup/TestFileGroupSource.java");
    Path subgroupSourceFile = TESTDATA_ROOT.resolve("filegroup/TestSubFileGroupSource.java");
    assertThat(graph.projectDeps())
        .containsExactly(Label.of("@com_google_guava_guava//jar:jar"));
    assertThat(graph.getJavaSourceFiles()).containsExactly(sourceFile, subgroupSourceFile);
    assertThat(graph.getFileDependencies(sourceFile))
        .containsExactly(Label.of("@com_google_guava_guava//jar:jar"));
    assertThat(graph.getFileDependencies(subgroupSourceFile))
        .containsExactly(Label.of("@com_google_guava_guava//jar:jar"));
  }

  @Test
  public void testCcLibrary() throws Exception {
    BuildGraphData graph =
        new BlazeQueryParser(
                getQuerySummary(TestData.CC_LIBRARY_QUERY),
                NOOP_CONTEXT,
                ImmutableSet.of(),
                Suppliers.ofInstance(true))
            .parse();
    assertThat(graph.getAllSourceFiles())
        .containsExactly(
            TESTDATA_ROOT.resolve("cc/TestClass.cc"),
            TESTDATA_ROOT.resolve("cc/TestClass.h"),
            TESTDATA_ROOT.resolve("cc/BUILD"));
    assertThat(graph.getJavaSourceFiles()).isEmpty();
    assertThat(graph.getAndroidSourceFiles()).isEmpty();
    assertThat(graph.getTargetOwners(TESTDATA_ROOT.resolve("cc/TestClass.cc")))
        .containsExactly(Label.of("//" + TESTDATA_ROOT + "/cc:cc"));
    assertThat(graph.getFileDependencies(TESTDATA_ROOT.resolve("cc/TestClass.cc"))).isEmpty();
    assertThat(graph.targetMap().get(Label.of("//" + TESTDATA_ROOT + "/cc:cc")).languages())
        .containsExactly(LanguageClass.CC);
  }

  @Test
  public void testCcLibrary_disabled() throws Exception {
    BuildGraphData graph =
        new BlazeQueryParser(
                getQuerySummary(TestData.CC_LIBRARY_QUERY),
                NOOP_CONTEXT,
                ImmutableSet.of(),
                Suppliers.ofInstance(false))
            .parse();
    assertThat(graph.getTargetOwners(TESTDATA_ROOT.resolve("cc/TestClass.cc"))).isEmpty();
  }

  @Test
  public void testGetSameLanguageTargetsDependingOn_returnsTargetAndDirectDependent()
      throws Exception {
    BuildGraphData graph =
        BuildGraphs.forTestProject(TestData.JAVA_LIBRARY_TRANSITIVE_INTERNAL_DEP_QUERY);
    assertThat(
            graph.getSameLanguageTargetsDependingOn(
                ImmutableSet.of(Label.of("//" + TestData.ROOT.resolve("nodeps:nodeps")))))
        .containsExactly(
            Label.of("//" + TestData.ROOT.resolve("nodeps:nodeps")),
            Label.of("//" + TestData.ROOT.resolve("internaldep:internaldep")));

    assertThat(
            graph.getSameLanguageTargetsDependingOn(
                ImmutableSet.of(Label.of("//" + TestData.ROOT.resolve("internaldep:internaldep")))))
        .containsExactly(
            Label.of("//" + TestData.ROOT.resolve("internaldep:internaldep")),
            Label.of("//" + TestData.ROOT.resolve("transitiveinternaldep:transitiveinternaldep")));

    assertThat(
            graph.getSameLanguageTargetsDependingOn(
                ImmutableSet.of(
                    Label.of(
                        "//"
                            + TestData.ROOT.resolve(
                                "transitiveinternaldep:transitiveinternaldep")))))
        .containsExactly(
            Label.of("//" + TestData.ROOT.resolve("transitiveinternaldep:transitiveinternaldep")));
  }

}
