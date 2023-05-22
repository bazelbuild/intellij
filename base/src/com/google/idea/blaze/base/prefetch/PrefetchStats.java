/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.prefetch;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.Multiset;
import java.util.stream.Stream;

/** Class encapsulating stats about a prefetch operation. */
@AutoValue
public abstract class PrefetchStats {

  public static final PrefetchStats NONE = doCreate(0L, ImmutableMultiset.of());

  public static PrefetchStats create(long bytesPrefetched, Multiset<String> countByExt) {
    return doCreate(bytesPrefetched, ImmutableMultiset.copyOf(countByExt));
  }

  public static PrefetchStats doCreate(long bytesPrefetched, ImmutableMultiset<String> countByExt) {
    return new AutoValue_PrefetchStats(bytesPrefetched, countByExt);
  }

  public PrefetchStats combine(PrefetchStats that) {
    return doCreate(
        this.bytesPrefetched() + that.bytesPrefetched(),
        Stream.concat(this.countByExtension().stream(), that.countByExtension().stream())
            .collect(ImmutableMultiset.toImmutableMultiset()));
  }

  /** Returns the number of bytes downloaded over the network. */
  public abstract long bytesPrefetched();

  public abstract ImmutableMultiset<String> countByExtension();
}
