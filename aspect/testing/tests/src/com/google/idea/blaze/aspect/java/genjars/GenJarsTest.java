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
package com.google.idea.blaze.aspect.java.genjars;

import static com.google.common.truth.Truth.assertThat;
import static java.util.stream.Collectors.toList;

import com.google.devtools.intellij.IntellijAspectTestFixtureOuterClass.IntellijAspectTestFixture;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.TargetIdeInfo;
import com.google.idea.blaze.BazelIntellijAspectTest;
import com.google.idea.blaze.aspect.IntellijAspectTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests generated jars from java libraries. */
@RunWith(JUnit4.class)
public class GenJarsTest extends BazelIntellijAspectTest {
  @Test
  public void testFilteredGenJarNotCreatedForSourceOnlyRule() throws Exception {
    IntellijAspectTestFixture testFixture = loadTestFixture(":no_plugin_fixture");
    TargetIdeInfo targetIdeInfo = findTarget(testFixture, ":no_plugin");
    assertThat(targetIdeInfo.getJavaIdeInfo().getGeneratedJarsList()).isEmpty();
  }

  @Test
  public void testJavaLibraryWithGeneratedSourcesHasGenJars() throws Exception {
    IntellijAspectTestFixture testFixture = loadTestFixture(":has_plugin_fixture");
    TargetIdeInfo targetIdeInfo = findTarget(testFixture, ":has_plugin");

    assertThat(
            targetIdeInfo
                .getJavaIdeInfo()
                .getGeneratedJarsList()
                .stream()
                .map(IntellijAspectTest::libraryArtifactToString)
                .collect(toList()))
        .containsExactly(
            jarString(
                testRelative("libhas_plugin-gen.jar"),
                null,
                testRelative("libhas_plugin-gensrc.jar")));

    assertThat(getOutputGroupFiles(testFixture, "intellij-info-java"))
        .containsAtLeast(
            testRelative("has_plugin.java-manifest"),
            testRelative(intellijInfoFileName("has_plugin")));
    assertThat(getOutputGroupFiles(testFixture, "intellij-resolve-java"))
        .containsAtLeast(
            testRelative("libhas_plugin-gen.jar"), testRelative("libhas_plugin-gensrc.jar"));
    assertThat(getOutputGroupFiles(testFixture, "intellij-compile-java"))
        .containsAtLeast(testRelative("libhas_plugin.jar"), testRelative("libhas_plugin-gen.jar"));

    assertThat(getOutputGroupFiles(testFixture, "intellij-info-generic")).isEmpty();
  }
}
