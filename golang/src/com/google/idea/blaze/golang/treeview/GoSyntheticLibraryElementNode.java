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

import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.SyntheticLibraryElementNode;
import com.intellij.ide.projectView.impl.nodes.ProjectViewDirectoryHelper;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.SyntheticLibrary;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.google.idea.blaze.golang.resolve.BlazeGoPackageFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.jetbrains.annotations.NotNull;

/**
* Represents a Go directory node within the "External Libraries" project view.
*/
public class GoSyntheticLibraryElementNode extends SyntheticLibraryElementNode {
    protected Map<String, GoSyntheticLibraryElementNode> importToChildNodeMap;

    public GoSyntheticLibraryElementNode(@NotNull Project project, @NotNull SyntheticLibrary library,
                                         @NotNull ItemPresentation itemPresentation, ViewSettings settings,
                                         @NotNull Map<String, GoSyntheticLibraryElementNode> children) {
        super(project, library, itemPresentation, settings);
        importToChildNodeMap = children;
    }

    @Override
    public boolean contains(@NotNull VirtualFile file) {
        if (getLibrary().contains(file)) {
            return true;
        }
        Project project = Objects.requireNonNull(getProject());
        // Get the file's importpath so we can determine if it is a descendant of the current node
        Map<File, String> fileToImportPathMap = BlazeGoPackageFactory.getFileToImportPathMap(project);
        String importPath = fileToImportPathMap.get(VfsUtil.virtualToIoFile(file));
        Path parent = importPath != null ? Paths.get(importPath) : null;
        while (parent != null) {
            if (hasChild(parent.toString())) {
                return true;
            }
            parent = parent.getParent();
        }
        return false;
    }

    @NotNull
    @Override
    public Collection<AbstractTreeNode<?>> getChildren() {
        List<AbstractTreeNode<?>> children = new ArrayList<AbstractTreeNode<?>>();
        children.addAll(importToChildNodeMap.values());
        SyntheticLibrary library = getLibrary();
        Project project = Objects.requireNonNull(getProject());
        Set<VirtualFile> excludedRoots = library.getExcludedRoots();
        List<VirtualFile> childrenFiles = ContainerUtil.filter(library.getAllRoots(), file -> file.isValid() && !excludedRoots.contains(file));
        children.addAll(ProjectViewDirectoryHelper.getInstance(project).createFileAndDirectoryNodes(childrenFiles, getSettings()));
        return children;
    }

    @NotNull
    private SyntheticLibrary getLibrary() {
        return Objects.requireNonNull(getValue());
    }

    public void addChild(String importPath, GoSyntheticLibraryElementNode child) { importToChildNodeMap.put(importPath, child); }

    public boolean hasChild(String importPath) { return importToChildNodeMap.containsKey(importPath); }

    public GoSyntheticLibraryElementNode getChild(String importPath) { return importToChildNodeMap.get(importPath); }

}