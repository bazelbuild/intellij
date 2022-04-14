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
import com.google.idea.blaze.base.scope.output.StateUpdate;
import com.google.idea.blaze.base.scope.output.StatusOutput;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.serviceContainer.NonInjectable;
import java.time.Instant;
import org.jetbrains.annotations.Nullable;

/** Service that controls the Blaze Outputs Tool Window. */
public final class TasksToolWindowService implements Disposable {

  /** Provider for the current value of "now" for users of {@code java.time}. */
  @FunctionalInterface
  @VisibleForTesting
  interface TimeSource {
    /** Returns the current {@link Instant} according to this time source. */
    Instant now();
  }

  @VisibleForTesting
  public void setTimeSource(@Nullable TimeSource timeSource) {
    if (timeSource == null) {
      this.timeSource = Instant::now;
    } else {
      this.timeSource = timeSource;
    }
  }

  private TimeSource timeSource;
  private final Project project;

  public TasksToolWindowService(Project project) {
    this(project, Instant::now);
  }

  @VisibleForTesting
  @NonInjectable
  TasksToolWindowService(Project project, TimeSource timeSource) {
    this.project = project;
    this.timeSource = timeSource;
  }

  /** Mark the given task as started and notify the view to reflect the started task. */
  public void startTask(Task task, ImmutableList<Filter> consoleFilters) {
    task.setStartTime(timeSource.now());
    getPublisher().addTask(task, consoleFilters, this);
  }

  /** Append new output to a task view. */
  public void output(Task task, PrintOutput output) {
    getPublisher().output(task, output);
  }

  /** Append new status to a task view. */
  public void status(Task task, StatusOutput output) {
    getPublisher().status(task, output);
  }

  /** Update the state in a task view. */
  public void state(Task task, StateUpdate output) {
    getPublisher().state(task, output);
  }

  /** Update the state and the view when task finishes */
  public void finishTask(Task task, boolean hasErrors, boolean isCancelled) {
    task.setEndTime(timeSource.now());
    task.setCancelled(isCancelled);
    task.setHasErrors(hasErrors);
    getPublisher().finishTask(task);
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
    getPublisher().navigate(task, link, offset);
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
    getPublisher().setStopHandler(task, runnable);
  }

  /** Remove option to stop the task manually in the UI. */
  public void removeStopHandler(Task task) {
    getPublisher().setStopHandler(task, null);
  }

  @Override
  public void dispose() {}

  public static TasksToolWindowService getInstance(Project project) {
    return ServiceManager.getService(project, TasksToolWindowService.class);
  }

  private TasksToolWindowChangeNotifier getPublisher() {
    return project
        .getMessageBus()
        .syncPublisher(TasksToolWindowChangeNotifier.TASKS_TOOL_WINDOW_CHANGE_TOPIC);
  }
}
