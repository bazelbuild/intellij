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
package com.google.idea.blaze.aspect.scala.scalalibrary;

import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.intellij.IntellijAspectTestFixtureOuterClass.IntellijAspectTestFixture;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.TargetIdeInfo;
import com.google.idea.blaze.BazelIntellijAspectTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests scala_library */
@RunWith(JUnit4.class)
public class ScalaLibraryTest extends BazelIntellijAspectTest {
  @Test
  public void testScalaLibrary() throws Exception {
    IntellijAspectTestFixture testFixture = loadTestFixture(":simple_fixture");
    TargetIdeInfo target = findTarget(testFixture, ":simple");
    assertThat(target.getKindString()).isEqualTo("scala_library");
    assertThat(target.hasJavaIdeInfo()).isTrue();
    assertThat(target.hasCIdeInfo()).isFalse();
    assertThat(target.hasAndroidIdeInfo()).isFalse();
    assertThat(target.hasPyIdeInfo()).isFalse();

    assertThat(relativePathsForArtifacts(target.getJavaIdeInfo().getSourcesList()))
        .containsExactly(testRelative("Foo.scala"));
    assertThat(target.getJavaIdeInfo().getJarsList()).hasSize(1);
    assertThat(target.getJavaIdeInfo().getJarsList().get(0).getJar().getRelativePath())
        .isEqualTo(testRelative("simple.jar"));
    // Also contains ijars for scala-library.
    // Also contains jars + srcjars for liblibrary.
    assertThat(getOutputGroupFiles(testFixture, "intellij-resolve-java"))
        .contains(testRelative("simple.jar"));

    assertThat(getOutputGroupFiles(testFixture, "intellij-info-java"))
        .contains(testRelative(intellijInfoFileName("simple")));
    assertThat(getOutputGroupFiles(testFixture, "intellij-compile-java"))
        .contains(testRelative("simple.jar"));
    assertThat(getOutputGroupFiles(testFixture, "intellij-info-generic")).isEmpty();

    assertThat(target.getJavaIdeInfo().getMainClass()).isEmpty();
  }
}
