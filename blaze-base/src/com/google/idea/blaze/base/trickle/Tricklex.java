/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.trickle;

import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.spotify.trickle.*;
import org.jetbrains.annotations.NotNull;

/**
 * Ties Trickle to an executor to cut down on boilerplate.
 */
public class Tricklex {
  public static <R> ConfigurableGraph<R> call(@NotNull Function0<R> func) {
    return Trickle.call(func0(func));
  }

  public static <A, R> Trickle.NeedsParameters1<A, R> call(@NotNull Function1<A, R> func) {
    return Trickle.call(func1(func));
  }

  public static <A, B, R> Trickle.NeedsParameters2<A, B, R> call(@NotNull Function2<A, B, R> func) {
    return Trickle.call(func2(func));
  }

  public static <A, B, C, R> Trickle.NeedsParameters3<A, B, C, R> call(@NotNull Function3<A, B, C, R> func) {
    return Trickle.call(func3(func));
  }

  public static <A, B, C, D, R> Trickle.NeedsParameters4<A, B, C, D, R> call(@NotNull Function4<A, B, C, D, R> func) {
    return Trickle.call(func4(func));
  }

  public static <A, B, C, D, E, R> Trickle.NeedsParameters5<A, B, C, D, E, R> call(@NotNull Function5<A, B, C, D, E, R> func) {
    return Trickle.call(func5(func));
  }

  private static <R> Func0<R> func0(@NotNull final Function0<R> func) {
    return () -> BlazeExecutor.getInstance().submit(func::run);
  }

  private static <A, R> Func1<A, R> func1(@NotNull final Function1<A, R> func) {
    return a -> BlazeExecutor.getInstance().submit(() -> func.run(a));
  }

  private static <A, B, R> Func2<A, B, R> func2(@NotNull final Function2<A, B, R> func) {
    return (a, b) -> BlazeExecutor.getInstance().submit(() -> func.run(a, b));
  }

  private static <A, B, C, R> Func3<A, B, C, R> func3(@NotNull final Function3<A, B, C, R> func) {
    return (a, b, c) -> BlazeExecutor.getInstance().submit(() -> func.run(a, b, c));
  }

  private static <A, B, C, D, R> Func4<A, B, C, D, R> func4(@NotNull final Function4<A, B, C, D, R> func) {
    return (a, b, c, d) -> BlazeExecutor.getInstance().submit(() -> func.run(a, b, c, d));
  }

  private static <A, B, C, D, E, R> Func5<A, B, C, D, E, R> func5(@NotNull final Function5<A, B, C, D, E, R> func) {
    return (a, b, c, d, e) -> BlazeExecutor.getInstance().submit(() -> func.run(a, b, c, d, e));
  }
}
