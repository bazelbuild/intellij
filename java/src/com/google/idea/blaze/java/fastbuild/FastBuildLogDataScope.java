/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.java.fastbuild;

import com.google.common.base.Stopwatch;
import com.google.idea.blaze.base.logging.EventLoggingService;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.BlazeScope;
import com.google.idea.blaze.base.scope.OutputSink.Propagation;
import com.google.idea.blaze.common.Output;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stores information about the fast build into a map so it can be logged by the BlazeContext
 * creator owner.
 */
public final class FastBuildLogDataScope implements BlazeScope {

  /** Log data about a fast build. */
  public static class FastBuildLogOutput implements Output {

    private final String key;
    private final String value;

    private FastBuildLogOutput(String key, String value) {
      this.key = key;
      this.value = value;
    }

    public static FastBuildLogOutput keyValue(String key, String value) {
      return new FastBuildLogOutput(key, value);
    }

    public static FastBuildLogOutput milliseconds(String key, Stopwatch timer) {
      return new FastBuildLogOutput(key, Long.toString(timer.elapsed().toMillis()));
    }
  }

  // Use a LinkedHashMap so that we preserve the order of the entries.
  private final Map<String, String> logData = new LinkedHashMap<>();
  private final Stopwatch timer = Stopwatch.createUnstarted();

  @Override
  public void onScopeBegin(BlazeContext context) {
    context.addOutputSink(
        FastBuildLogOutput.class,
        output -> {
          logData.put(output.key, output.value);
          return Propagation.Continue;
        });
    timer.start();
  }

  @Override
  public void onScopeEnd(BlazeContext context) {
    EventLoggingService.getInstance()
        .logEvent(FastBuildService.class, "fast_build", logData, timer.elapsed().toMillis());
  }
}
