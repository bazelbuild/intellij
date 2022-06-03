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

import com.intellij.icons.AllIcons.RunConfigurations;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.PresentableNodeDescriptor;
import com.intellij.ui.AnimatedIcon;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.text.DateFormatUtil;
import java.util.Date;
import javax.swing.Icon;

/** Contains logic for rendering Task information into a tree cell. */
public final class TaskNodeDescriptor extends PresentableNodeDescriptor<Task> {
  private static final Icon NODE_ICON_CANCELED = RunConfigurations.TestIgnored;
  private static final Icon NODE_ICON_ERROR = RunConfigurations.TestError;
  private static final Icon NODE_ICON_OK = RunConfigurations.TestPassed;
  private static final Icon NODE_ICON_RUNNING = new AnimatedIcon.Default();

  private final Task task;

  /**
   * Creates a new TaskNodeDecriptor.
   *
   * @param task the task to describe
   * @param parent the parent descriptor instance
   */
  public TaskNodeDescriptor(Task task, TaskNodeDescriptor parent) {
    super(task.getProject(), parent);
    this.task = task;
  }

  @Override
  protected void update(PresentationData presentationData) {
    presentationData.setPresentableText(task.getName());
    presentationData.setIcon(iconForTask(task));
    presentationData.addText(getName(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
    if (!task.getState().isEmpty()) {
      presentationData.addText(" " + task.getState(), SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES);
    }
    if (!task.isFinished()) {
      return;
    }

    if (task.getParent().isEmpty()) {
      String taskResult;
      if (task.isCancelled()) {
        taskResult = "cancelled";
      } else if (task.getHasErrors()) {
        taskResult = "failed";
      } else {
        taskResult = "finished";
      }

      presentationData.addText(" " + taskResult, SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES);

      task.getEndTime()
          .map(s -> DateFormatUtil.formatTime(Date.from(s)))
          .ifPresent(
              endDateString ->
                  presentationData.addText(
                      " at " + endDateString, SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES));
    }
  }

  private static Icon iconForTask(Task task) {
    if (!task.isFinished()) {
      return NODE_ICON_RUNNING;
    } else if (task.isCancelled()) {
      return NODE_ICON_CANCELED;
    } else if (task.getHasErrors()) {
      return NODE_ICON_ERROR;
    } else {
      return NODE_ICON_OK;
    }
  }

  @Override
  public Task getElement() {
    return this.task;
  }
}
