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
package com.google.idea.blaze.base.run.targetfinder;

import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.JdkFutureAdapters;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.diagnostic.Logger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Utilities operating on futures. */
public class FuturesUtil {

  private static final Logger logger = Logger.getInstance(FuturesUtil.class);

  /**
   * Blocks while calling get on the future. Use with care: logs a warning for {@link
   * ExecutionException}, and otherwise returns null on error or interrupt.
   */
  @Nullable
  public static <T> T getIgnoringErrors(Future<T> future) {
    try {
      return future.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (ExecutionException e) {
      logger.warn(e);
    }
    return null;
  }

  /**
   * Iterates through the futures, returning the first future satisfying the predicate.
   * Future returns null if there are no results matching the predicate.
   *
   * <p>Prioritizes immediately available results.
   */
  @NotNull
  public static <T> ListenableFuture<@Nullable T> getFirstFutureSatisfyingPredicate(
          Iterable<Future<T>> iterable, Predicate<@Nullable T> predicate) {
    return getFirstFutureSatisfyingPredicateImpl(iterable, predicate, null);
  }

  /**
   * Iterates through the futures, returning the first future satisfying the predicate.
   * Future returns fallback value if there are no results matching the predicate.
   *
   * <p>Prioritizes immediately available results.
   */
  @NotNull
  public static <T> ListenableFuture<@NotNull T> getFirstFutureSatisfyingPredicate(
          Iterable<Future<T>> iterable, Predicate<@Nullable T> predicate, @NotNull T fallbackValue) {
    return getFirstFutureSatisfyingPredicateImpl(iterable, predicate, fallbackValue);
  }

  @NotNull
  private static <T> ListenableFuture<T> getFirstFutureSatisfyingPredicateImpl(
      Iterable<Future<T>> iterable, Predicate<@Nullable T> predicate, @Nullable T fallback) {
    // it would be nice to have @Contract(_, _, !null -> <!null>) here,
    // but @Contract does not support types in generics
    List<ListenableFuture<T>> futures = new ArrayList<>();
    for (Future<T> future : iterable) {
      if (future.isDone()) {
        T result = getIgnoringErrors(future);
        if (predicate.test(result)) {
          return Futures.immediateFuture(result);
        }
      } else {
        // we can't return ListenableFuture directly, because implementations are using different
        // versions of that class...
        futures.add(JdkFutureAdapters.listenInPoolThread(future));
      }
    }
    if (futures.isEmpty()) {
      return Futures.immediateFuture(fallback);
    }
    return Futures.transform(
        Futures.allAsList(futures),
        list -> list.stream().filter(predicate).findFirst().orElse(fallback),
        directExecutor());
  }
}
