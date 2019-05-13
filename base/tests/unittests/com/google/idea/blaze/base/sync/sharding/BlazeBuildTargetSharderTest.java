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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.sync.sharding.BlazeBuildTargetSharder.LexicographicTargetSharder;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test that targets are correctly partitioned in {@link BlazeBuildTargetSharder#shardTargets}. */
@RunWith(JUnit4.class)
public class BlazeBuildTargetSharderTest extends BlazeTestCase {

  @Override
  protected void initTest(Container applicationServices, Container projectServices) {
    applicationServices.register(ExperimentService.class, new MockExperimentService());
    registerExtensionPoint(BuildBatchingService.EP_NAME, BuildBatchingService.class)
        .registerExtension(new LexicographicTargetSharder());
  }

  private static TargetExpression target(String expression) {
    return Preconditions.checkNotNull(TargetExpression.fromStringSafe(expression));
  }

  @Test
  public void testShardSizeRespected() {
    List<TargetExpression> targets =
        ImmutableList.of(
            target("//java/com/google:one"),
            target("//java/com/google:two"),
            target("//java/com/google:three"),
            target("//java/com/google:four"),
            target("//java/com/google:five"));
    ShardedTargetList shards = BlazeBuildTargetSharder.shardTargets(project, targets, 2);
    assertThat(shards.shardedTargets).hasSize(3);
    assertThat(shards.shardedTargets.get(0)).hasSize(2);
    assertThat(shards.shardedTargets.get(1)).hasSize(2);
    assertThat(shards.shardedTargets.get(2)).hasSize(1);

    shards = BlazeBuildTargetSharder.shardTargets(project, targets, 4);
    assertThat(shards.shardedTargets).hasSize(2);
    assertThat(shards.shardedTargets.get(0)).hasSize(4);
    assertThat(shards.shardedTargets.get(1)).hasSize(1);

    shards = BlazeBuildTargetSharder.shardTargets(project, targets, 100);
    assertThat(shards.shardedTargets).hasSize(1);
    assertThat(shards.shardedTargets.get(0)).hasSize(5);
  }

  @Test
  public void testTargetsAreSorted() {
    List<TargetExpression> targets =
        ImmutableList.of(
            target("//java/com/d:target"),
            target("//java/com/b:target"),
            target("//java/com/e:target"),
            target("//java/com/a:target"),
            target("//java/com/c:target"),
            target("-//java/com/e:target"));
    ShardedTargetList shards = BlazeBuildTargetSharder.shardTargets(project, targets, 2);
    assertThat(shards.shardedTargets).hasSize(2);
    assertThat(shards.shardedTargets.get(0))
        .containsExactly(target("//java/com/a:target"), target("//java/com/b:target"))
        .inOrder();
    assertThat(shards.shardedTargets.get(1))
        .containsExactly(target("//java/com/c:target"), target("//java/com/d:target"))
        .inOrder();
  }

  @Test
  public void testExcludedTargetsAreRemoved() {
    List<TargetExpression> targets =
        ImmutableList.of(
            target("//java/com/google:one"),
            target("//java/com/google:two"),
            target("//java/com/google:three"),
            target("-//java/com/google:one"),
            target("-//java/com/google:three"),
            target("-//java/com/google:six"));
    ShardedTargetList shards = BlazeBuildTargetSharder.shardTargets(project, targets, 3);

    assertThat(shards.shardedTargets).hasSize(1);
    assertThat(shards.shardedTargets.get(0)).containsExactly(target("//java/com/google:two"));
  }

  @Test
  public void testExcludedThenIncludedTargetsAreRetained() {
    List<TargetExpression> targets =
        ImmutableList.of(
            target("//java/com/google:one"),
            target("-//java/com/google:one"),
            target("//java/com/google:one"),
            target("-//java/com/google:two"),
            target("//java/com/google:two"));
    ShardedTargetList shards = BlazeBuildTargetSharder.shardTargets(project, targets, 3);
    assertThat(shards.shardedTargets).hasSize(1);
    assertThat(shards.shardedTargets.get(0))
        .containsExactly(target("//java/com/google:one"), target("//java/com/google:two"));
  }
}
