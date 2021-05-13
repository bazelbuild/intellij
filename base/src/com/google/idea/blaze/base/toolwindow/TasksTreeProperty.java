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

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Property that represents the tree structure of {@link Task} instances. Top level tasks (tasks
 * without a parent) are considered to be children of an artificial root. The property provides the
 * ability to add the listeners for addition and removal of the tasks into the tree.
 */
final class TasksTreeProperty {
  private final Task root = new Task("root", Task.Type.OTHER);
  // The weak hash map implementation is to indicate that orphaned nodes can be cleaned up
  // implicitly or explicitly. The explicit clean up is performed by the method
  // `cleanUpDetachedSubtree` below.
  private final Map<Task, List<Task>> adjacencyList = new WeakHashMap<>();

  private final Set<AddListener> addListeners = new HashSet<>();
  private final Set<RemoveListener> removeListeners = new HashSet<>();

  Task getRoot() {
    return root;
  }

  List<Task> getChildren(Task task) {
    return adjacencyList.get(task);
  }

  Task getParent(Task task) {
    return task.getParent().orElse(root);
  }

  void addTask(Task task) {
    Preconditions.checkNotNull(task);
    List<Task> siblings = adjacencyList.computeIfAbsent(getParent(task), t -> new ArrayList<>());
    siblings.add(task);
    for (AddListener listener : addListeners) {
      listener.taskAdded(task, siblings.size() - 1);
    }
  }

  void removeTask(Task task) {
    Preconditions.checkNotNull(task);
    Task parent = getParent(task);
    List<Task> children = adjacencyList.get(parent);

    if (children == null) {
      throw new IllegalStateException(
          "The tree doesn't have parent: " + parent + " for the task: " + task);
    }
    int taskIndex = children.indexOf(task);
    if (taskIndex < 0) {
      throw new IllegalStateException(
          "The tree doesn't have child: " + task + " for the parent: " + parent);
    }
    children.remove(task);
    cleanUpDetachedSubtree(task);
    for (RemoveListener listener : removeListeners) {
      listener.taskRemoved(task, taskIndex);
    }
  }

  private void cleanUpDetachedSubtree(Task task) {
    List<Task> children = adjacencyList.remove(task);
    if (children != null) {
      for (Task child : children) {
        cleanUpDetachedSubtree(child);
      }
    }
  }

  void addAdditionListener(AddListener listener) {
    addListeners.add(listener);
  }

  void addRemovalListener(RemoveListener listener) {
    removeListeners.add(listener);
  }

  void removeAdditionListener(AddListener listener) {
    addListeners.remove(listener);
  }

  void removeRemovalListener(RemoveListener listener) {
    removeListeners.remove(listener);
  }

  interface AddListener {
    void taskAdded(Task task, int index);
  }

  interface RemoveListener {
    void taskRemoved(Task task, int index);
  }
}
