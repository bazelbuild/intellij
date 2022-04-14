/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.scope.output.PrintOutput;
import com.google.idea.blaze.base.scope.output.StateUpdate;
import com.google.idea.blaze.base.scope.output.StatusOutput;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.openapi.Disposable;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.Nullable;

/** Interface for notifications related to the Blaze Tool Window */
public interface TasksToolWindowChangeNotifier {
  Topic<TasksToolWindowChangeNotifier> TASKS_TOOL_WINDOW_CHANGE_TOPIC =
      Topic.create("Blaze Tasks", TasksToolWindowChangeNotifier.class);

  void addTask(Task task, ImmutableList<Filter> filters, Disposable parentDisposable);

  void output(Task task, PrintOutput output);

  void status(Task task, StatusOutput statusOutput);

  void state(Task task, StateUpdate output);

  void finishTask(Task task);

  void navigate(Task task, HyperlinkInfo link, int offset);

  void setStopHandler(Task task, @Nullable Runnable runnable);
}
