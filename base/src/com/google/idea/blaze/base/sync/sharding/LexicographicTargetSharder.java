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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.idea.blaze.base.model.primitives.Label;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * A simple target batcher splitting based on the target strings. This will tend to split by
 * package, so is better than random batching.
 */
class LexicographicTargetSharder implements BuildBatchingService {
  @Override
  public ImmutableList<ImmutableList<Label>> calculateTargetBatches(
      Set<Label> targets, boolean remoteBuild, int suggestedShardSize) {
    List<Label> sorted = ImmutableList.sortedCopyOf(Comparator.comparing(Label::toString), targets);
    return Lists.partition(sorted, suggestedShardSize).stream()
        .map(ImmutableList::copyOf)
        .collect(toImmutableList());
  }
}
