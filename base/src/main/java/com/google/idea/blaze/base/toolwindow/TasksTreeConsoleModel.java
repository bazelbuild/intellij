/*
 * Copyright 2021 The Bazel Authors. All rights reserved.
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

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

/** Model for the combination of the tree and output consoles. */
final class TasksTreeConsoleModel {
  static final int MAX_FINISHED_TASKS = 10;

  private final TasksTreeModel treeModel = new TasksTreeModel();
  private final Map<Task, ConsoleView> consolesOfTasks = new HashMap<>();

  private final Queue<Task> topLevelFinishedTasks = new ArrayDeque<>(MAX_FINISHED_TASKS + 1);

  private TasksTreeConsoleModel() {}

  static TasksTreeConsoleModel create(TasksTreeConsoleBehaviour behaviour) {
    TasksTreeConsoleModel model = new TasksTreeConsoleModel();
    behaviour.defineBehavior(model);
    return model;
  }

  TasksTreeModel getTreeModel() {
    return treeModel;
  }

  Map<Task, ConsoleView> getConsolesOfTasks() {
    return consolesOfTasks;
  }

  public Queue<Task> getTopLevelFinishedTasks() {
    return topLevelFinishedTasks;
  }
}
