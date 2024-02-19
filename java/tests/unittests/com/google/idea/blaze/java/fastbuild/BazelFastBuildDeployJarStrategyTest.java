/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.java.fastbuild;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.bazel.BazelVersion;
import com.google.idea.blaze.base.model.BlazeVersionData;
import com.google.idea.blaze.base.model.primitives.Label;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link BazelFastBuildDeployJarStrategy}. */
@RunWith(JUnit4.class)
public final class BazelFastBuildDeployJarStrategyTest {

  private BazelFastBuildDeployJarStrategy bazelFastBuildDeployJarStrategy;

  private static final BlazeVersionData BAZEL_7_1_0 =
      BlazeVersionData.builder().setBazelVersion(new BazelVersion(7, 1, 0)).build();
  private static final BlazeVersionData BAZEL_6_5_0 =
      BlazeVersionData.builder().setBazelVersion(new BazelVersion(6, 5, 0)).build();

  @Before
  public void setUp() {
    bazelFastBuildDeployJarStrategy = new BazelFastBuildDeployJarStrategy();
  }

  @Test
  public void buildTargetsBazel_7_1_0() {
    Label testTarget = Label.create("//pkg:foo_test");

    ImmutableList<String> buildTargets =
        bazelFastBuildDeployJarStrategy.getBuildTargets(testTarget, BAZEL_7_1_0).stream()
            .map(t -> t.toString())
            .collect(toImmutableList());

    assertThat(buildTargets).containsExactly("//pkg:foo_test");
  }

  @Test
  public void buildTargetsBeforeBazel_6_5_0() {
    Label testTarget = Label.create("//pkg:foo_test");

    ImmutableList<String> buildTargets =
        bazelFastBuildDeployJarStrategy.getBuildTargets(testTarget, BAZEL_6_5_0).stream()
            .map(t -> t.toString())
            .collect(toImmutableList());

    assertThat(buildTargets).containsExactly("//pkg:foo_test_deploy.jar", "//pkg:foo_test");
  }

  @Test
  public void buildFlagsBazel_7_1_0() {
    ImmutableList<String> buildFlags = bazelFastBuildDeployJarStrategy.getBuildFlags(BAZEL_7_1_0);

    assertThat(buildFlags)
        .containsExactly(
            "--experimental_java_test_auto_create_deploy_jar",
            "--output_groups=+_hidden_top_level_INTERNAL_");
  }

  @Test
  public void buildFlagsBeforeBazel_6_5_0() {
    ImmutableList<String> buildFlags = bazelFastBuildDeployJarStrategy.getBuildFlags(BAZEL_6_5_0);

    assertThat(buildFlags).isEmpty();
  }

  @Test
  public void deployJarLabelBazel_7_1_0() {
    Label testTarget = Label.create("//pkg:foo_test");

    Label deployJarLabel =
        bazelFastBuildDeployJarStrategy.createDeployJarLabel(testTarget, BAZEL_7_1_0);

    assertThat(deployJarLabel.toString()).isEqualTo("//pkg:foo_test_auto_deploy.jar");
  }

  @Test
  public void deployJarLabelBeforeBazel_6_5_0() {
    Label testTarget = Label.create("//pkg:foo_test");

    Label deployJarLabel =
        bazelFastBuildDeployJarStrategy.createDeployJarLabel(testTarget, BAZEL_6_5_0);

    assertThat(deployJarLabel.toString()).isEqualTo("//pkg:foo_test_deploy.jar");
  }

  @Test
  public void deployJarOwnerLabelBazel_7_1_0() {
    Label testTarget = Label.create("//pkg:foo_test");

    Label deployJarOwnerLabel =
        bazelFastBuildDeployJarStrategy.deployJarOwnerLabel(testTarget, BAZEL_7_1_0);

    assertThat(deployJarOwnerLabel).isEqualTo(testTarget);
  }

  @Test
  public void deployJarOwnerLabelBeforeBazel_6_5_0() {
    Label testTarget = Label.create("//pkg:foo_test");

    Label deployJarOwnerLabel =
        bazelFastBuildDeployJarStrategy.deployJarOwnerLabel(testTarget, BAZEL_6_5_0);

    assertThat(deployJarOwnerLabel.toString()).isEqualTo("//pkg:foo_test_deploy.jar");
  }
}
