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
package com.google.idea.blaze.aspect.java.javaplugin;

import static com.google.common.truth.Truth.assertThat;
import static java.util.stream.Collectors.toList;

import com.google.devtools.intellij.IntellijAspectTestFixtureOuterClass.IntellijAspectTestFixture;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.TargetIdeInfo;
import com.google.idea.blaze.BazelIntellijAspectTest;
import com.google.idea.blaze.aspect.IntellijAspectTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests java_plugin */
@RunWith(JUnit4.class)
public class JavaPluginTest extends BazelIntellijAspectTest {
  @Test
  public void testJavaPlugin() throws Exception {
    IntellijAspectTestFixture testFixture = loadTestFixture(":java_plugin_fixture");
    TargetIdeInfo plugin = findTarget(testFixture, ":plugin");

    assertThat(plugin.getKindString()).isEqualTo("java_plugin");
    assertThat(
            plugin
                .getJavaIdeInfo()
                .getJarsList()
                .stream()
                .map(IntellijAspectTest::libraryArtifactToString)
                .collect(toList()))
        .containsExactly(
            jarString(
                testRelative("libplugin.jar"),
                testRelative("libplugin-hjar.jar"),
                testRelative("libplugin-src.jar")));
  }
}
