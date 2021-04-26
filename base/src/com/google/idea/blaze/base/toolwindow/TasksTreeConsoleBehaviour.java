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

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.scope.output.PrintOutput;
import com.google.idea.blaze.base.scope.output.StatusOutput;
import com.google.idea.common.ui.templates.Behavior;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import javax.annotation.Nullable;

/** Behaviour class of the combination of the tree and console view. */
final class TasksTreeConsoleBehaviour implements Behavior<TasksTreeConsoleModel> {
  private TasksTreeConsoleModel model;

  @Override
  public void defineBehavior(TasksTreeConsoleModel model) {
    this.model = model;
  }

  void addTask(Task task, Project project, ImmutableList<Filter> filters) {
    TasksTreeModel treeModel = model.getTreeModel();
    treeModel.tasksTreeProperty().addTask(task);
    model.getConsolesOfTasks().computeIfAbsent(task, t -> ConsoleView.create(project, filters));
    treeModel.selectedTaskProperty().setValue(task); // select the new task
  }

  void finishTask(Task task) {
    if (task.getParent().isPresent()) {
      return;
    }
    model.getTopLevelFinishedTasks().offer(task);
    cleanUpTasksExceedingLimit();
  }

  void taskOutput(Task task, PrintOutput output) {
    getConsole(task).println(output);
  }

  void taskStatus(Task task, StatusOutput output) {
    getConsole(task).println(output);
  }

  public void navigate(Task task, HyperlinkInfo link, int offset) {
    getConsole(task).navigateToHyperlink(link, offset);
    model.getTreeModel().selectedTaskProperty().setValue(task);
  }

  void setStopHandler(Task task, @Nullable Runnable runnable) {
    getConsole(task).setStopHandler(runnable);
  }

  private ConsoleView getConsole(Task task) {
    ConsoleView console = model.getConsolesOfTasks().get(task);
    if (console == null) {
      throw new IllegalStateException("Task `" + task.getName() + "` is missing a console.");
    }
    return console;
  }

  private void cleanUpTasksExceedingLimit() {
    while (model.getTopLevelFinishedTasks().size() > TasksTreeConsoleModel.MAX_FINISHED_TASKS) {
      Task task = model.getTopLevelFinishedTasks().poll();
      model.getTreeModel().tasksTreeProperty().removeTask(task);
      ConsoleView consoleView = model.getConsolesOfTasks().remove(task);
      if (consoleView == null) {
        throw new IllegalStateException(
            "Finished task `" + task.getName() + "` doesn't have a corresponding console view");
      }
      Disposer.dispose(consoleView);
    }
  }
}
