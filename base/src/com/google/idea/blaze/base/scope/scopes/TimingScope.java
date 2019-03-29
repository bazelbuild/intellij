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
package com.google.idea.blaze.base.scope.scopes;

import com.google.common.collect.Lists;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.BlazeScope;
import com.google.idea.blaze.base.scope.output.PrintOutput;
import com.google.idea.blaze.base.scope.scopes.TimingScopeListener.TimedEvent;
import com.intellij.openapi.diagnostic.Logger;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;

/** Prints timing information as output. */
public class TimingScope implements BlazeScope {

  private static final Logger logger = Logger.getInstance(TimingScope.class);

  /** The type of event for which timing information is being recorded */
  public enum EventType {
    BlazeInvocation,
    Prefetching,
    Other,
  }

  private final String name;
  private final EventType eventType;

  private long startTime;

  private Optional<Double> duration = Optional.empty();

  private final List<TimingScopeListener> scopeListeners = Lists.newArrayList();

  @Nullable private TimingScope parentScope;

  private final List<TimingScope> children = Lists.newArrayList();

  public TimingScope(String name, EventType eventType) {
    this.name = name;
    this.eventType = eventType;
  }

  @Override
  public void onScopeBegin(BlazeContext context) {
    startTime = System.currentTimeMillis();
    parentScope = context.getParentScope(this);

    if (parentScope != null) {
      parentScope.children.add(this);
      scopeListeners.addAll(parentScope.scopeListeners);
    }
  }

  @Override
  public void onScopeEnd(BlazeContext context) {
    if (context.isCancelled()) {
      duration = Optional.of(0.0);
      return;
    }

    long elapsedTime = System.currentTimeMillis() - startTime;
    duration = Optional.of((double) elapsedTime / 1000.0);

    TimedEvent event = new TimedEvent(name, eventType, elapsedTime, children.isEmpty());
    scopeListeners.forEach(listener -> listener.onScopeEnd(event));

    if (parentScope == null) {
      outputReport(context);
    }
  }

  /** Adds a TimingScope listener to its list of listeners, and those of its children. */
  public void addScopeListener(TimingScopeListener listener) {
    scopeListeners.add(listener);
  }

  private void outputReport(BlazeContext context) {
    context.output(PrintOutput.log("\n==== TIMING REPORT ====\n"));
    outputReport(context, this, new TimingReportData(), 0);
  }

  private static void outputReport(
      BlazeContext context, TimingScope timingScope, TimingReportData data, int depth) {
    String selfString = "";

    // Self time trivially 100% if no children
    if (timingScope.children.size() > 0) {
      // Calculate self time as <my duration> - <sum child duration>
      double selfTime = timingScope.getDuration();
      for (TimingScope child : timingScope.children) {
        selfTime -= child.getDuration();
      }

      selfString = selfTime > 0.1 ? String.format(" (%s)", durationStr(selfTime)) : "";
    }

    context.output(
        PrintOutput.log(
            String.format(
                "%s%s: %s%s",
                getIndentation(depth),
                timingScope.name,
                durationStr(timingScope.getDuration()),
                selfString)));

    for (TimingScope child : timingScope.children) {
      outputReport(context, child, data, depth + 1);
    }

    if (timingScope.children.isEmpty()) {
      // sum times for leaf nodes
      data.addEventTiming(timingScope.eventType, timingScope.getDuration());
    }
    if (depth == 0) {
      data.outputReport(context);
    }
  }

  private double getDuration() {
    if (duration.isPresent()) {
      return duration.get();
    }
    // Could happen if a TimingScope outlives the root context (e.g., from BlazeSyncTask), so the
    // actual duration is not yet known.
    logger.warn(String.format("Duration not computed for TimingScope %s", name));
    return 0;
  }

  private static String durationStr(double time) {
    return time >= 1.0 ? String.format("%.1fs", time) : String.format("%dms", (int) (time * 1000));
  }

  private static String getIndentation(int depth) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < depth; ++i) {
      sb.append("    ");
    }
    return sb.toString();
  }

  private static class TimingReportData {
    final Map<EventType, Double> timingPerEvent = new LinkedHashMap<>();

    {
      Arrays.stream(EventType.values()).forEach(t -> timingPerEvent.put(t, 0d));
    }

    void addEventTiming(EventType type, double duration) {
      timingPerEvent.put(type, duration + timingPerEvent.get(type));
    }

    void outputReport(BlazeContext context) {
      context.output(PrintOutput.log("\nTiming summary:\n"));
      for (EventType type : timingPerEvent.keySet()) {
        double duration = timingPerEvent.get(type);
        if (duration > 0) {
          context.output(PrintOutput.log(String.format("%s: %s", type, durationStr(duration))));
        }
      }
    }
  }
}
