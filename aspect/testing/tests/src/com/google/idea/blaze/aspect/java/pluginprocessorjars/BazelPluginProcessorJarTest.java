/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.aspect.java.pluginprocessorjars;

import static com.google.common.truth.Truth.assertThat;
import static java.util.stream.Collectors.toList;

import com.google.devtools.intellij.IntellijAspectTestFixtureOuterClass.IntellijAspectTestFixture;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.TargetIdeInfo;
import com.google.idea.blaze.BazelIntellijAspectTest;
import com.google.idea.blaze.aspect.IntellijAspectTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests that plugin processor jars from java targets are extracted correctly by the aspects for
 * bazel.
 */
@RunWith(JUnit4.class)
public class BazelPluginProcessorJarTest extends BazelIntellijAspectTest {
  // The external repository name in these paths is simplified, actual paths will be mapped to match them.
  private static final String JAR_STR =
      jarString("external/maven/com/google/auto/value/auto-value/1.10.4/processed_auto-value-1.10.4.jar", /*iJar=*/ null, /*sourceJar=*/ null);
  private static final String OUTPUT_GROUP_FILES = "../maven/com/google/auto/value/auto-value/1.10.4/processed_auto-value-1.10.4.jar";

  @Test
  public void ruleWithNoPlugins() throws Exception {
    IntellijAspectTestFixture testFixture = loadTestFixture(":no_plugin_fixture");
    TargetIdeInfo targetIdeInfo = findTarget(testFixture, ":no_plugin");
    assertThat(targetIdeInfo.getJavaIdeInfo().getPluginProcessorJarsList()).isEmpty();
  }

  @Test
  public void ruleWithPlugins_createsPluginProcessorJars() throws Exception {
    IntellijAspectTestFixture testFixture = loadTestFixture(":has_plugin_fixture");
    TargetIdeInfo targetIdeInfo = findTarget(testFixture, ":has_plugin");

    assertThat(
            targetIdeInfo.getJavaIdeInfo().getPluginProcessorJarsList().stream()
                .map(IntellijAspectTest::libraryArtifactToString)
                .map(p -> simplifyPath(p))
                .collect(toList()))
        .contains(JAR_STR);

    assertThat(
            getOutputGroupFiles(testFixture, "intellij-resolve-java").stream()
                .map(p -> simplifyPath(p))
                .collect(toList()))
        .contains(OUTPUT_GROUP_FILES);
  }

  @Test
  public void ruleWithDeps_createsPluginProcessorJars() throws Exception {
    IntellijAspectTestFixture testFixture = loadTestFixture(":has_plugin_deps_fixture");
    TargetIdeInfo targetIdeInfo = findTarget(testFixture, ":has_plugin_deps");

    assertThat(
            targetIdeInfo.getJavaIdeInfo().getPluginProcessorJarsList().stream()
                .map(IntellijAspectTest::libraryArtifactToString)
                .map(p -> simplifyPath(p))
                .collect(toList()))
        .contains(JAR_STR);

    assertThat(
            getOutputGroupFiles(testFixture, "intellij-resolve-java").stream()
                .map(p -> simplifyPath(p))
                .collect(toList()))
        .contains(OUTPUT_GROUP_FILES);
  }

  /** Simplify external dependencies paths by simplifying the repository name part
   * For example,
   * `external/rules_jvm_external~5.3~maven~com_google_guava_guava/jar` will be 
   * `external/com_google_guava_guava/jar`
   */
  private static String simplifyPath(String path) {
    return path.replaceAll("/.*~", "/");
  }
}
