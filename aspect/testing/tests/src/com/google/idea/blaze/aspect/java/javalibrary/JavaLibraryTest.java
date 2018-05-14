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
package com.google.idea.blaze.aspect.java.javalibrary;

import static com.google.common.truth.Truth.assertThat;
import static java.util.stream.Collectors.toList;

import com.google.devtools.intellij.IntellijAspectTestFixtureOuterClass.IntellijAspectTestFixture;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.TargetIdeInfo;
import com.google.idea.blaze.BazelIntellijAspectTest;
import com.google.idea.blaze.aspect.IntellijAspectTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Simple test */
@RunWith(JUnit4.class)
public class JavaLibraryTest extends BazelIntellijAspectTest {

  @Test
  public void testJavaLibrary() throws Exception {
    IntellijAspectTestFixture testFixture = loadTestFixture(":simple_fixture");
    TargetIdeInfo target = findTarget(testFixture, ":simple");

    assertThat(target.getKindString()).isEqualTo("java_library");
    assertThat(target.hasJavaIdeInfo()).isTrue();
    assertThat(target.hasCIdeInfo()).isFalse();
    assertThat(target.hasAndroidIdeInfo()).isFalse();
    assertThat(target.hasPyIdeInfo()).isFalse();

    assertThat(relativePathsForArtifacts(target.getJavaIdeInfo().getSourcesList()))
        .containsExactly(testRelative("Foo.java"));
    assertThat(
            target
                .getJavaIdeInfo()
                .getJarsList()
                .stream()
                .map(IntellijAspectTest::libraryArtifactToString)
                .collect(toList()))
        .containsExactly(
            jarString(
                testRelative("libsimple.jar"),
                testRelative("libsimple-hjar.jar"),
                testRelative("libsimple-src.jar")));

    assertThat(getOutputGroupFiles(testFixture, "intellij-info-java"))
        .containsAllOf(
            testRelative("simple.java-manifest"), testRelative("simple.intellij-info.txt"));
    assertThat(getOutputGroupFiles(testFixture, "intellij-resolve-java"))
        .containsExactly(
            testRelative("libsimple.jar"),
            testRelative("libsimple-hjar.jar"),
            testRelative("libsimple-src.jar"));
    assertThat(getOutputGroupFiles(testFixture, "intellij-compile-java"))
        .containsExactly(testRelative("libsimple.jar"));

    assertThat(getOutputGroupFiles(testFixture, "intellij-info-generic")).isEmpty();

    assertThat(target.getJavaIdeInfo().getJdeps().getRelativePath())
        .isEqualTo(testRelative("libsimple.jdeps"));

    assertThat(target.getJavaIdeInfo().getMainClass()).isEmpty();
  }
}
