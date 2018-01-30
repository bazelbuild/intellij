/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.sync.importer.aggregators;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.ideinfo.TargetMapBuilder;
import com.google.idea.blaze.base.model.primitives.Label;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link TransitiveAggregator}. */
@RunWith(JUnit4.class)
public class TransitiveAggregatorTest extends BlazeTestCase {
  private static int createCount;
  private static int reduceCount;

  @Test
  public void testAggregate() {
    createCount = 0;
    reduceCount = 0;
    TargetKeyAggregator aggregator =
        new TargetKeyAggregator(
            TargetMapBuilder.builder()
                .addTarget(
                    TargetIdeInfo.builder()
                        .setLabel("//:foo")
                        .addDependency("//:bar")
                        .addDependency("//:baz")
                        .addDependency("//:qux")
                        .build())
                .addTarget(
                    TargetIdeInfo.builder().setLabel("//:bar").addDependency("//:baz").build())
                .addTarget(
                    TargetIdeInfo.builder().setLabel("//:baz").addDependency("//:qux").build())
                .addTarget(TargetIdeInfo.builder().setLabel("//:qux").build())
                .addTarget(
                    TargetIdeInfo.builder()
                        .setLabel("//:unrelated")
                        .addDependency("//:qux")
                        .build())
                .build());
    assertThat(aggregator.get("//:foo")).containsExactly("//:foo", "//:bar", "//:baz", "//:qux");
    assertThat(aggregator.get("//:bar")).containsExactly("//:bar", "//:baz", "//:qux");
    assertThat(aggregator.get("//:baz")).containsExactly("//:baz", "//:qux");
    assertThat(aggregator.get("//:qux")).containsExactly("//:qux");
    assertThat(aggregator.get("//:unrelated")).containsExactly("//:unrelated", "//:qux");
    // One create per target.
    assertThat(createCount).isEqualTo(5);
    // One reduce per direct dependency: 3 + 1 + 1 + 0 + 1
    assertThat(reduceCount).isEqualTo(6);
  }

  @Test
  public void testAggregate100() {
    TargetMapBuilder targetMapBuilder = TargetMapBuilder.builder();
    // Put the targets with more dependencies first so we don't cheat with a partially filled map.
    for (int i = 99; i >= 0; --i) {
      TargetIdeInfo.Builder targetIdeInfoBuilder = TargetIdeInfo.builder().setLabel("//:t" + i);
      for (int j = i - 1; j >= 0; --j) {
        targetIdeInfoBuilder.addDependency("//:t" + j);
      }
      targetMapBuilder.addTarget(targetIdeInfoBuilder);
    }
    createCount = 0;
    reduceCount = 0;
    TargetKeyAggregator aggregator = new TargetKeyAggregator(targetMapBuilder.build());
    IntStream.range(0, 100)
        .forEach(
            i ->
                assertThat(aggregator.get("//:t" + i))
                    .containsExactlyElementsIn(
                        IntStream.rangeClosed(0, i)
                            .boxed()
                            .map(j -> "//:t" + j)
                            .collect(Collectors.toList())));
    // One create per target.
    assertThat(createCount).isEqualTo(100);
    // One reduce per direct dependency: 0 + 1 + 2 + ... + 97 + 98 + 99 = 100*99/2
    assertThat(reduceCount).isEqualTo(4950);
  }

  @Test
  public void testAggregateCyclicDependencyTerminates() {
    new TargetKeyAggregator(
        TargetMapBuilder.builder()
            .addTarget(TargetIdeInfo.builder().setLabel("//:foo").addDependency("//:bar").build())
            .addTarget(TargetIdeInfo.builder().setLabel("//:bar").addDependency("//:foo").build())
            .build());
  }

  @Test
  public void testAggregateMissingDependency() {
    createCount = 0;
    reduceCount = 0;
    TargetKeyAggregator aggregator =
        new TargetKeyAggregator(
            TargetMapBuilder.builder()
                .addTarget(
                    TargetIdeInfo.builder().setLabel("//:foo").addDependency("//:bar").build())
                .build());
    assertThat(aggregator.get("//:foo")).containsExactly("//:foo");
    assertThat(aggregator.get("//:bar")).isNull();
    // One create per target.
    assertThat(createCount).isEqualTo(1);
    // One reduce per direct dependency: 0
    assertThat(reduceCount).isEqualTo(0);
  }

  private static class TargetKeyAggregator extends TransitiveAggregator<Set<String>> {
    TargetKeyAggregator(TargetMap targetMap) {
      super(targetMap);
    }

    public Set<String> get(String targetKey) {
      return getOrDefault(TargetKey.forPlainTarget(Label.create(targetKey)), null);
    }

    @Override
    protected Iterable<TargetKey> getDependencies(TargetIdeInfo target) {
      return target.dependencies.stream().map(d -> d.targetKey).collect(Collectors.toList());
    }

    @Override
    protected Set<String> createForTarget(TargetIdeInfo target) {
      ++createCount;
      return ImmutableSet.of(target.key.label.toString());
    }

    @Override
    protected Set<String> reduce(Set<String> value, Set<String> dependencyValue) {
      ++reduceCount;
      return ImmutableSet.copyOf(Iterables.concat(value, dependencyValue));
    }
  }
}
