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
package com.google.idea.blaze.base.sync.sharding;

import static com.google.common.truth.Truth.assertThat;
import static com.google.idea.blaze.base.sync.sharding.LexicographicTargetSharder.maximumRemoteShardSize;
import static com.google.idea.blaze.base.sync.sharding.LexicographicTargetSharder.parallelThreshold;
import static com.google.idea.blaze.base.sync.sharding.ShardedTargetList.remoteConcurrentSyncs;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.settings.BuildBinaryType;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link LexicographicTargetSharder}. */
@RunWith(JUnit4.class)
public class LexicographicTargetSharderTest extends BlazeTestCase {

  private static final LexicographicTargetSharder lexicographicTargetSharder =
      new LexicographicTargetSharder();
  private static final Label LABEL_ONE = Label.create("//java/com/google:one");
  private static final Label LABEL_TWO = Label.create("//java/com/google:two");
  private static final Label LABEL_THREE = Label.create("//java/com/google:three");
  private static final Label LABEL_FOUR = Label.create("//java/com/google:four");
  private final MockExperimentService mockExperimentService = new MockExperimentService();

  @Override
  protected void initTest(Container applicationServices, Container projectServices) {
    applicationServices.register(ExperimentService.class, mockExperimentService);
  }

  private void setParallelThreshold(int value) {
    mockExperimentService.setExperimentInt(parallelThreshold, value);
  }

  private void setRemoteConcurrentSyncs(int value) {
    mockExperimentService.setExperimentInt(remoteConcurrentSyncs, value);
  }

  private void setMaximumRemoteShardSize(int value) {
    mockExperimentService.setExperimentInt(maximumRemoteShardSize, value);
  }

  @Test
  public void calculateTargetBatches_testLocalBuildType_suggestedSizeIsUsed() {
    setParallelThreshold(1000);
    setRemoteConcurrentSyncs(10);
    setMaximumRemoteShardSize(1000);
    Set<Label> targets = ImmutableSet.of(LABEL_ONE, LABEL_TWO, LABEL_THREE, LABEL_FOUR);
    ImmutableList<ImmutableList<Label>> shardedTargets =
        lexicographicTargetSharder.calculateTargetBatches(targets, BuildBinaryType.BLAZE, 2);
    assertThat(shardedTargets).hasSize(2);
    assertThat(shardedTargets.get(0)).containsExactly(LABEL_FOUR, LABEL_ONE).inOrder();
    assertThat(shardedTargets.get(1)).containsExactly(LABEL_THREE, LABEL_TWO).inOrder();
  }

  @Test
  public void
      calculateTargetBatches_testRemoteBuildTypeAndSuggestedSizeIsSmaller_suggestedSizeIsUsed() {
    Set<Label> targets = ImmutableSet.of(LABEL_ONE, LABEL_TWO, LABEL_THREE, LABEL_FOUR);
    setParallelThreshold(4);
    setRemoteConcurrentSyncs(1);
    setMaximumRemoteShardSize(1000);
    ImmutableList<ImmutableList<Label>> shardedTargets =
        lexicographicTargetSharder.calculateTargetBatches(targets, BuildBinaryType.RABBIT, 2);
    assertThat(shardedTargets).hasSize(2);
    assertThat(shardedTargets.get(0)).containsExactly(LABEL_FOUR, LABEL_ONE).inOrder();
    assertThat(shardedTargets.get(1)).containsExactly(LABEL_THREE, LABEL_TWO).inOrder();
  }

  @Test
  public void
      calculateTargetBatches_testRemoteBuildTypeAndCalculatedSizeIsSmaller_calculatedSizeIsUsed() {
    Set<Label> targets = ImmutableSet.of(LABEL_ONE, LABEL_TWO, LABEL_THREE, LABEL_FOUR);
    setParallelThreshold(4);
    setRemoteConcurrentSyncs(10);
    setMaximumRemoteShardSize(1000);
    ImmutableList<ImmutableList<Label>> shardedTargets =
        lexicographicTargetSharder.calculateTargetBatches(targets, BuildBinaryType.RABBIT, 2);
    assertThat(shardedTargets).hasSize(4);
    assertThat(shardedTargets.get(0)).containsExactly(LABEL_FOUR);
    assertThat(shardedTargets.get(1)).containsExactly(LABEL_ONE);
    assertThat(shardedTargets.get(2)).containsExactly(LABEL_THREE);
    assertThat(shardedTargets.get(3)).containsExactly(LABEL_TWO);
  }

  @Test
  public void
      calculateTargetBatches_testRemoteBuildTypeAndMaximumRemoteShardSizeIsSmaller_maximumRemoteShardSizeIsUsed() {
    Set<Label> targets = ImmutableSet.of(LABEL_ONE, LABEL_TWO, LABEL_THREE, LABEL_FOUR);
    setParallelThreshold(4);
    setRemoteConcurrentSyncs(1);
    setMaximumRemoteShardSize(3);
    ImmutableList<ImmutableList<Label>> shardedTargets =
        lexicographicTargetSharder.calculateTargetBatches(targets, BuildBinaryType.RABBIT, 100);
    assertThat(shardedTargets).hasSize(2);
    assertThat(shardedTargets.get(0)).containsExactly(LABEL_FOUR, LABEL_ONE, LABEL_THREE).inOrder();
    assertThat(shardedTargets.get(1)).containsExactly(LABEL_TWO);
  }
}
