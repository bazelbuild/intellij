/*
 * Copyright 2021 The Bazel Authors. All rights reserved.
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
 * Tests that plugin processor jars from java & kotlin targets are extracted correctly by the
 * aspects for blaze.
 */
@RunWith(JUnit4.class)
public class PluginProcessorJarTest extends BazelIntellijAspectTest {
  // blaze and bazel have different annotation processor paths & names.
  private static final String CUSTOMIZE_LINT_RULE_JAR =
      "third_party/java_src/auto/value/libvalue_processor.jar";
  private static final String CUSTOMIZE_LINT_RULE_JAR_STR =
      jarString(CUSTOMIZE_LINT_RULE_JAR, /*iJar=*/ null, /*sourceJar=*/ null);
  private static final String GLOBAL_LINT_CHECK_JAR_PATH = "java/com/google/android/tools/lint/";

  @Test
  public void ruleWithNoPlugins() throws Exception {
    IntellijAspectTestFixture testFixture = loadTestFixture(":no_plugin_fixture");
    TargetIdeInfo targetIdeInfo = findTarget(testFixture, ":no_plugin");
    assertThat(targetIdeInfo.getJavaIdeInfo().getPluginProcessorJarsList()).isEmpty();
    targetIdeInfo = findTarget(testFixture, ":no_plugin_kt");
    // only kotlin rules can export global lint rule jars
    assertGlobalLintRuleJarIsIncluded(targetIdeInfo, testFixture);
  }

  private void assertCustomizedLintRuleJarIsIncluded(
      TargetIdeInfo targetIdeInfo, IntellijAspectTestFixture testFixture) {
    assertThat(
            targetIdeInfo.getJavaIdeInfo().getPluginProcessorJarsList().stream()
                .map(IntellijAspectTest::libraryArtifactToString)
                .collect(toList()))
        .contains(CUSTOMIZE_LINT_RULE_JAR_STR);

    assertThat(getOutputGroupFiles(testFixture, "intellij-resolve-java"))
        .contains(CUSTOMIZE_LINT_RULE_JAR);
  }

  private void assertGlobalLintRuleJarIsIncluded(
      TargetIdeInfo targetIdeInfo, IntellijAspectTestFixture testFixture) {
    assertThat(targetIdeInfo.getJavaIdeInfo().getPluginProcessorJarsList()).isNotEmpty();
    assertThat(
            targetIdeInfo.getJavaIdeInfo().getPluginProcessorJarsList().stream()
                .map(IntellijAspectTest::libraryArtifactToString)
                .anyMatch(jar -> jar.contains(GLOBAL_LINT_CHECK_JAR_PATH)))
        .isTrue();
    assertThat(
            getOutputGroupFiles(testFixture, "intellij-resolve-java").stream()
                .anyMatch(jar -> jar.contains(GLOBAL_LINT_CHECK_JAR_PATH)))
        .isTrue();
  }

  @Test
  public void ruleWithPlugins_createsPluginProcessorJars() throws Exception {
    IntellijAspectTestFixture testFixture = loadTestFixture(":has_plugin_fixture");
    TargetIdeInfo targetIdeInfo = findTarget(testFixture, ":has_plugin");
    assertCustomizedLintRuleJarIsIncluded(targetIdeInfo, testFixture);
    targetIdeInfo = findTarget(testFixture, ":has_plugin_kt");
    assertCustomizedLintRuleJarIsIncluded(targetIdeInfo, testFixture);
    assertGlobalLintRuleJarIsIncluded(targetIdeInfo, testFixture);
  }

  @Test
  public void ruleWithDeps_createsPluginProcessorJars() throws Exception {
    IntellijAspectTestFixture testFixture = loadTestFixture(":has_plugin_deps_fixture");
    TargetIdeInfo targetIdeInfo = findTarget(testFixture, ":has_plugin_deps");
    assertCustomizedLintRuleJarIsIncluded(targetIdeInfo, testFixture);
    targetIdeInfo = findTarget(testFixture, ":has_plugin_deps_kt");
    assertCustomizedLintRuleJarIsIncluded(targetIdeInfo, testFixture);
    assertGlobalLintRuleJarIsIncluded(targetIdeInfo, testFixture);
  }
}
