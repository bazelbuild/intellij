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

import com.google.idea.common.ui.templates.AbstractView;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.AnimatedIcon;
import com.intellij.ui.JBDefaultTreeCellRenderer;
import com.intellij.ui.tree.BaseTreeModel;
import com.intellij.ui.treeStructure.Tree;
import java.awt.Component;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import javax.swing.Icon;
import javax.swing.JTree;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;

/** The view that represents the tree of the hierarchy of tasks. */
final class TasksTreeView extends AbstractView<Tree> {
  private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

  private final TasksTreeModel model;

  private final TreeSelectionListener treeSelectionListener = this::onTaskSelected;

  TasksTreeView(TasksTreeModel model) {
    this.model = model;
  }

  @Override
  protected Tree createComponent() {
    Tree tree = new Tree(new TreeModel());
    tree.setRootVisible(false);
    tree.setCellRenderer(new TreeCellRenderer());
    return tree;
  }

  @Override
  protected void bind() {
    getComponent().addTreeSelectionListener(treeSelectionListener);
  }

  @Override
  protected void unbind() {
    getComponent().removeTreeSelectionListener(treeSelectionListener);
  }

  private void onTaskSelected(TreeSelectionEvent event) {
    TreePath selectionPath = event.getNewLeadSelectionPath();
    Object selection = selectionPath == null ? null : selectionPath.getLastPathComponent();
    model.selectedTaskProperty().setValue(selection instanceof Task ? (Task) selection : null);
  }

  /** Swing's TreeModel implementation that reflects the hierarchy of the tasks. */
  private final class TreeModel extends BaseTreeModel<Task> {
    /** The invisible root of the tree. */
    final Object root = new Object();

    @Override
    public List<Task> getChildren(Object parent) {
      if (parent == root) {
        return model.getTopLevelTasks();
      }
      if (!(parent instanceof Task)) {
        throw new IllegalStateException("Task trees can only contain Task instances");
      }
      return ((Task) parent).getChildren();
    }

    @Override
    public Object getRoot() {
      return root;
    }
  }

  /**
   * Swing's TreeCellRenderer that defines the representation of the tree node. Replace {@link
   * JBDefaultTreeCellRenderer} with {@link com.intellij.ui.render.LabelBasedRenderer.Tree} when
   * #api193 fully goes away.
   */
  private static final class TreeCellRenderer extends JBDefaultTreeCellRenderer {
    static final Icon NODE_ICON_RUNNING = new AnimatedIcon.Default();
    private static final Icon NODE_ICON_OK = AllIcons.RunConfigurations.TestPassed;
    private static final Icon NODE_ICON_ERROR = AllIcons.RunConfigurations.TestError;

    @Override
    public Component getTreeCellRendererComponent(
        JTree tree,
        Object value,
        boolean selected,
        boolean expanded,
        boolean leaf,
        int row,
        boolean hasFocus) {
      if (!(value instanceof Task)) {
        throw new IllegalStateException("Task trees can only contain Task instances");
      }

      super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);

      Task task = (Task) value;
      if (task.isFinished()) {
        setIcon(task.getHasErrors() ? NODE_ICON_ERROR : NODE_ICON_OK);
      } else {
        setIcon(NODE_ICON_RUNNING);
      }
      setText(makeLabelText(task));

      return this;
    }

    private static String makeLabelText(Task task) {
      return String.format("<html>%s%s</html>", task.getName(), timesLabel(task));
    }

    private static CharSequence timesLabel(Task task) {
      return startTimeLabel(task)
          .map(
              s ->
                  " <font color=gray>"
                      + s
                      + durationLabel(task).map(d -> " [" + d + ']').orElse("")
                      + "</font>")
          .orElse("");
    }

    private static Optional<String> startTimeLabel(Task task) {
      return task.getStartTime()
          .map(s -> LocalDateTime.ofInstant(s, ZoneId.systemDefault()).format(TIME_FORMATTER));
    }

    private static Optional<String> durationLabel(Task task) {
      return task.getStartTime()
          .flatMap(s -> task.getEndTime().map(e -> Duration.between(s, e)))
          .map(d -> StringUtil.formatDuration(d.toMillis()));
    }
  }
}
