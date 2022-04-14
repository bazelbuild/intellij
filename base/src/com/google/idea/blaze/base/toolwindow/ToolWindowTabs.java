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
import com.google.idea.blaze.base.scope.output.StateUpdate;
import com.google.idea.blaze.base.scope.output.StatusOutput;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import java.util.EnumMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Tabs controller for the Blaze tool window. Listens for related notifications and sends updates to
 * the UI.
 *
 * <p>All the tasks that are added to the tool window are grouped into tabs by type. Each tab
 * contains a tree/console combination.
 */
final class ToolWindowTabs implements Disposable {
  private final Project project;
  private final Map<Task.Type, Tab> tabs = new EnumMap<>(Task.Type.class);
  private ContentManager contentManager;

  public static ToolWindowTabs create(Project project) {
    ToolWindowTabs toolWindowTabs = new ToolWindowTabs(project);
    project
        .getMessageBus()
        .connect()
        .subscribe(
            TasksToolWindowChangeNotifier.TASKS_TOOL_WINDOW_CHANGE_TOPIC,
            toolWindowTabs.createNotificationListener());
    return toolWindowTabs;
  }

  private ToolWindowTabs(Project project) {
    this.project = project;
  }

  private Tab getTab(Task task) {
    Tab tab = tabs.get(task.getType());
    if (tab == null) {
      throw new IllegalStateException(
          "Task `" + task.getName() + "` with type `" + task.getType() + "` doesn't have tab.");
    }
    return tab;
  }

  private Tab createNewTab(Task.Type type) {
    TasksTreeConsoleBehaviour behaviour = new TasksTreeConsoleBehaviour();
    TasksTreeConsoleModel model = TasksTreeConsoleModel.create(project, behaviour);
    Content content = createToolWindowContent(model, type);
    ApplicationManager.getApplication().invokeLater(() -> getContentManager().addContent(content));
    return new Tab(behaviour, content);
  }

  private Content createToolWindowContent(TasksTreeConsoleModel model, Task.Type type) {
    Content content =
        ContentFactory.SERVICE
            .getInstance()
            .createContent(
                new TasksTreeConsoleView(model, this).getComponent(), type.getDisplayName(), false);
    content.setCloseable(false);
    content.setDisposer(this);
    return content;
  }

  private ContentManager getContentManager() {
    if (contentManager != null) {
      return contentManager;
    }
    ToolWindow toolWindow =
        ToolWindowManager.getInstance(project).getToolWindow(TasksToolWindowFactory.ID);
    if (toolWindow == null) {
      throw new IllegalStateException("Toolwindow " + TasksToolWindowFactory.ID + " doesn't exist");
    }
    contentManager = toolWindow.getContentManager();
    return contentManager;
  }

  @Override
  public void dispose() {}

  private static class Tab {
    final TasksTreeConsoleBehaviour behaviour;
    final Content content;

    Tab(TasksTreeConsoleBehaviour behaviour, Content content) {
      this.behaviour = behaviour;
      this.content = content;
    }
  }

  private class NotificationListener implements TasksToolWindowChangeNotifier {
    @Override
    public void addTask(Task task, ImmutableList<Filter> filters, Disposable parentDisposable) {
      Tab tab = tabs.computeIfAbsent(task.getType(), ToolWindowTabs.this::createNewTab);
      tab.behaviour.addTask(task, project, filters, parentDisposable);

      // Only auto-select top level tasks
      if (task.getParent().isEmpty()) {
        ApplicationManager.getApplication()
            .invokeLater(() -> getContentManager().setSelectedContent(tab.content));
      }
    }

    @Override
    public void output(Task task, PrintOutput output) {
      getTab(task).behaviour.taskOutput(task, output);
    }

    @Override
    public void status(Task task, StatusOutput statusOutput) {
      getTab(task).behaviour.taskStatus(task, statusOutput);
    }

    @Override
    public void state(Task task, StateUpdate output) {
      getTab(task).behaviour.taskState(task, output);
    }

    @Override
    public void finishTask(Task task) {
      getTab(task).behaviour.finishTask(task);
    }

    @Override
    public void navigate(Task task, HyperlinkInfo link, int offset) {
      Tab tab = getTab(task);
      tab.behaviour.navigate(task, link, offset);
      ApplicationManager.getApplication()
          .invokeLater(() -> getContentManager().setSelectedContent(tab.content));
    }

    @Override
    public void setStopHandler(Task task, @Nullable Runnable runnable) {
      getTab(task).behaviour.setStopHandler(task, runnable);
    }
  }

  private NotificationListener createNotificationListener() {
    return new NotificationListener();
  }
}
