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
import static com.google.idea.blaze.base.sync.sharding.ShardedTargetList.remoteConcurrentSyncs;
import static java.lang.Math.min;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.idea.blaze.base.logging.utils.ShardStats.ShardingApproach;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.settings.BuildBinaryType;
import com.google.idea.common.experiments.IntExperiment;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * A simple target batcher splitting based on the target strings. This will tend to split by
 * package, so is better than random batching.
 */
public class LexicographicTargetSharder implements BuildBatchingService {
  // The maximum amount of target per shard for remote build to avoid potential OOM
  @VisibleForTesting
  static final IntExperiment maximumRemoteShardSize =
      new IntExperiment("lexicographic.sharder.maximum.remote.shard.size", 1000);
  // The minimum targets size requirement to use all idle workers. Splitting targets does not help
  // to reduce build time when their target size is too small. So set a threshold to avoid
  // over-split.
  @VisibleForTesting
  static final IntExperiment parallelThreshold =
      new IntExperiment("lexicographic.sharder.parallel.threshold", 1000);

  @Override
  public ImmutableList<ImmutableList<Label>> calculateTargetBatches(
      Set<Label> targets, BuildBinaryType buildType, int suggestedShardSize) {
    List<Label> sorted = ImmutableList.sortedCopyOf(Comparator.comparing(Label::toString), targets);
    // When LexicographicTargetSharder is used for remote build, we may decide optimized shard size
    // for users even they have provided shard_size in project view. The size is decided according
    // to three aspects:
    // 1. take advantage of parallelization
    // 2. size specified by users
    // 3. avoid potential OOM (maximumRemoteShardSize)
    // We collect suggested size from these aspects and use the minimum one finally.
    // If user enable shard sync manually without remote build, LexicographicTargetSharder
    // will still be used. But use suggestedShardSize without further calculation since there's
    // only one worker in that case.
    if (buildType.isRemote && targets.size() >= parallelThreshold.getValue()) {
      // try to use all idle workers
      suggestedShardSize =
          min(
              (int) Math.ceil((double) targets.size() / remoteConcurrentSyncs.getValue()),
              suggestedShardSize);
      suggestedShardSize = min(maximumRemoteShardSize.getValue(), suggestedShardSize);
    }
    return Lists.partition(sorted, suggestedShardSize).stream()
        .map(ImmutableList::copyOf)
        .collect(toImmutableList());
  }

  @Override
  public ShardingApproach getShardingApproach() {
    return ShardingApproach.LEXICOGRAPHIC_TARGET_SHARDER;
  }
}
