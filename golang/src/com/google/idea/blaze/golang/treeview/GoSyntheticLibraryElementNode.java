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

package com.google.idea.blaze.golang.treeview;

import com.goide.GoIcons;
import com.google.common.collect.ImmutableSet;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.SyntheticLibraryElementNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Collection;
import java.util.Map;
import java.util.SortedMap;
import javax.swing.Icon;

/**
 * Represents a Go directory node within the "External Libraries" project view.
 *
 * <p>It can have an arbitrary amount of child nodes, forming a tree. Child nodes may be added and
 * queried with the appropriate methods.
 */
class GoSyntheticLibraryElementNode extends SyntheticLibraryElementNode {
  private final Map<String, AbstractTreeNode<?>> children;

  GoSyntheticLibraryElementNode(
      Project project,
      BlazeGoExternalSyntheticLibrary library,
      String dirName,
      ViewSettings settings,
      SortedMap<String, AbstractTreeNode<?>> children) {

    super(project, library, new BlazeGoLibraryItemPresentation(dirName), settings);
    this.children = children;
  }

  @Override
  public Collection<AbstractTreeNode<?>> getChildren() {
    return children.values();
  }

  GoSyntheticLibraryElementNode getChildNode(String dirName) {
    return (GoSyntheticLibraryElementNode) children.get(dirName);
  }

  public void addChild(String dirName, AbstractTreeNode<?> child) {
    children.put(dirName, child);
  }

  public boolean hasChild(String dirName) {
    return children.containsKey(dirName);
  }

  public void addFiles(ImmutableSet<VirtualFile> files) {
    ((BlazeGoExternalSyntheticLibrary) getValue()).addFiles(files);
  }

  private static class BlazeGoLibraryItemPresentation implements ItemPresentation {

    private final String dirName;

    public BlazeGoLibraryItemPresentation(String dirName) {
      this.dirName = dirName;
    }

    @Override
    public String getPresentableText() {
      return dirName;
    }

    @Override
    public Icon getIcon(boolean ignore) {
      return GoIcons.PACKAGE;
    }
  }
}
