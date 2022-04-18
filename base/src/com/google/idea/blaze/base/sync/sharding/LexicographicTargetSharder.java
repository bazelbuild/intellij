/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.idea.blaze.base.bazel.BuildSystem.SyncStrategy;
import com.google.idea.blaze.base.logging.utils.ShardStats.ShardingApproach;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.common.experiments.IntExperiment;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * A simple target batcher splitting based on the target strings. This will tend to split by
 * package, so is better than random batching.
 */
public class LexicographicTargetSharder implements BuildBatchingService {
  // The maximum number of targets per shard for remote builds to avoid potential OOM
  @VisibleForTesting
  static final IntExperiment maximumRemoteShardSize =
      new IntExperiment("lexicographic.sharder.maximum.remote.shard.size", 1000);

  // The minimum number of targets per shard for remote builds. Ignored if the user explicitly
  // sets a smaller target_shard_size
  @VisibleForTesting
  static final IntExperiment minimumRemoteShardSize =
      new IntExperiment("lexicographic.sharder.minimum.remote.shard.size", 20);

  // The minimum targets size requirement to use all idle workers. Splitting targets does not help
  // to reduce build time when their target size is too small. So set a threshold to avoid
  // over-split.
  @VisibleForTesting
  static final IntExperiment parallelThreshold =
      new IntExperiment("lexicographic.sharder.parallel.threshold", 1000);

  @Override
  public ImmutableList<ImmutableList<Label>> calculateTargetBatches(
      Set<Label> targets, SyncStrategy syncStrategy, int suggestedShardSize) {
    List<Label> sorted = ImmutableList.sortedCopyOf(Comparator.comparing(Label::toString), targets);
    return Lists.partition(sorted, suggestedShardSize).stream()
        .map(ImmutableList::copyOf)
        .collect(toImmutableList());
  }

  @Override
  public ShardingApproach getShardingApproach() {
    return ShardingApproach.LEXICOGRAPHIC_TARGET_SHARDER;
  }
}
