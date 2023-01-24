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

import com.google.devtools.build.runtime.RunfilesPaths;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.common.Output;
import java.io.File;
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

  private static File getQueryFile(String genQueryName) {
    return RunfilesPaths.resolve(TEST_ROOT.resolve(genQueryName)).toFile();
  }

  @Test
  public void testJavaLibraryNoDeps() throws Exception {
    BuildGraphData graph =
        new BlazeQueryParser(TEST_CONTEXT).parse(getQueryFile("JavaLibraryNoDepsQuery"));
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
        new BlazeQueryParser(TEST_CONTEXT).parse(getQueryFile("JavaLibraryExternalDepQuery"));
    assertThat(graph.getFileDependencies(TESTDATA_ROOT + "/externaldep/TestClassExternalDep.java"))
        .containsExactly("//java/com/google/common/collect:collect");
  }

  @Test
  public void testJavaLibraryInternalDep() throws Exception {
    BuildGraphData graph =
        new BlazeQueryParser(TEST_CONTEXT).parse(getQueryFile("JavaLibraryInternalDepQuery"));
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
        new BlazeQueryParser(TEST_CONTEXT).parse(getQueryFile("JavaLibraryTransitiveDepQuery"));
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
        new BlazeQueryParser(TEST_CONTEXT).parse(getQueryFile("JavaLibraryProtoDepQuery"));
    assertThat(graph.getFileDependencies(TESTDATA_ROOT + "/protodep/TestClassProtoDep.java"))
        .containsExactly("//" + TESTDATA_ROOT + "/protodep:proto_java_proto_lite");
  }

  @Test
  public void testJavaLibraryMultiTargets() throws Exception {
    BuildGraphData graph =
        new BlazeQueryParser(TEST_CONTEXT).parse(getQueryFile("JavaLibraryMultiTargets"));
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
  public void testAndroidLibrary() throws Exception {
    BuildGraphData graph =
        new BlazeQueryParser(TEST_CONTEXT).parse(getQueryFile("AndroidLibQuery"));
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
  }
}
