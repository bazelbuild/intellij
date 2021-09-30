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
package com.google.idea.blaze.golang.treeview;

import com.google.common.collect.ImmutableMultimap;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.libraries.BlazeExternalSyntheticLibrary;
import com.google.idea.blaze.golang.sync.BlazeGoExternalSyntheticLibrary;
import com.intellij.ide.projectView.impl.nodes.ExternalLibrariesNode;
import com.intellij.ide.projectView.impl.nodes.SyntheticLibraryElementNode;
import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.SyntheticLibrary;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Iterator;
import java.util.HashMap;

/**
 * Modifies the project view by replacing the External Go Libraries root node (contaning a flat list of sources)
 * with a root node that structures sources based on their importpaths.
 */
public class BlazeGoTreeStructureProvider implements TreeStructureProvider, DumbAware {
  @Override
  public Collection<AbstractTreeNode<?>> modify(
      AbstractTreeNode<?> parent, Collection<AbstractTreeNode<?>> children, ViewSettings settings) {
    Project project = parent.getProject();
    if (!Blaze.isBlazeProject(project) || !(parent instanceof ExternalLibrariesNode)) {
      return children;
    }
    List<AbstractTreeNode<?>> newChildren = new ArrayList<AbstractTreeNode<?>>();
    for (AbstractTreeNode<?> child : children) {
      if ((child.getValue() instanceof BlazeGoExternalSyntheticLibrary)) {
        BlazeGoExternalSyntheticLibrary libraryRoot = (BlazeGoExternalSyntheticLibrary)child.getValue();
        child = libraryRoot.isRoot() ? createSyntheticDirectoryStructure(libraryRoot, settings, project) : child;
      }
      newChildren.add(child);
    }
    return newChildren;
  }

  /**
  * Creates the importpath based project structure.
  * Loops through the Go project's external importpath references and creates a {@link GoSyntheticLibraryElementNode} for each directory.
  * Each node holds a {@link BlazeExternalSyntheticLibrary} representing the dir's source files.
  */
  private static GoSyntheticLibraryElementNode createSyntheticDirectoryStructure(BlazeGoExternalSyntheticLibrary libraryRoot, ViewSettings settings, Project project) {
    ImmutableMultimap<String, File> importPathToFilesMap = libraryRoot.getImportpathToFilesMap();
    BlazeExternalSyntheticLibrary newLibraryRoot =  new BlazeExternalSyntheticLibrary(libraryRoot.getPresentableText(), Collections.emptyList());
    GoSyntheticLibraryElementNode rootNode = new GoSyntheticLibraryElementNode(project, newLibraryRoot, (ItemPresentation)newLibraryRoot, settings, new HashMap<String, GoSyntheticLibraryElementNode>());
    String[] imports = importPathToFilesMap.keySet().toArray(String[]::new);
    // sort imports to ensure parent dirs are visited before children
    Arrays.sort(imports);
    for (String key : imports) {
      GoSyntheticLibraryElementNode currRoot = rootNode;
      String currImport = null;
      Iterator<Path> it = Paths.get(key).iterator();
      while (it.hasNext()) {
        String dir = it.next().toString();
        currImport = currImport == null ? dir : currImport + "/" + dir;
        if (!currRoot.hasChild(currImport)) {
          Collection<File> files = it.hasNext() ? Collections.emptyList() :  importPathToFilesMap.get(key);
          BlazeExternalSyntheticLibrary currLib =  new BlazeGoExternalSyntheticLibrary(dir, currImport, files);
          GoSyntheticLibraryElementNode currNode = new GoSyntheticLibraryElementNode(project, currLib, (ItemPresentation)currLib, settings, new HashMap<String, GoSyntheticLibraryElementNode>());
          currRoot.addChild(currImport, currNode);
        }
        currRoot = currRoot.getChild(currImport);
      }
    }
    return rootNode;
  }
}
