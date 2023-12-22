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
  public void testExports() throws Exception {
    IntellijAspectTestFixture testFixture = loadTestFixture(":foo_exports_fixture");
    TargetIdeInfo target = findTarget(testFixture, ":foo_exports");

    // transitive exports should be rolled up into direct deps
    assertThat(target.getDepsList())
        .containsAllOf(
            dep(":exports_direct"), dep(":direct"), dep(":exports_indirect"), dep(":indirect"));
    assertThat(target.getDepsList()).doesNotContain(dep(":distant"));

    // intellij-info groups
    assertThat(getOutputGroupFiles(testFixture, "intellij-info-java"))
        .containsAllOf(
            testRelative("foo_exports.java-manifest"),
            testRelative(intellijInfoFileName("foo_exports")),
            testRelative(intellijInfoFileName("exports_direct")),
            testRelative("direct.java-manifest"),
            testRelative(intellijInfoFileName("direct")),
            testRelative(intellijInfoFileName("exports_indirect")),
            testRelative("indirect.java-manifest"),
            testRelative(intellijInfoFileName("indirect")),
            testRelative("distant.java-manifest"),
            testRelative(intellijInfoFileName("distant")));

    assertThat(getOutputGroupFiles(testFixture, "intellij-info-java-outputs"))
        .containsExactly(
            testRelative("foo_exports.java-manifest"),
            testRelative(intellijInfoFileName("foo_exports")));

    assertThat(getOutputGroupFiles(testFixture, "intellij-info-java-direct-deps"))
        .containsAllOf(
            testRelative("foo_exports.java-manifest"),
            testRelative(intellijInfoFileName("foo_exports")),
            testRelative(intellijInfoFileName("exports_direct")),
            testRelative("direct.java-manifest"),
            testRelative(intellijInfoFileName("direct")),
            testRelative(intellijInfoFileName("exports_indirect")),
            testRelative("indirect.java-manifest"),
            testRelative(intellijInfoFileName("indirect")));
    assertThat(getOutputGroupFiles(testFixture, "intellij-info-java-direct-deps"))
        .containsNoneOf(
            testRelative("distant.java-manifest"), testRelative(intellijInfoFileName("distant")));
  }

  @Test
  public void testJavaLibrary() throws Exception {
    IntellijAspectTestFixture testFixture = loadTestFixture(":foo_fixture");
    TargetIdeInfo target = findTarget(testFixture, ":foo");

    assertThat(target.getKindString()).isEqualTo("java_library");
    assertThat(target.hasJavaIdeInfo()).isTrue();
    assertThat(target.hasCIdeInfo()).isFalse();
    assertThat(target.hasAndroidIdeInfo()).isFalse();
    assertThat(target.hasPyIdeInfo()).isFalse();

    assertThat(relativePathsForArtifacts(target.getJavaIdeInfo().getSourcesList()))
        .containsExactly(testRelative("Foo.java"));
    assertThat(
            target.getJavaIdeInfo().getJarsList().stream()
                .map(IntellijAspectTest::libraryArtifactToString)
                .collect(toList()))
        .containsExactly(
            jarString(
                testRelative("libfoo.jar"),
                testRelative("libfoo-hjar.jar"),
                testRelative("libfoo-src.jar")));

    // intellij-info groups
    assertThat(getOutputGroupFiles(testFixture, "intellij-info-java"))
        .containsAllOf(
            testRelative("foo.java-manifest"), testRelative(intellijInfoFileName("foo")),
            testRelative("direct.java-manifest"), testRelative(intellijInfoFileName("direct")),
            testRelative("indirect.java-manifest"), testRelative(intellijInfoFileName("indirect")),
            testRelative("distant.java-manifest"), testRelative(intellijInfoFileName("distant")));

    assertThat(getOutputGroupFiles(testFixture, "intellij-info-java-outputs"))
        .containsExactly(
            testRelative("foo.java-manifest"), testRelative(intellijInfoFileName("foo")));

    assertThat(getOutputGroupFiles(testFixture, "intellij-info-java-direct-deps"))
        .containsAllOf(
            testRelative("foo.java-manifest"), testRelative(intellijInfoFileName("foo")),
            testRelative("direct.java-manifest"), testRelative(intellijInfoFileName("direct")));
    assertThat(getOutputGroupFiles(testFixture, "intellij-info-java-direct-deps"))
        .containsNoneOf(
            testRelative("indirect.java-manifest"), testRelative(intellijInfoFileName("indirect")),
            testRelative("distant.java-manifest"), testRelative(intellijInfoFileName("distant")));

    // intellij-resolve groups
    assertThat(getOutputGroupFiles(testFixture, "intellij-resolve-java"))
        .containsExactly(
            // foo
            testRelative("libfoo.jar"),
            testRelative("libfoo-hjar.jar"),
            testRelative("libfoo-src.jar"),
            testRelative("libfoo.jdeps"),
            // direct
            testRelative("libdirect.jar"),
            testRelative("libdirect-hjar.jar"),
            testRelative("libdirect-src.jar"),
            testRelative("libdirect.jdeps"),
            // indirect
            testRelative("libindirect.jar"),
            testRelative("libindirect-hjar.jar"),
            testRelative("libindirect-src.jar"),
            testRelative("libindirect.jdeps"),
            // distant
            testRelative("libdistant.jar"),
            testRelative("libdistant-hjar.jar"),
            testRelative("libdistant-src.jar"),
            testRelative("libdistant.jdeps"));
    assertThat(getOutputGroupFiles(testFixture, "intellij-resolve-java-outputs"))
        .containsExactly(
            testRelative("libfoo.jar"),
            testRelative("libfoo-hjar.jar"),
            testRelative("libfoo-src.jar"),
            testRelative("libfoo.jdeps"));
    assertThat(getOutputGroupFiles(testFixture, "intellij-resolve-java-direct-deps"))
        .containsExactly(
            // foo
            testRelative("libfoo.jar"),
            testRelative("libfoo-hjar.jar"),
            testRelative("libfoo-src.jar"),
            testRelative("libfoo.jdeps"),
            // direct
            testRelative("libdirect.jar"),
            testRelative("libdirect-hjar.jar"),
            testRelative("libdirect-src.jar"),
            testRelative("libdirect.jdeps"),
            // indirect (only hjar and src-jar)
            testRelative("libindirect-hjar.jar"),
            testRelative("libindirect-src.jar"),
            // distant (only hjar and src-jar)
            testRelative("libdistant-hjar.jar"),
            testRelative("libdistant-src.jar"));

    // intellij-compile groups
    assertThat(getOutputGroupFiles(testFixture, "intellij-compile-java"))
        .containsExactly(
            testRelative("libfoo.jar"),
            testRelative("libdirect.jar"),
            testRelative("libindirect.jar"),
            testRelative("libdistant.jar"));
    assertThat(getOutputGroupFiles(testFixture, "intellij-compile-java-outputs"))
        .containsExactly(testRelative("libfoo.jar"));
    assertThat(getOutputGroupFiles(testFixture, "intellij-compile-java-direct-deps"))
        .containsExactly(testRelative("libfoo.jar"), testRelative("libdirect.jar"));

    assertThat(getOutputGroupFiles(testFixture, "intellij-info-generic")).isEmpty();

    assertThat(target.getJavaIdeInfo().getJdeps().getRelativePath())
        .isEqualTo(testRelative("libfoo.jdeps"));

    assertThat(target.getJavaIdeInfo().getMainClass()).isEmpty();
  }
}
