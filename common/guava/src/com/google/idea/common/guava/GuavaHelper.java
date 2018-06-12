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
package com.google.idea.common.guava;


import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Adds a few methods that aren't available until Guava 21. #api172 */
public final class GuavaHelper {

  private static final Collector<Object, ?, ImmutableSet<Object>> TO_IMMUTABLE_SET =
      Collector.of(
          ImmutableSet::<Object>builder,
          ImmutableSet.Builder::add,
          GuavaHelper::combineSet,
          ImmutableSet.Builder::build);

  private static final Collector<Object, ?, ImmutableList<Object>> TO_IMMUTABLE_LIST =
      Collector.of(
          ImmutableList::<Object>builder,
          ImmutableList.Builder::add,
          GuavaHelper::combineList,
          ImmutableList.Builder::build);

  /** Replaces {@code ImmutableSet#toImmutableSet}, which isn't available until Guava 21. */
  @SuppressWarnings("unchecked") // so we can use a singleton
  public static <E> Collector<E, ?, ImmutableSet<E>> toImmutableSet() {
    return (Collector) TO_IMMUTABLE_SET;
  }

  /** Replaces {@code ImmutableList#toImmutableList}, which isn't available until Guava 21. */
  @SuppressWarnings("unchecked") // so we can use a singleton
  public static <E> Collector<E, ?, ImmutableList<E>> toImmutableList() {
    return (Collector) TO_IMMUTABLE_LIST;
  }

  /** Replaces {@code ImmutableMap#toImmutableMap}, which isn't available until Guava 21. */
  public static <T, K, V> Collector<T, ?, ImmutableMap<K, V>> toImmutableMap(
      Function<? super T, ? extends K> keyFunction,
      Function<? super T, ? extends V> valueFunction) {
    return Collector.of(
        ImmutableMap.Builder<K, V>::new,
        (builder, input) -> builder.put(keyFunction.apply(input), valueFunction.apply(input)),
        GuavaHelper::combineMap,
        ImmutableMap.Builder::build);
  }

  /** Replaces {@code ImmutableMap#toImmutableMap}, which isn't available until Guava 21. */
  public static <T, K, V> Collector<T, ?, ImmutableMap<K, V>> toImmutableMap(
      Function<? super T, ? extends K> keyFunction,
      Function<? super T, ? extends V> valueFunction,
      BinaryOperator<V> mergeFunction) {
    return Collectors.collectingAndThen(
        Collectors.toMap(keyFunction, valueFunction, mergeFunction, LinkedHashMap::new),
        ImmutableMap::copyOf);
  }

  private static <T> ImmutableSet.Builder<T> combineSet(
      ImmutableSet.Builder<T> one, ImmutableSet.Builder<T> two) {
    return one.addAll(two.build());
  }

  private static <T> ImmutableList.Builder<T> combineList(
      ImmutableList.Builder<T> one, ImmutableList.Builder<T> two) {
    return one.addAll(two.build());
  }

  private static <K, V> ImmutableMap.Builder<K, V> combineMap(
      ImmutableMap.Builder<K, V> one, ImmutableMap.Builder<K, V> two) {
    return one.putAll(two.build());
  }

  /** Replaces {@code Streams::stream} in Guava 21, or {@code Optional::stream} in Java 9. */
  public static <T> Stream<T> stream(Optional<T> optional) {
    return optional.isPresent() ? Stream.of(optional.get()) : Stream.of();
  }

  /** Replaces {@code ImmutableList#sortedCopyOf}, which isn't available until Guava 21. */
  public static <T> ImmutableList<T> sortedImmutableListOf(
      Comparator<? super T> comparator, Collection<? extends T> elements) {
    return elements.stream().sorted(comparator).collect(toImmutableList());
  }
}
