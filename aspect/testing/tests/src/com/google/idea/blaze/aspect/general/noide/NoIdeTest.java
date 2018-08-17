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
package com.google.idea.blaze.aspect.general.noide;

import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.intellij.IntellijAspectTestFixtureOuterClass.IntellijAspectTestFixture;
import com.google.idea.blaze.BazelIntellijAspectTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests the "noide" tag */
@RunWith(JUnit4.class)
public class NoIdeTest extends BazelIntellijAspectTest {
  @Test
  public void testNoIde() throws Exception {
    IntellijAspectTestFixture testFixture = loadTestFixture(":noide_fixture");
    assertThat(findTarget(testFixture, ":foo")).isNotNull();
    assertThat(findTarget(testFixture, ":bar")).isNull();
    assertThat(findTarget(testFixture, ":baz")).isNull();

    assertThat(getOutputGroupFiles(testFixture, "intellij-info-java"))
        .containsAllOf(
            testRelative("foo.java-manifest"), testRelative(intellijInfoFileName("foo")));
    assertThat(getOutputGroupFiles(testFixture, "intellij-resolve-java"))
        .containsExactly(
            testRelative("libfoo.jar"),
            testRelative("libfoo-hjar.jar"),
            testRelative("libfoo-src.jar"),
            testRelative("libfoo.jdeps"));
    assertThat(getOutputGroupFiles(testFixture, "intellij-compile-java"))
        .containsExactly(testRelative("libfoo.jar"));
  }
}
