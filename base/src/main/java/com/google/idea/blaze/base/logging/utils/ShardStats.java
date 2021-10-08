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
package com.google.idea.blaze.base.logging.utils;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;

/** Stats of sharded targets. */
@AutoValue
public abstract class ShardStats {

  public abstract int suggestedTargetSizePerShard();

  public abstract ImmutableList<Integer> actualTargetSizePerShard();

  public abstract ShardingApproach shardingApproach();

  public static ShardStats.Builder builder() {
    return new AutoValue_ShardStats.Builder()
        .setActualTargetSizePerShard(ImmutableList.of())
        .setSuggestedTargetSizePerShard(0)
        .setShardingApproach(ShardingApproach.NONE);
  }

  /** Auto value builder for SyncStats. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract ShardStats.Builder setSuggestedTargetSizePerShard(
        int suggestedTargetSizePerShard);

    public abstract ShardStats.Builder setActualTargetSizePerShard(
        ImmutableList<Integer> actualTargetSizePerShard);

    public abstract ShardStats.Builder setShardingApproach(ShardingApproach shardingApproach);

    public abstract ShardStats build();
  }

  /** Types of sharding method */
  public enum ShardingApproach {
    NONE,
    PARTITION_WITHOUT_EXPANDING,
    BUILD_TARGET_BATCHING_SERVICE,
    LEXICOGRAPHIC_TARGET_SHARDER,
    ERROR
  }
}
