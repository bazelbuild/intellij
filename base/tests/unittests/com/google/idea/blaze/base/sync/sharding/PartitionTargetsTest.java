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
package com.google.idea.blaze.base.sync.sharding;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test that targets are correctly partitioned in {@link BlazeBuildTargetSharder#shardTargets}. */
@RunWith(JUnit4.class)
public class PartitionTargetsTest extends BlazeTestCase {

  @Override
  protected void initTest(Container applicationServices, Container projectServices) {
    applicationServices.register(ExperimentService.class, new MockExperimentService());
  }

  @Test
  public void testShardSizeRespected() {
    List<TargetExpression> targets =
        ImmutableList.of(
            TargetExpression.fromStringSafe("//java/com/google:one"),
            TargetExpression.fromStringSafe("//java/com/google:two"),
            TargetExpression.fromStringSafe("//java/com/google:three"),
            TargetExpression.fromStringSafe("//java/com/google:four"),
            TargetExpression.fromStringSafe("//java/com/google:five"));
    ShardedTargetList shards = BlazeBuildTargetSharder.shardTargets(targets, 2);
    assertThat(shards.shardedTargets).hasSize(3);
    assertThat(shards.shardedTargets.get(0)).hasSize(2);
    assertThat(shards.shardedTargets.get(1)).hasSize(2);
    assertThat(shards.shardedTargets.get(2)).hasSize(1);

    shards = BlazeBuildTargetSharder.shardTargets(targets, 4);
    assertThat(shards.shardedTargets).hasSize(2);
    assertThat(shards.shardedTargets.get(0)).hasSize(4);
    assertThat(shards.shardedTargets.get(1)).hasSize(1);

    shards = BlazeBuildTargetSharder.shardTargets(targets, 100);
    assertThat(shards.shardedTargets).hasSize(1);
    assertThat(shards.shardedTargets.get(0)).hasSize(5);
  }

  @Test
  public void testAllSubsequentExcludedTargetsAppendedToShards() {
    List<TargetExpression> targets =
        ImmutableList.of(
            TargetExpression.fromStringSafe("//java/com/google:one"),
            TargetExpression.fromStringSafe("-//java/com/google:two"),
            TargetExpression.fromStringSafe("//java/com/google:three"),
            TargetExpression.fromStringSafe("-//java/com/google:four"),
            TargetExpression.fromStringSafe("//java/com/google:five"),
            TargetExpression.fromStringSafe("-//java/com/google:six"));
    ShardedTargetList shards = BlazeBuildTargetSharder.shardTargets(targets, 3);
    assertThat(shards.shardedTargets).hasSize(2);
    assertThat(shards.shardedTargets.get(0)).hasSize(5);
    assertThat(shards.shardedTargets.get(0))
        .isEqualTo(
            ImmutableList.of(
                TargetExpression.fromStringSafe("//java/com/google:one"),
                TargetExpression.fromStringSafe("-//java/com/google:two"),
                TargetExpression.fromStringSafe("//java/com/google:three"),
                TargetExpression.fromStringSafe("-//java/com/google:four"),
                TargetExpression.fromStringSafe("-//java/com/google:six")));
    assertThat(shards.shardedTargets.get(1)).hasSize(3);
    assertThat(shards.shardedTargets.get(1))
        .containsExactly(
            TargetExpression.fromStringSafe("-//java/com/google:four"),
            TargetExpression.fromStringSafe("//java/com/google:five"),
            TargetExpression.fromStringSafe("-//java/com/google:six"))
        .inOrder();

    shards = BlazeBuildTargetSharder.shardTargets(targets, 1);
    assertThat(shards.shardedTargets).hasSize(3);
    assertThat(shards.shardedTargets.get(0))
        .containsExactly(
            TargetExpression.fromStringSafe("//java/com/google:one"),
            TargetExpression.fromStringSafe("-//java/com/google:two"),
            TargetExpression.fromStringSafe("-//java/com/google:four"),
            TargetExpression.fromStringSafe("-//java/com/google:six"))
        .inOrder();
  }

  @Test
  public void testShardWithOnlyExcludedTargetsIsDropped() {
    List<TargetExpression> targets =
        ImmutableList.of(
            TargetExpression.fromStringSafe("//java/com/google:one"),
            TargetExpression.fromStringSafe("//java/com/google:two"),
            TargetExpression.fromStringSafe("//java/com/google:three"),
            TargetExpression.fromStringSafe("-//java/com/google:four"),
            TargetExpression.fromStringSafe("-//java/com/google:five"),
            TargetExpression.fromStringSafe("-//java/com/google:six"));

    ShardedTargetList shards = BlazeBuildTargetSharder.shardTargets(targets, 3);

    assertThat(shards.shardedTargets).hasSize(1);
    assertThat(shards.shardedTargets.get(0)).hasSize(6);
  }
}
