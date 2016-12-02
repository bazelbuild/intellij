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
package com.google.idea.blaze.base.targetmaps;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableMultimap;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.ideinfo.TargetMapBuilder;
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
    TargetMapBuilder builder = TargetMapBuilder.builder();
    TargetMap targetMap =
        builder
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(sourceRoot("test/BUILD"))
                    .setLabel("//l:l1")
                    .setKind("java_library")
                    .addDependency("//l:l2"))
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(sourceRoot("test/BUILD"))
                    .setLabel("//l:l2")
                    .setKind("java_library"))
            .build();

    ImmutableMultimap<TargetKey, TargetKey> reverseDependencies =
        ReverseDependencyMap.createRdepsMap(targetMap);
    assertThat(reverseDependencies)
        .containsEntry(
            TargetKey.forPlainTarget(new Label("//l:l2")),
            TargetKey.forPlainTarget(new Label("//l:l1")));
  }

  @Test
  public void testLabelDepsOnTwoLabels() {
    TargetMapBuilder builder = TargetMapBuilder.builder();
    TargetMap targetMap =
        builder
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(sourceRoot("test/BUILD"))
                    .setLabel("//l:l1")
                    .setKind("java_library")
                    .addDependency("//l:l2")
                    .addDependency("//l:l3"))
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(sourceRoot("test/BUILD"))
                    .setLabel("//l:l2")
                    .setKind("java_library"))
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(sourceRoot("test/BUILD"))
                    .setLabel("//l:l3")
                    .setKind("java_library"))
            .build();

    ImmutableMultimap<TargetKey, TargetKey> reverseDependencies =
        ReverseDependencyMap.createRdepsMap(targetMap);
    assertThat(reverseDependencies)
        .containsEntry(
            TargetKey.forPlainTarget(new Label("//l:l2")),
            TargetKey.forPlainTarget(new Label("//l:l1")));
    assertThat(reverseDependencies)
        .containsEntry(
            TargetKey.forPlainTarget(new Label("//l:l3")),
            TargetKey.forPlainTarget(new Label("//l:l1")));
  }

  @Test
  public void testTwoLabelsDepOnSameLabel() {
    TargetMapBuilder builder = TargetMapBuilder.builder();
    TargetMap targetMap =
        builder
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(sourceRoot("test/BUILD"))
                    .setLabel("//l:l1")
                    .setKind("java_library")
                    .addDependency("//l:l3"))
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(sourceRoot("test/BUILD"))
                    .setLabel("//l:l2")
                    .addDependency("//l:l3")
                    .setKind("java_library"))
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(sourceRoot("test/BUILD"))
                    .setLabel("//l:l3")
                    .setKind("java_library"))
            .build();

    ImmutableMultimap<TargetKey, TargetKey> reverseDependencies =
        ReverseDependencyMap.createRdepsMap(targetMap);
    assertThat(reverseDependencies)
        .containsEntry(
            TargetKey.forPlainTarget(new Label("//l:l3")),
            TargetKey.forPlainTarget(new Label("//l:l1")));
    assertThat(reverseDependencies)
        .containsEntry(
            TargetKey.forPlainTarget(new Label("//l:l3")),
            TargetKey.forPlainTarget(new Label("//l:l2")));
  }

  @Test
  public void testThreeLevelGraph() {
    TargetMapBuilder builder = TargetMapBuilder.builder();
    TargetMap targetMap =
        builder
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(sourceRoot("test/BUILD"))
                    .setLabel("//l:l1")
                    .setKind("java_library")
                    .addDependency("//l:l3"))
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(sourceRoot("test/BUILD"))
                    .setLabel("//l:l2")
                    .addDependency("//l:l3")
                    .setKind("java_library"))
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(sourceRoot("test/BUILD"))
                    .setLabel("//l:l3")
                    .setKind("java_library"))
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(sourceRoot("test/BUILD"))
                    .setLabel("//l:l4")
                    .addDependency("//l:l3")
                    .setKind("java_library"))
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(sourceRoot("test/BUILD"))
                    .setLabel("//l:l5")
                    .addDependency("//l:l4")
                    .setKind("java_library"))
            .build();

    ImmutableMultimap<TargetKey, TargetKey> reverseDependencies =
        ReverseDependencyMap.createRdepsMap(targetMap);
    assertThat(reverseDependencies)
        .containsEntry(
            TargetKey.forPlainTarget(new Label("//l:l3")),
            TargetKey.forPlainTarget(new Label("//l:l1")));
    assertThat(reverseDependencies)
        .containsEntry(
            TargetKey.forPlainTarget(new Label("//l:l3")),
            TargetKey.forPlainTarget(new Label("//l:l2")));
    assertThat(reverseDependencies)
        .containsEntry(
            TargetKey.forPlainTarget(new Label("//l:l3")),
            TargetKey.forPlainTarget(new Label("//l:l4")));
    assertThat(reverseDependencies)
        .containsEntry(
            TargetKey.forPlainTarget(new Label("//l:l4")),
            TargetKey.forPlainTarget(new Label("//l:l5")));
  }

  private static ArtifactLocation sourceRoot(String relativePath) {
    return ArtifactLocation.builder()
        .setRelativePath(relativePath)
        .setIsSource(true)
        .build();
  }
}
