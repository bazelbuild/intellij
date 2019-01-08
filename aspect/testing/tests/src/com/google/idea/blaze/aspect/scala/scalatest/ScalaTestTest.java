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
package com.google.idea.blaze.aspect.scala.scalatest;

import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.intellij.IntellijAspectTestFixtureOuterClass.IntellijAspectTestFixture;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.TargetIdeInfo;
import com.google.idea.blaze.BazelIntellijAspectTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests scala_test */
@RunWith(JUnit4.class)
public class ScalaTestTest extends BazelIntellijAspectTest {

  @Test
  public void testScalaTest() throws Exception {
    IntellijAspectTestFixture testFixture = loadTestFixture(":footest_fixture");
    TargetIdeInfo testInfo = findTarget(testFixture, ":FooTest");
    assertThat(testInfo.getKindString()).isEqualTo("scala_test");
    assertThat(relativePathsForArtifacts(testInfo.getJavaIdeInfo().getSourcesList()))
        .containsExactly(testRelative("FooTest.scala"));
    assertThat(testInfo.getJavaIdeInfo().getJarsList()).hasSize(1);
    assertThat(testInfo.getJavaIdeInfo().getJarsList().get(0).getJar().getRelativePath())
        .isEqualTo(testRelative("FooTest.jar"));

    assertThat(getOutputGroupFiles(testFixture, "intellij-info-java"))
        .contains(testRelative(intellijInfoFileName("FooTest")));
    assertThat(getOutputGroupFiles(testFixture, "intellij-resolve-java"))
        .contains(testRelative("FooTest.jar"));
    assertThat(getOutputGroupFiles(testFixture, "intellij-compile-java"))
        .contains(testRelative("FooTest.jar"));
    assertThat(getOutputGroupFiles(testFixture, "intellij-info-generic")).isEmpty();

    assertThat(testInfo.getTestInfo().getSize()).isEqualTo("large");
  }
}
