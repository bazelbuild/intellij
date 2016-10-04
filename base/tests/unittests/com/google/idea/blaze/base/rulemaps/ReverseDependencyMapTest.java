/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.rulemaps;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableMultimap;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.RuleIdeInfo;
import com.google.idea.blaze.base.ideinfo.RuleMapBuilder;
import com.google.idea.blaze.base.model.RuleMap;
import com.google.idea.blaze.base.model.primitives.Label;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for the reverse dependency map */
@RunWith(JUnit4.class)
public class ReverseDependencyMapTest extends BlazeTestCase {
  @Override
  protected void initTest(
      @NotNull Container applicationServices, @NotNull Container projectServices) {
    super.initTest(applicationServices, projectServices);
  }

  @Test
  public void testSingleDep() {
    RuleMapBuilder builder = RuleMapBuilder.builder();
    RuleMap ruleMap =
        builder
            .addRule(
                RuleIdeInfo.builder()
                    .setBuildFile(sourceRoot("test/BUILD"))
                    .setLabel("//l:l1")
                    .setKind("java_library")
                    .addDependency("//l:l2"))
            .addRule(
                RuleIdeInfo.builder()
                    .setBuildFile(sourceRoot("test/BUILD"))
                    .setLabel("//l:l2")
                    .setKind("java_library"))
            .build();

    ImmutableMultimap<Label, Label> reverseDependencies =
        ReverseDependencyMap.createRdepsMap(ruleMap);
    assertThat(reverseDependencies).containsEntry(new Label("//l:l2"), new Label("//l:l1"));
  }

  @Test
  public void testLabelDepsOnTwoLabels() {
    RuleMapBuilder builder = RuleMapBuilder.builder();
    RuleMap ruleMap =
        builder
            .addRule(
                RuleIdeInfo.builder()
                    .setBuildFile(sourceRoot("test/BUILD"))
                    .setLabel("//l:l1")
                    .setKind("java_library")
                    .addDependency("//l:l2")
                    .addDependency("//l:l3"))
            .addRule(
                RuleIdeInfo.builder()
                    .setBuildFile(sourceRoot("test/BUILD"))
                    .setLabel("//l:l2")
                    .setKind("java_library"))
            .addRule(
                RuleIdeInfo.builder()
                    .setBuildFile(sourceRoot("test/BUILD"))
                    .setLabel("//l:l3")
                    .setKind("java_library"))
            .build();

    ImmutableMultimap<Label, Label> reverseDependencies =
        ReverseDependencyMap.createRdepsMap(ruleMap);
    assertThat(reverseDependencies).containsEntry(new Label("//l:l2"), new Label("//l:l1"));
    assertThat(reverseDependencies).containsEntry(new Label("//l:l3"), new Label("//l:l1"));
  }

  @Test
  public void testTwoLabelsDepOnSameLabel() {
    RuleMapBuilder builder = RuleMapBuilder.builder();
    RuleMap ruleMap =
        builder
            .addRule(
                RuleIdeInfo.builder()
                    .setBuildFile(sourceRoot("test/BUILD"))
                    .setLabel("//l:l1")
                    .setKind("java_library")
                    .addDependency("//l:l3"))
            .addRule(
                RuleIdeInfo.builder()
                    .setBuildFile(sourceRoot("test/BUILD"))
                    .setLabel("//l:l2")
                    .addDependency("//l:l3")
                    .setKind("java_library"))
            .addRule(
                RuleIdeInfo.builder()
                    .setBuildFile(sourceRoot("test/BUILD"))
                    .setLabel("//l:l3")
                    .setKind("java_library"))
            .build();

    ImmutableMultimap<Label, Label> reverseDependencies =
        ReverseDependencyMap.createRdepsMap(ruleMap);
    assertThat(reverseDependencies).containsEntry(new Label("//l:l3"), new Label("//l:l1"));
    assertThat(reverseDependencies).containsEntry(new Label("//l:l3"), new Label("//l:l2"));
  }

  @Test
  public void testThreeLevelGraph() {
    RuleMapBuilder builder = RuleMapBuilder.builder();
    RuleMap ruleMap =
        builder
            .addRule(
                RuleIdeInfo.builder()
                    .setBuildFile(sourceRoot("test/BUILD"))
                    .setLabel("//l:l1")
                    .setKind("java_library")
                    .addDependency("//l:l3"))
            .addRule(
                RuleIdeInfo.builder()
                    .setBuildFile(sourceRoot("test/BUILD"))
                    .setLabel("//l:l2")
                    .addDependency("//l:l3")
                    .setKind("java_library"))
            .addRule(
                RuleIdeInfo.builder()
                    .setBuildFile(sourceRoot("test/BUILD"))
                    .setLabel("//l:l3")
                    .setKind("java_library"))
            .addRule(
                RuleIdeInfo.builder()
                    .setBuildFile(sourceRoot("test/BUILD"))
                    .setLabel("//l:l4")
                    .addDependency("//l:l3")
                    .setKind("java_library"))
            .addRule(
                RuleIdeInfo.builder()
                    .setBuildFile(sourceRoot("test/BUILD"))
                    .setLabel("//l:l5")
                    .addDependency("//l:l4")
                    .setKind("java_library"))
            .build();

    ImmutableMultimap<Label, Label> reverseDependencies =
        ReverseDependencyMap.createRdepsMap(ruleMap);
    assertThat(reverseDependencies).containsEntry(new Label("//l:l3"), new Label("//l:l1"));
    assertThat(reverseDependencies).containsEntry(new Label("//l:l3"), new Label("//l:l2"));
    assertThat(reverseDependencies).containsEntry(new Label("//l:l3"), new Label("//l:l4"));
    assertThat(reverseDependencies).containsEntry(new Label("//l:l4"), new Label("//l:l5"));
  }

  private static ArtifactLocation sourceRoot(String relativePath) {
    return ArtifactLocation.builder()
        .setRootPath("/")
        .setRelativePath(relativePath)
        .setIsSource(true)
        .build();
  }
}
