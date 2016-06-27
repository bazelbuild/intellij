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

import com.google.common.collect.Lists;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.BlazeScope;
import com.google.idea.blaze.base.scope.OutputSink;
import com.google.idea.blaze.base.scope.output.PrintOutput;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.List;

/**
 * Tracks trace events and writes trace to ~/blaze-trace.json at end of scope.
 *
 * The results can be imported into Chrome using chrome://tracing.
 */
public class TraceScope implements BlazeScope, OutputSink<TraceEvent> {
  private static final Logger LOG = Logger.getInstance(TraceScope.class);
  private final List<TraceEvent> traceEvents = Collections.synchronizedList(Lists.newArrayList());
  private long traceStartNanos;

  @Override
  public void onScopeBegin(@NotNull BlazeContext context) {
    traceStartNanos = System.nanoTime();
    context.addOutputSink(TraceEvent.class, this);
  }

  @Override
  public void onScopeEnd(@NotNull BlazeContext context) {
    Collections.sort(traceEvents, (a, b) -> Long.compare(a.nanoTime, b.nanoTime));

    String home = System.getProperty("user.home");
    File file = new File(home, "blaze-trace.json");
    try (PrintWriter printWriter = new PrintWriter(file)) {
      printWriter.println("[");

      for (int i = 0; i < traceEvents.size(); ++i) {
        TraceEvent traceEvent = traceEvents.get(i);
        long startTimeNanos = traceEvent.nanoTime - traceStartNanos;
        long startTimeMicros = startTimeNanos / 1000;
        printWriter.print(String.format(
          "{\"name\": \"%s\", \"ts\": %d, \"ph\": \"%s\", \"pid\": %d}",
          traceEvent.name,
          startTimeMicros,
          traceEvent.type == TraceEvent.Type.Begin ? "B" : "E",
          traceEvent.threadId
        ));

        // No trailing commas in JSON :(
        if (i != traceEvents.size() - 1) {
          printWriter.append(',');
        }
        printWriter.append('\n');
      }

      printWriter.println("]");
    }
    catch (FileNotFoundException e) {
      LOG.error(e);
    }

    context.output(new PrintOutput("Wrote trace output to: " + file));
  }

  @Override
  public Propagation onOutput(@NotNull TraceEvent output) {
    traceEvents.add(output);
    return Propagation.Continue;
  }
}
