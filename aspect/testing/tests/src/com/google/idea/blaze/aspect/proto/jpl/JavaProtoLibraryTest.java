/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
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
    IntellijAspectTestFixture testFixture = loadTestFixture(":java_proto_library_fixture");

    TargetIdeInfo jpl = findTarget(testFixture, ":foo_java_proto");
    assertThat(jpl).isNotNull();

    // We don't want java_proto_library to be rolling up any jars
    assertThat(jpl.getJavaIdeInfo().getJarsList()).isEmpty();

    // We shouldn't have reached the underlying base proto_library
    assertThat(findTarget(testFixture, ":foo_proto")).isNull();

    TargetIdeInfo proto = findAspectTarget(testFixture, ":foo_proto", "java_proto_library");
    assertThat(proto).isNotNull();

    // jpl -> (proto + jpl aspect)
    assertThat(jpl.getDepsList())
        .contains(
            Dependency.newBuilder()
                .setDependencyType(DependencyType.COMPILE_TIME)
                .setTarget(proto.getKey())
                .build());

    // Make sure we suppress the proto_library legacy provider info
    assertThat(proto.hasProtoLibraryLegacyJavaIdeInfo()).isFalse();

    assertThat(proto.hasJavaIdeInfo()).isTrue();
    List<String> jarStrings =
        proto
            .getJavaIdeInfo()
            .getJarsList()
            .stream()
            .map(IntellijAspectTest::libraryArtifactToString)
            .collect(toList());
    assertThat(jarStrings)
        .containsExactly(
            jarString(
                testRelative("libfoo_proto-speed.jar"),
                testRelative("libfoo_proto-speed-hjar.jar"),
                testRelative("foo_proto-speed-src.jar")));
  }
}
