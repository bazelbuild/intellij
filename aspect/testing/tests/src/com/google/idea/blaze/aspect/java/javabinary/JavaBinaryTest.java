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
package com.google.idea.blaze.aspect.java.javabinary;

import static com.google.common.truth.Truth.assertThat;
import static java.util.stream.Collectors.toList;

import com.google.devtools.intellij.IntellijAspectTestFixtureOuterClass.IntellijAspectTestFixture;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.TargetIdeInfo;
import com.google.idea.blaze.BazelIntellijAspectTest;
import com.google.idea.blaze.aspect.IntellijAspectTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests java_binary */
@RunWith(JUnit4.class)
public class JavaBinaryTest extends BazelIntellijAspectTest {
  @Test
  public void testJavaBinary() throws Exception {
    IntellijAspectTestFixture testFixture = loadTestFixture(":foo_fixture");
    TargetIdeInfo binaryInfo = findTarget(testFixture, ":foo");

    assertThat(binaryInfo.getKindString()).isEqualTo("java_binary");
    assertThat(relativePathsForArtifacts(binaryInfo.getJavaIdeInfo().getSourcesList()))
        .containsExactly(testRelative("FooMain.java"));
    assertThat(dependenciesForTarget(binaryInfo)).contains(dep(":foolib"));

    assertThat(
            binaryInfo
                .getJavaIdeInfo()
                .getJarsList()
                .stream()
                .map(IntellijAspectTest::libraryArtifactToString)
                .collect(toList()))
        .containsExactly(jarString(testRelative("foo.jar"), null, testRelative("foo-src.jar")));

    assertThat(binaryInfo.getJavaIdeInfo().getMainClass()).isEqualTo("com.google.MyMainClass");

    assertThat(getOutputGroupFiles(testFixture, "intellij-info-java"))
        .containsAtLeast(
            testRelative("foolib.java-manifest"),
            testRelative(intellijInfoFileName("foolib")),
            testRelative("foo.java-manifest"),
            testRelative(intellijInfoFileName("foo")));
    assertThat(getOutputGroupFiles(testFixture, "intellij-resolve-java"))
        .containsAtLeast(
            testRelative("libfoolib.jar"),
            testRelative("libfoolib-hjar.jar"),
            testRelative("libfoolib-src.jar"),
            testRelative("foo.jar"),
            testRelative("foo-src.jar"));
    assertThat(getOutputGroupFiles(testFixture, "intellij-compile-java"))
        .containsExactly(testRelative("libfoolib.jar"), testRelative("foo.jar"));

    assertThat(binaryInfo.getJavaIdeInfo().getJdeps().getRelativePath())
        .isEqualTo(testRelative("foo.jdeps"));
    assertThat(getOutputGroupFiles(testFixture, "intellij-info-generic")).isEmpty();
  }
}
