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
package com.google.idea.blaze.aspect.proto.jpl;

import static com.google.common.truth.Truth.assertThat;
import static java.util.stream.Collectors.toList;

import com.google.devtools.intellij.IntellijAspectTestFixtureOuterClass.IntellijAspectTestFixture;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.Dependency;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.Dependency.DependencyType;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.TargetIdeInfo;
import com.google.idea.blaze.BazelIntellijAspectTest;
import com.google.idea.blaze.aspect.IntellijAspectTest;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for java_proto_library. */
@RunWith(JUnit4.class)
public class JavaProtoLibraryTest extends BazelIntellijAspectTest {

  @Test
  public void testJavaProtoLibrary() throws Exception {
    IntellijAspectTestFixture testFixture = loadTestFixture(":lib_fixture");

    TargetIdeInfo lib = findTarget(testFixture, ":lib");
    assertThat(lib).isNotNull();

    TargetIdeInfo jpl = findTarget(testFixture, ":bar_java_proto");
    assertThat(jpl).isNotNull();

    // We don't want java_proto_library to be rolling up any jars
    assertThat(jpl.getJavaIdeInfo().getJarsList()).isEmpty();

    // We shouldn't have reached the underlying base proto_library's
    assertThat(findTarget(testFixture, ":bar_proto")).isNull();
    assertThat(findTarget(testFixture, ":foo_proto")).isNull();

    TargetIdeInfo barProto =
        findAspectTarget(testFixture, ":bar_proto", "JavaProtoAspect", "java_proto_aspect");
    TargetIdeInfo fooProto =
        findAspectTarget(testFixture, ":foo_proto", "JavaProtoAspect", "java_proto_aspect");
    assertThat(barProto).isNotNull();
    assertThat(fooProto).isNotNull();

    // jpl -> (proto + jpl aspect)
    assertThat(jpl.getDepsList())
        .contains(
            Dependency.newBuilder()
                .setDependencyType(DependencyType.COMPILE_TIME)
                .setTarget(barProto.getKey())
                .build());

    // Make sure we suppress the proto_library legacy provider info

    assertThat(barProto.hasJavaIdeInfo()).isTrue();
    List<String> jarStrings =
        barProto
            .getJavaIdeInfo()
            .getJarsList()
            .stream()
            .map(IntellijAspectTest::libraryArtifactToString)
            .collect(toList());
    assertThat(jarStrings)
        .containsExactly(
            jarString(
                testRelative("libbar_proto-speed.jar"),
                testRelative("libbar_proto-speed-hjar.jar"),
                testRelative("bar_proto-speed-src.jar")));

    // intellij-info groups
    assertThat(getOutputGroupFiles(testFixture, "intellij-info-java"))
        .containsAtLeast(
            testRelative("lib.java-manifest"),
            testRelative(intellijInfoFileName("lib")),
            testRelative(intellijInfoFileName("bar_java_proto")),
            testRelative(intellijInfoFileName(barProto.getKey())),
            testRelative(intellijInfoFileName(fooProto.getKey())));

    assertThat(getOutputGroupFiles(testFixture, "intellij-info-java-outputs"))
        .containsExactly(
            testRelative("lib.java-manifest"), testRelative(intellijInfoFileName("lib")));

    assertThat(getOutputGroupFiles(testFixture, "intellij-info-java-direct-deps"))
        .containsAtLeast(
            testRelative("lib.java-manifest"),
            testRelative(intellijInfoFileName("lib")),
            testRelative(intellijInfoFileName("bar_java_proto")),
            testRelative(intellijInfoFileName(barProto.getKey())));
    assertThat(getOutputGroupFiles(testFixture, "intellij-info-java-direct-deps"))
        .doesNotContain(testRelative(intellijInfoFileName(fooProto.getKey())));

    // intellij-resolve groups
    assertThat(getOutputGroupFiles(testFixture, "intellij-resolve-java"))
        .containsExactly(
            // lib
            testRelative("liblib-hjar.jar"),
            testRelative("liblib-src.jar"),
            testRelative("liblib.jdeps"),
            // bar_proto
            testRelative("libbar_proto-speed-hjar.jar"),
            testRelative("bar_proto-speed-src.jar"),
            // foo_proto
            testRelative("libfoo_proto-speed-hjar.jar"),
            testRelative("foo_proto-speed-src.jar"));
    assertThat(getOutputGroupFiles(testFixture, "intellij-resolve-java-outputs"))
        .containsExactly(
            testRelative("liblib-hjar.jar"),
            testRelative("liblib-src.jar"),
            testRelative("liblib.jdeps"));
    assertThat(getOutputGroupFiles(testFixture, "intellij-resolve-java-direct-deps"))
        .containsAtLeast(
            // lib
            testRelative("liblib-hjar.jar"),
            testRelative("liblib-src.jar"),
            testRelative("liblib.jdeps"),
            // bar_proto
            testRelative("libbar_proto-speed-hjar.jar"),
            testRelative("bar_proto-speed-src.jar"),
            // foo_proto (only hjar)
            testRelative("libfoo_proto-speed-hjar.jar"));

    // intellij-compile groups
    assertThat(getOutputGroupFiles(testFixture, "intellij-compile-java"))
        .containsExactly(
            testRelative("liblib.jar"),
            testRelative("libbar_proto-speed.jar"),
            testRelative("libfoo_proto-speed.jar"));
    assertThat(getOutputGroupFiles(testFixture, "intellij-compile-java-outputs"))
        .containsExactly(testRelative("liblib.jar"));
    assertThat(getOutputGroupFiles(testFixture, "intellij-compile-java-direct-deps"))
        .containsExactly(testRelative("liblib.jar"), testRelative("libbar_proto-speed.jar"));
  }
}
