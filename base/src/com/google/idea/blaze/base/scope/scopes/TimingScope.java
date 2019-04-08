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

import static java.util.stream.Collectors.joining;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.BlazeScope;
import com.google.idea.blaze.base.scope.output.PrintOutput;
import com.google.idea.blaze.base.scope.scopes.TimingScopeListener.TimedEvent;
import com.intellij.openapi.diagnostic.Logger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;

/** Collects and logs timing information. */
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

  private Optional<Long> durationMillis = Optional.empty();

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
    }
  }

  @Override
  public void onScopeEnd(BlazeContext context) {
    if (context.isCancelled()) {
      durationMillis = Optional.of(0L);
      return;
    }

    long elapsedTime = System.currentTimeMillis() - startTime;
    durationMillis = Optional.of(elapsedTime);

    if (parentScope == null) {
      collectAndLogTimingData(context);
    }
  }

  private TimedEvent getTimedEvent() {
    return new TimedEvent(name, eventType, durationMillis.orElse(0L), children.isEmpty());
  }

  /** Adds a TimingScope listener to its list of listeners. */
  public void addScopeListener(TimingScopeListener listener) {
    scopeListeners.add(listener);
  }

  private void collectAndLogTimingData(BlazeContext context) {
    logger.info("==== TIMING REPORT ====");
    TimingReportData data = new TimingReportData();
    collectAndLogTimingData(context, this, data, 0);
    data.outputSummary(context);
    scopeListeners.forEach(l -> l.onScopeEnd(ImmutableList.copyOf(data.timedEvents)));
  }

  private static void collectAndLogTimingData(
      BlazeContext context, TimingScope timingScope, TimingReportData data, int depth) {
    String selfString = "";

    // Self time trivially 100% if no children
    if (timingScope.children.size() > 0) {
      // Calculate self time as <my duration> - <sum child duration>
      long selfTime = timingScope.getDurationMillis();
      for (TimingScope child : timingScope.children) {
        selfTime -= child.getDurationMillis();
      }
      if (selfTime > 100) {
        selfString = String.format(" (%s)", durationStr(selfTime));
      }
    }

    // TODO(brendandouglas): combine repeated child events with the same name (e.g. sharded builds)
    logger.info(
        String.format(
            "%s%s: %s%s",
            getIndentation(depth),
            timingScope.name,
            durationStr(timingScope.getDurationMillis()),
            selfString));

    TimedEvent event = timingScope.getTimedEvent();
    data.addTimedEvent(event);

    for (TimingScope child : timingScope.children) {
      collectAndLogTimingData(context, child, data, depth + 1);
    }
  }

  private long getDurationMillis() {
    if (durationMillis.isPresent()) {
      return durationMillis.get();
    }
    // Could happen if a TimingScope outlives the root context (e.g., from BlazeSyncTask), so the
    // actual duration is not yet known.
    logger.warn(String.format("Duration not computed for TimingScope %s", name));
    return 0;
  }

  private static String durationStr(long timeMillis) {
    return timeMillis >= 1000
        ? String.format("%.1fs", timeMillis / 1000d)
        : String.format("%sms", timeMillis);
  }

  private static String getIndentation(int depth) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < depth; ++i) {
      sb.append("    ");
    }
    return sb.toString();
  }

  private static class TimingReportData {
    final List<TimedEvent> timedEvents = new ArrayList<>();

    void addTimedEvent(TimedEvent event) {
      timedEvents.add(event);
    }

    void outputSummary(BlazeContext context) {
      Map<EventType, Long> totalTimes = new LinkedHashMap<>();
      for (EventType type : EventType.values()) {
        long totalTime =
            timedEvents.stream()
                .filter(e -> e.isLeafEvent && e.type == type)
                .mapToLong(e -> e.durationMillis)
                .sum();
        totalTimes.put(type, totalTime);
      }
      if (totalTimes.values().stream().mapToLong(l -> l).sum() < 1000) {
        return;
      }

      String summary =
          totalTimes.entrySet().stream()
              .map(e -> String.format("%s: %s", e.getKey(), durationStr(e.getValue())))
              .collect(joining(", "));

      context.output(PrintOutput.log("\nTiming summary:\n" + summary));
    }
  }
}
