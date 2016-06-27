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
package com.google.idea.blaze.base.trace;

import com.google.idea.blaze.base.trickle.*;
import com.google.idea.blaze.base.scope.BlazeContext;
import org.jetbrains.annotations.NotNull;

/**
 * Helper methods for tracing.
 */
public class Trace {
  public static <R> Function0<R> trace(@NotNull BlazeContext context, @NotNull String name, @NotNull Function0<R> func) {
    return () -> {
      try (TraceContext traceContext = new TraceContext(context, name)) {
        return func.run();
      }
    };
  }

  public static <A, R> Function1<A, R> trace(@NotNull BlazeContext context, @NotNull String name, @NotNull Function1<A, R> func) {
    return (a) -> {
      try (TraceContext traceContext = new TraceContext(context, name)) {
        return func.run(a);
      }
    };
  }

  public static <A, B, R> Function2<A, B, R> trace(@NotNull BlazeContext context, @NotNull String name, @NotNull Function2<A, B, R> func) {
    return (a, b) -> {
      try (TraceContext traceContext = new TraceContext(context, name)) {
        return func.run(a, b);
      }
    };
  }

  public static <A, B, C, R> Function3<A, B, C, R> trace(@NotNull BlazeContext context, @NotNull String name, @NotNull Function3<A, B, C, R> func) {
    return (a, b, c) -> {
      try (TraceContext traceContext = new TraceContext(context, name)) {
        return func.run(a, b, c);
      }
    };
  }

  public static <A, B, C, D, R> Function4<A, B, C, D, R> trace(@NotNull BlazeContext context, @NotNull String name, @NotNull Function4<A, B, C, D, R> func) {
    return (a, b, c, d) -> {
      try (TraceContext traceContext = new TraceContext(context, name)) {
        return func.run(a, b, c, d);
      }
    };
  }

  public static <A, B, C, D, E, R> Function5<A, B, C, D, E, R> trace(@NotNull BlazeContext context, @NotNull String name, @NotNull Function5<A, B, C, D, E, R> func) {
    return (a, b, c, d, e) -> {
      try (TraceContext traceContext = new TraceContext(context, name)) {
        return func.run(a, b, c, d, e);
      }
    };
  }
}
