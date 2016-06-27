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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Prints timing information as output.
 */
public class TimingScope implements BlazeScope {

  @NotNull
  private final String name;

  private long startTime;

  private double duration;

  @Nullable
  private TimingScope parentScope;

  @NotNull
  private List<TimingScope> children = Lists.newArrayList();

  public TimingScope(@NotNull String name) {
    this.name = name;
  }

  @Override
  public void onScopeBegin(@NotNull BlazeContext context) {
    startTime = System.currentTimeMillis();
    parentScope = context.getParentScope(this);

    if (parentScope != null) {
      parentScope.children.add(this);
    }
  }

  @Override
  public void onScopeEnd(@NotNull BlazeContext context) {
    if (context.isCancelled()) {
      return;
    }

    long elapsedTime = System.currentTimeMillis() - startTime;
    duration = (double)elapsedTime / 1000.0;

    if (parentScope == null) {
      outputReport(context);
    }
  }

  private void outputReport(@NotNull BlazeContext context) {
    context.output(new PrintOutput("\n==== TIMING REPORT ====\n"));
    outputReport(context, this, 0);
  }

  private static void outputReport(
    @NotNull BlazeContext context,
    @NotNull TimingScope timingScope,
    int depth) {
    String selfString = "";

    // Self time trivially 100% if no children
    if (timingScope.children.size() > 0) {
      // Calculate self time as <my duration> - <sum child duration>
      double selfTime = timingScope.duration;
      for (TimingScope child : timingScope.children) {
        selfTime -= child.duration;
      }

      selfString = selfTime > 0.1
                   ? String.format(" (%s)", durationStr(selfTime))
                   : "";
    }

    context.output(new PrintOutput(
      String.format("%s%s: %s%s",
                    getIndentation(depth),
                    timingScope.name,
                    durationStr(timingScope.duration),
                    selfString)
    ));

    for (TimingScope child : timingScope.children) {
      outputReport(context, child, depth + 1);
    }
  }

  private static String durationStr(double time) {
    return time >= 1.0
           ? String.format("%.1fs", time)
           : String.format("%dms", (int)(time * 1000));
  }

  private static String getIndentation(int depth) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < depth; ++i) {
      sb.append("    ");
    }
    return sb.toString();
  }
}
