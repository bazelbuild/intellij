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
package com.google.idea.blaze.aspect.java.javatest;

import static com.google.common.truth.Truth.assertThat;
import static java.util.stream.Collectors.toList;

import com.google.devtools.intellij.IntellijAspectTestFixtureOuterClass.IntellijAspectTestFixture;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.TargetIdeInfo;
import com.google.idea.blaze.BazelIntellijAspectTest;
import com.google.idea.blaze.aspect.IntellijAspectTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests java_test */
@RunWith(JUnit4.class)
public class JavaTestTest extends BazelIntellijAspectTest {

  @Test
  public void testJavaTest() throws Exception {
    IntellijAspectTestFixture testFixture = loadTestFixture(":footest_fixture");
    TargetIdeInfo testInfo = findTarget(testFixture, ":FooTest");
    assertThat(testInfo.getKindString()).isEqualTo("java_test");
    assertThat(relativePathsForArtifacts(testInfo.getJavaIdeInfo().getSourcesList()))
        .containsExactly(testRelative("FooTest.java"));
    assertThat(
            testInfo
                .getJavaIdeInfo()
                .getJarsList()
                .stream()
                .map(IntellijAspectTest::libraryArtifactToString)
                .collect(toList()))
        .containsExactly(
            jarString(testRelative("FooTest.jar"), null, testRelative("FooTest-src.jar")));

    // intellij-info groups
    assertThat(getOutputGroupFiles(testFixture, "intellij-info-generic")).isEmpty();
    assertThat(getOutputGroupFiles(testFixture, "intellij-info-java"))
        .containsAllOf(
            testRelative("FooTest.java-manifest"), testRelative(intellijInfoFileName("FooTest")));
    assertThat(getOutputGroupFiles(testFixture, "intellij-info-java-direct-deps"))
        .containsAllOf(
            testRelative("FooTest.java-manifest"), testRelative(intellijInfoFileName("FooTest")));
    assertThat(getOutputGroupFiles(testFixture, "intellij-info-java-outputs"))
        .containsExactly(
            testRelative("FooTest.java-manifest"), testRelative(intellijInfoFileName("FooTest")));

    // intellij-resolve groups
    assertThat(getOutputGroupFiles(testFixture, "intellij-resolve-java"))
        .containsAllOf(
            testRelative("FooTest.jar"),
            testRelative("FooTest-src.jar"),
            testRelative("FooTest.jdeps"));
    assertThat(getOutputGroupFiles(testFixture, "intellij-resolve-java-direct-deps"))
        .containsAllOf(
            testRelative("FooTest.jar"),
            testRelative("FooTest-src.jar"),
            testRelative("FooTest.jdeps"));
    assertThat(getOutputGroupFiles(testFixture, "intellij-resolve-java-outputs"))
        .containsExactly(
            testRelative("FooTest.jar"),
            testRelative("FooTest-src.jar"),
            testRelative("FooTest.jdeps"));

    // intellij-compile groups
    assertThat(getOutputGroupFiles(testFixture, "intellij-compile-java"))
        .contains(testRelative("FooTest.jar"));
    assertThat(getOutputGroupFiles(testFixture, "intellij-compile-java-direct-deps"))
        .contains(testRelative("FooTest.jar"));
    assertThat(getOutputGroupFiles(testFixture, "intellij-compile-java-outputs"))
        .containsExactly(testRelative("FooTest.jar"));

    // TargetIdeInfo contents
    assertThat(testInfo.getJavaIdeInfo().getJdeps().getRelativePath())
        .isEqualTo(testRelative("FooTest.jdeps"));
    assertThat(testInfo.getTestInfo().getSize()).isEqualTo("large");
  }
}
