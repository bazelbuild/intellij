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
package com.google.idea.blaze.qsync;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.common.Output;
import com.google.idea.blaze.qsync.query.Query;
import com.google.idea.blaze.qsync.query.QuerySummary;
import com.google.idea.blaze.qsync.testdata.TestData;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class BuildGraphTest {

  private static final Path TEST_ROOT =
      Path.of("querysync/javatests/com/google/idea/blaze/qsync");

  private static final Path TESTDATA_ROOT = TEST_ROOT.resolve("testdata");

  private static final Context TEST_CONTEXT =
      new Context() {
        @Override
        public <T extends Output> void output(T output) {}

        @Override
        public void setHasError() {}
      };

  private static Query.Summary getQuerySummary(TestData genQueryName) throws IOException {
    return QuerySummary.create(TestData.getPathFor(genQueryName).toFile()).getProto();
  }

  @Test
  public void testJavaLibraryNoDeps() throws Exception {
    BuildGraphData graph =
        new BlazeQueryParser(TEST_CONTEXT)
            .parse(getQuerySummary(TestData.JAVA_LIBRARY_NO_DEPS_QUERY));
    assertThat(graph.getAllSourceFiles())
        .containsExactly(
            TESTDATA_ROOT.resolve("nodeps/TestClassNoDeps.java"),
            TESTDATA_ROOT.resolve("nodeps/BUILD"));
    assertThat(graph.getJavaSourceFiles())
        .containsExactly(TESTDATA_ROOT.resolve("nodeps/TestClassNoDeps.java"));
    assertThat(graph.getAndroidSourceFiles()).isEmpty();
    assertThat(
            graph.getTargetOwner(TESTDATA_ROOT.resolve("nodeps/TestClassNoDeps.java").toString()))
        .isEqualTo("//" + TESTDATA_ROOT + "/nodeps:nodeps");
    assertThat(
            graph.getFileDependencies(
                TESTDATA_ROOT.resolve("nodeps/TestClassNoDeps.java").toString()))
        .isEmpty();
  }

  @Test
  public void testJavaLibraryExternalDep() throws Exception {
    BuildGraphData graph =
        new BlazeQueryParser(TEST_CONTEXT)
            .parse(getQuerySummary(TestData.JAVA_LIBRARY_EXTERNAL_DEP_QUERY));
    assertThat(graph.getFileDependencies(TESTDATA_ROOT + "/externaldep/TestClassExternalDep.java"))
        .containsExactly("//java/com/google/common/collect:collect");
  }

  @Test
  public void testJavaLibraryInternalDep() throws Exception {
    BuildGraphData graph =
        new BlazeQueryParser(TEST_CONTEXT)
            .parse(getQuerySummary(TestData.JAVA_LIBRARY_INTERNAL_DEP_QUERY));
    // Sanity check:
    assertThat(graph.getAllSourceFiles())
        .contains(TESTDATA_ROOT.resolve("nodeps/TestClassNoDeps.java"));
    assertThat(
            graph.getFileDependencies(
                TESTDATA_ROOT.resolve("internaldep/TestClassInternalDep.java").toString()))
        .isEmpty();
  }

  @Test
  public void testJavaLibraryTransientDep() throws Exception {
    BuildGraphData graph =
        new BlazeQueryParser(TEST_CONTEXT)
            .parse(getQuerySummary(TestData.JAVA_LIBRARY_TRANSITIVE_DEP_QUERY));
    // Sanity check:
    assertThat(graph.getAllSourceFiles())
        .contains(TESTDATA_ROOT.resolve("externaldep/TestClassExternalDep.java"));
    assertThat(
            graph.getFileDependencies(TESTDATA_ROOT + "/transitivedep/TestClassTransitiveDep.java"))
        .containsExactly("//java/com/google/common/collect:collect");
  }

  @Test
  public void testJavaLibraryProtoDep() throws Exception {
    BuildGraphData graph =
        new BlazeQueryParser(TEST_CONTEXT)
            .parse(getQuerySummary(TestData.JAVA_LIBRARY_PROTO_DEP_QUERY));
    assertThat(graph.getFileDependencies(TESTDATA_ROOT + "/protodep/TestClassProtoDep.java"))
        .containsExactly("//" + TESTDATA_ROOT + "/protodep:proto_java_proto_lite");
  }

  @Test
  public void testJavaLibraryMultiTargets() throws Exception {
    BuildGraphData graph =
        new BlazeQueryParser(TEST_CONTEXT)
            .parse(getQuerySummary(TestData.JAVA_LIBRARY_MULTI_TARGETS));
    // Sanity check:
    assertThat(graph.getJavaSourceFiles())
        .contains(TESTDATA_ROOT.resolve("multitarget/TestClassSingleTarget.java"));
    // If a source file is included in more than one target, we prefer the one with fewer
    // dependencies.
    assertThat(graph.getTargetOwner(TESTDATA_ROOT + "/multitarget/TestClassMultiTarget.java"))
        .isEqualTo("//" + TESTDATA_ROOT + "/multitarget:nodeps");
    assertThat(graph.getFileDependencies(TESTDATA_ROOT + "/multitarget/TestClassMultiTarget.java"))
        .isEmpty();
  }

  @Test
  public void testJavaLibaryExportingExternalTargets() throws Exception {
    BuildGraphData graph =
        new BlazeQueryParser(TEST_CONTEXT).parse(getQuerySummary(TestData.JAVA_EXPORTED_DEP_QUERY));
    Path sourceFile = TESTDATA_ROOT.resolve("exports/TestClassUsingExport.java");
    assertThat(graph.getJavaSourceFiles()).containsExactly(sourceFile);
    assertThat(graph.getFileDependencies(sourceFile.toString()))
        .containsExactly("//java/com/google/common/collect:collect");
  }

  @Test
  public void testAndroidLibrary() throws Exception {
    BuildGraphData graph =
        new BlazeQueryParser(TEST_CONTEXT).parse(getQuerySummary(TestData.ANDROID_LIB_QUERY));
    assertThat(graph.getAllSourceFiles())
        .containsExactly(
            TESTDATA_ROOT.resolve("android/TestAndroidClass.java"),
            TESTDATA_ROOT.resolve("android/BUILD"));
    assertThat(graph.getJavaSourceFiles())
        .containsExactly(TESTDATA_ROOT.resolve("android/TestAndroidClass.java"));
    assertThat(graph.getAndroidSourceFiles())
        .containsExactly(TESTDATA_ROOT.resolve("android/TestAndroidClass.java"));
    assertThat(graph.getTargetOwner(TESTDATA_ROOT + "/android/TestAndroidClass.java"))
        .isEqualTo("//" + TESTDATA_ROOT + "/android:android");
    assertThat(graph.getFileDependencies(TESTDATA_ROOT + "/android/TestAndroidClass.java"))
        .isEmpty();
    assertThat(graph.projectDeps()).isEmpty();
  }

  @Test
  public void testProjectAndroidLibrariesWithAidlSource_areProjectDeps() throws Exception {
    BuildGraphData graph =
        new BlazeQueryParser(TEST_CONTEXT)
            .parse(getQuerySummary(TestData.ANDROID_AIDL_SOURCE_QUERY));
    assertThat(graph.getAllSourceFiles())
        .containsExactly(
            TESTDATA_ROOT.resolve("aidl/TestAndroidAidlClass.java"),
            TESTDATA_ROOT.resolve("aidl/TestAidlService.aidl"),
            TESTDATA_ROOT.resolve("aidl/BUILD"));
    assertThat(graph.getJavaSourceFiles())
        .containsExactly(TESTDATA_ROOT.resolve("aidl/TestAndroidAidlClass.java"));
    assertThat(graph.getAndroidSourceFiles())
        .containsExactly(TESTDATA_ROOT.resolve("aidl/TestAndroidAidlClass.java"));
    assertThat(graph.projectDeps()).containsExactly("//" + TESTDATA_ROOT + "/aidl:aidl");
  }
}
