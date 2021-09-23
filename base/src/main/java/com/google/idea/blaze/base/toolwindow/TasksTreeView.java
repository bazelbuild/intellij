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

import com.google.idea.common.ui.properties.ChangeListener;
import com.google.idea.common.ui.properties.ObservableValue;
import com.google.idea.common.ui.properties.Property;
import com.google.idea.common.ui.templates.AbstractView;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.AnimatedIcon;
import com.intellij.ui.render.LabelBasedRenderer;
import com.intellij.ui.tree.BaseTreeModel;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.tree.TreeUtil;
import java.awt.Component;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.swing.Icon;
import javax.swing.JTree;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;

/** The view that represents the tree of the hierarchy of tasks. */
final class TasksTreeView extends AbstractView<Tree> {
  private final TasksTreeModel model;
  private final JTreeModel jTreeModel = new JTreeModel();

  private final TreeSelectionListener treeSelectionListener = this::onTaskSelected;
  private final ChangeListener<Task> modelSelectedTaskListener = this::selectTask;

  TasksTreeView(TasksTreeModel model) {
    this.model = model;
    // looks like the insertion and removal listeners for the JTree's model need to be executed even
    // when the tree is not displayed
    model.tasksTreeProperty().addAdditionListener(jTreeModel::newTaskAdded);
    model.tasksTreeProperty().addRemovalListener(jTreeModel::taskRemoved);
  }

  @Override
  protected Tree createComponent() {
    Tree tree = new Tree(jTreeModel);
    tree.setRootVisible(false);
    tree.setCellRenderer(new TreeCellRenderer());
    return tree;
  }

  @Override
  protected void bind() {
    getComponent().addTreeSelectionListener(treeSelectionListener);
    Property<Task> selectedTaskProperty = model.selectedTaskProperty();
    selectedTaskProperty.addListener(modelSelectedTaskListener);
    selectTask(selectedTaskProperty, null, selectedTaskProperty.getValue());
  }

  @Override
  protected void unbind() {
    getComponent().removeTreeSelectionListener(treeSelectionListener);
    model.selectedTaskProperty().removeListener(modelSelectedTaskListener);
  }

  private void onTaskSelected(TreeSelectionEvent event) {
    TreePath selectionPath = event.getNewLeadSelectionPath();
    Object selection = selectionPath == null ? null : selectionPath.getLastPathComponent();
    model.selectedTaskProperty().setValue(selection instanceof Task ? (Task) selection : null);
  }

  private void selectTask(
      ObservableValue<? extends Task> observable, @Nullable Task oldTask, @Nullable Task task) {
    if (Objects.equals(task, getComponent().getLastSelectedPathComponent())) {
      return;
    }
    JTree tree = getComponent();
    if (task == null) {
      tree.clearSelection();
    }
    TreeUtil.selectPath(tree, taskToTreePath(task), false);
    tree.repaint();
  }

  private TreePath taskToTreePath(Task task) {
    Task root = model.tasksTreeProperty().getRoot();
    if (root.equals(task)) {
      return new TreePath(root);
    }
    Deque<Object> path = new ArrayDeque<>();
    path.push(task);
    while (task.getParent().isPresent()) {
      Task parent = task.getParent().get();
      path.push(parent);
      task = parent;
    }
    path.push(root);
    return new TreePath(path.toArray());
  }

  /** Swing's TreeModel implementation that reflects the hierarchy of the tasks. */
  private final class JTreeModel extends BaseTreeModel<Task> {
    @Override
    public List<Task> getChildren(Object parent) {
      if (!(parent instanceof Task)) {
        throw new IllegalStateException("Task trees can only contain Task instances");
      }
      return model.tasksTreeProperty().getChildren((Task) parent);
    }

    @Override
    public Object getRoot() {
      return model.tasksTreeProperty().getRoot();
    }

    void newTaskAdded(Task task, int taskIndex) {
      treeNodesInserted(
          /* path= */ taskToTreePath(model.tasksTreeProperty().getParent(task)),
          /* indices= */ new int[] {taskIndex},
          /* children= */ new Task[] {task});
    }

    void taskRemoved(Task task, int taskIndex) {
      treeNodesRemoved(
          /* path= */ taskToTreePath(model.tasksTreeProperty().getParent(task)),
          /* indices= */ new int[] {taskIndex},
          /* children= */ new Task[] {task});
    }
  }

  /** Swing's TreeCellRenderer that defines the representation of the tree node. */
  private static final class TreeCellRenderer extends LabelBasedRenderer.Tree {
    static final Icon NODE_ICON_RUNNING = new AnimatedIcon.Default();
    static final Icon NODE_ICON_OK = AllIcons.RunConfigurations.TestPassed;
    static final Icon NODE_ICON_ERROR = AllIcons.RunConfigurations.TestError;
    static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

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
      setText(makeLabelText(task, selected));

      return this;
    }

    private static String makeLabelText(Task task, boolean selected) {
      return String.format(
          "<html>%s%s</html>", nameLabel(task.getName()), timesLabel(task, selected));
    }

    private static CharSequence nameLabel(String name) {
      // truncate the name to avoid very long horizontal scroll in the tree
      return name.length() < 81 ? name : name.substring(0, 80) + "...";
    }

    private static CharSequence timesLabel(Task task, boolean selected) {
      return startTimeLabel(task)
          .map(
              s ->
                  " <font"
                      + (selected ? '>' : " color=gray>")
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
