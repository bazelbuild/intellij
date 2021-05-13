/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.toolwindow;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.scope.output.PrintOutput;
import com.google.idea.blaze.base.scope.output.StatusOutput;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.serviceContainer.NonInjectable;
import com.intellij.util.ui.UIUtil;
import java.time.Instant;

/** Service that controls the Blaze Outputs Tool Window. */
public final class TasksToolWindowService {

  /** Provider for the current value of "now" for users of {@code java.time}. */
  @FunctionalInterface
  @VisibleForTesting
  interface TimeSource {
    /** Returns the current {@link Instant} according to this time source. */
    Instant now();
  }

  private final TimeSource timeSource;
  private final ToolWindowTabs tabs;
  private final Project project;

  public TasksToolWindowService(Project project) {
    this(project, Instant::now);
  }

  @VisibleForTesting
  @NonInjectable
  TasksToolWindowService(Project project, TimeSource timeSource) {
    this.project = project;
    this.timeSource = timeSource;
    tabs = new ToolWindowTabs(project);
  }

  // The below methods might be better replaced by an event-based approach. When we touch this part
  // in the future, we should consider to refactor it.

  /** Mark the given task as started and notify the view to reflect the started task. */
  public void startTask(Task task, ImmutableList<Filter> consoleFilters) {
    task.setStartTime(timeSource.now());
    UIUtil.invokeLaterIfNeeded(() -> tabs.addTask(task, consoleFilters));
  }

  /** Update the state and view with new task output */
  public void output(Task task, PrintOutput output) {
    UIUtil.invokeLaterIfNeeded(() -> tabs.taskOutput(task, output));
  }

  /** Update the state and the view with new task status */
  public void status(Task task, StatusOutput output) {
    UIUtil.invokeLaterIfNeeded(() -> tabs.statusOutput(task, output));
  }

  /** Update the state and the view when task finishes */
  public void finishTask(Task task, boolean hasErrors) {
    task.setEndTime(timeSource.now());
    task.setHasErrors(hasErrors);
    UIUtil.invokeLaterIfNeeded(() -> tabs.finishTask(task));
  }

  /** Move task to a new parent task */
  public void moveTask(Task task, Task newParent) {
    task.setParent(newParent);
  }

  /** Make task a root, removing it from the current parent if any. */
  public void makeTaskRoot(Task task) {
    task.setParent(null);
  }

  /** Open given task's output hyperlink */
  public void navigate(Task task, HyperlinkInfo link, int offset) {
    UIUtil.invokeLaterIfNeeded(() -> tabs.navigate(task, link, offset));
  }

  /** Activate the view */
  public void activate() {
    ToolWindow toolWindow =
        ToolWindowManager.getInstance(project).getToolWindow(TasksToolWindowFactory.ID);
    if (toolWindow != null) {
      toolWindow.activate(/* runnable= */ null, /* autoFocusContents= */ false);
    }
  }

  /** Set the action to be executed when the given task is being manually stopped in the UI. */
  public void setStopHandler(Task task, Runnable runnable) {
    UIUtil.invokeLaterIfNeeded(() -> tabs.setStopHandler(task, runnable));
  }

  /** Remove option to stop the task manually in the UI. */
  public void removeStopHandler(Task task) {
    UIUtil.invokeLaterIfNeeded(() -> tabs.setStopHandler(task, null));
  }

  public static TasksToolWindowService getInstance(Project project) {
    return ServiceManager.getService(project, TasksToolWindowService.class);
  }
}
