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

import com.google.common.base.Stopwatch;
import com.google.idea.blaze.base.metrics.Action;
import com.google.idea.blaze.base.metrics.LoggingService;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.BlazeScope;
import com.intellij.openapi.project.Project;
import java.util.concurrent.TimeUnit;

/** Timing scope where the results are sent to a logging service */
public class LoggedTimingScope implements BlazeScope {
  // It is not guaranteed that the threading model will be sane during the entirety of this scope,
  // so we use wall clock time and not ThreadMXBean where we could get user/system time.

  Project project;
  private final Action action;
  private Stopwatch timer;

  /** @param action The action we will be reporting a time for to the logging service */
  public LoggedTimingScope(Project project, Action action) {
    this.project = project;
    this.action = action;
    this.timer = Stopwatch.createUnstarted();
  }

  @Override
  public void onScopeBegin(BlazeContext context) {
    timer.start();
  }

  @Override
  public void onScopeEnd(BlazeContext context) {
    if (!context.isCancelled()) {
      long totalMS = timer.elapsed(TimeUnit.MILLISECONDS);
      LoggingService.reportEvent(project, action, totalMS);
    }
  }
}
