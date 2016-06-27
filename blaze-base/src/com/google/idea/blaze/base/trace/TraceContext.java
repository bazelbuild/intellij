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

import com.google.idea.blaze.base.scope.BlazeContext;
import org.jetbrains.annotations.NotNull;

/**
 * Trace context utility class that can be used with try-with-resource.
 */
public class TraceContext implements AutoCloseable {
  @NotNull BlazeContext context;
  @NotNull String name;

  public TraceContext(@NotNull BlazeContext context, @NotNull String name) {
    this.context = context;
    this.name = name;
    context.output(new TraceEvent(name, TraceEvent.Type.Begin));
  }

  @Override
  public void close() {
    context.output(new TraceEvent(name, TraceEvent.Type.End));
  }
}
