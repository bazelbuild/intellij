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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.idea.blaze.golang.sync.BlazeGoAdditionalLibraryRootsProvider.GO_EXTERNAL_LIBRARY_ROOT_NAME;

import com.google.common.base.Functions;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.golang.resolve.BlazeGoPackageFactory;
import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Modifies the project view by replacing the External Go Libraries root node (containing a flat
 * list of sources) with a root node that structures sources based on their import paths.
 */
public final class BlazeGoTreeStructureProvider implements TreeStructureProvider, DumbAware {

  @Override
  public Collection<AbstractTreeNode<?>> modify(
      AbstractTreeNode<?> parent, Collection<AbstractTreeNode<?>> children, ViewSettings settings) {
    Project project = parent.getProject();
    if (!Blaze.isBlazeProject(project)
        || !isGoBlazeExternalLibraryRoot(parent)) {
      return children;
    }
    ImmutableMap<VirtualFile, AbstractTreeNode<?>> originalFileNodes =
        getOriginalFileNodesMap(children);

    Map<File, String> fileToImportPathMap = BlazeGoPackageFactory.getFileToImportPathMap(project);
    if (fileToImportPathMap == null) {
      return children;
    }

    SortedMap<String, AbstractTreeNode<?>> newChildren = new TreeMap<>();

    ImmutableListMultimap<String, VirtualFile> importPathToFilesMap =
        originalFileNodes.keySet().stream()
            .collect(
                ImmutableListMultimap.toImmutableListMultimap(
                    // Some nodes may, for some reason, not be in importPathMap, use the empty
                    // String as a guard character.
                    virtualFile ->
                        fileToImportPathMap.getOrDefault(VfsUtil.virtualToIoFile(virtualFile), ""),
                    Function.identity()));

    for (String importPath : importPathToFilesMap.keySet()) {
      if (importPath.isEmpty()) {
        continue;
      }
      generateTree(
          settings,
          project,
          newChildren,
          importPath,
          ImmutableSet.copyOf(importPathToFilesMap.get(importPath)),
          originalFileNodes);
    }
    return Streams.concat(
            newChildren.values().stream(),
            // Put nodes without an importPath as direct children.
            importPathToFilesMap.get("").stream().map(originalFileNodes::get))
        .collect(toImmutableList());
  }

  private static void generateTree(
      ViewSettings settings,
      Project project,
      SortedMap<String, AbstractTreeNode<?>> newChildren,
      String importPath,
      ImmutableSet<VirtualFile> availableFiles,
      Map<VirtualFile, AbstractTreeNode<?>> originalFileNodes) {
    Iterator<Path> pathIter = Paths.get(importPath).iterator();
    if (!pathIter.hasNext()) {
      return;
    }
    // Root nodes (e.g., src) have to be added directly to newChildren.
    String rootName = pathIter.next().toString();
    GoSyntheticLibraryElementNode root =
        (GoSyntheticLibraryElementNode)
            newChildren.computeIfAbsent(
                rootName,
                (unused) ->
                    new GoSyntheticLibraryElementNode(
                        project,
                        new BlazeGoExternalSyntheticLibrary(rootName, availableFiles),
                        rootName,
                        settings,
                        new TreeMap<>()));

    // Child nodes (e.g., package_name under src) are added under root nodes recursively.
    GoSyntheticLibraryElementNode leaf =
        buildChildTree(settings, project, availableFiles, pathIter, root);

    // Required for files to actually show up in the Project View.
    availableFiles.forEach((file) -> leaf.addChild(file.getName(), originalFileNodes.get(file)));
  }

  /**
   * Recurse down the import path tree and add elements as children.
   *
   * <p>Fills previously created nodes with source files from the current import path.
   */
  private static GoSyntheticLibraryElementNode buildChildTree(
      ViewSettings settings,
      Project project,
      ImmutableSet<VirtualFile> files,
      Iterator<Path> pathIter,
      GoSyntheticLibraryElementNode parent) {
    while (pathIter.hasNext()) {
      parent.addFiles(files);
      String dirName = pathIter.next().toString();

      // current path already was created, no need to re-create
      if (parent.hasChild(dirName)) {
        parent = parent.getChildNode(dirName);
        continue;
      }
      GoSyntheticLibraryElementNode libraryNode =
          new GoSyntheticLibraryElementNode(
              project,
              new BlazeGoExternalSyntheticLibrary(dirName, files),
              dirName,
              settings,
              new TreeMap<>());
      parent.addChild(dirName, libraryNode);
      parent = libraryNode;
    }
    return parent;
  }

  private static boolean isGoBlazeExternalLibraryRoot(AbstractTreeNode<?> parent) {
    if (parent.getName() == null) {
      return false;
    }
    return parent.getName().equals(GO_EXTERNAL_LIBRARY_ROOT_NAME);
  }

  private static ImmutableMap<VirtualFile, AbstractTreeNode<?>> getOriginalFileNodesMap(
      Collection<AbstractTreeNode<?>> children) {

    return children.stream()
        .filter(child -> (child instanceof PsiFileNode))
        .filter(child -> ((PsiFileNode) child).getVirtualFile() != null)
        .collect(
            toImmutableMap(child -> ((PsiFileNode) child).getVirtualFile(), Functions.identity()));
  }
}
