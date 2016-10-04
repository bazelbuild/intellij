/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.sync.projectstructure;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.util.io.URLUtil;
import java.io.File;
import java.util.Collection;
import java.util.List;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/** Modifies content entries based on project data. */
public class ContentEntryEditor {

  public static void createContentEntries(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      BlazeProjectData blazeProjectData,
      ModifiableRootModel modifiableRootModel) {
    ImportRoots importRoots =
        ImportRoots.builder(workspaceRoot, Blaze.getBuildSystem(project))
            .add(projectViewSet)
            .build();
    Collection<WorkspacePath> rootDirectories = importRoots.rootDirectories();
    Collection<WorkspacePath> excludeDirectories = importRoots.excludeDirectories();
    Multimap<WorkspacePath, WorkspacePath> excludesByRootDirectory =
        sortExcludesByRootDirectory(rootDirectories, excludeDirectories);

    List<ContentEntry> contentEntries = Lists.newArrayList();
    for (WorkspacePath rootDirectory : rootDirectories) {
      File root = workspaceRoot.fileForPath(rootDirectory);
      ContentEntry contentEntry = modifiableRootModel.addContentEntry(pathToUrl(root.getPath()));
      contentEntries.add(contentEntry);

      for (WorkspacePath exclude : excludesByRootDirectory.get(rootDirectory)) {
        File excludeFolder = workspaceRoot.fileForPath(exclude);
        contentEntry.addExcludeFolder(pathToIdeaUrl(excludeFolder));
      }
    }

    for (BlazeSyncPlugin syncPlugin : BlazeSyncPlugin.EP_NAME.getExtensions()) {
      syncPlugin.updateContentEntries(
          project, context, workspaceRoot, projectViewSet, blazeProjectData, contentEntries);
    }
  }

  private static Multimap<WorkspacePath, WorkspacePath> sortExcludesByRootDirectory(
      Collection<WorkspacePath> rootDirectories, Collection<WorkspacePath> excludedDirectories) {

    Multimap<WorkspacePath, WorkspacePath> result = ArrayListMultimap.create();
    for (WorkspacePath exclude : excludedDirectories) {
      WorkspacePath foundWorkspacePath =
          rootDirectories
              .stream()
              .filter(rootDirectory -> isUnderRootDirectory(rootDirectory, exclude.relativePath()))
              .findFirst()
              .orElse(null);
      if (foundWorkspacePath != null) {
        result.put(foundWorkspacePath, exclude);
      }
    }
    return result;
  }

  private static boolean isUnderRootDirectory(WorkspacePath rootDirectory, String relativePath) {
    if (rootDirectory.isWorkspaceRoot()) {
      return true;
    }
    String rootDirectoryString = rootDirectory.toString();
    return relativePath.startsWith(rootDirectoryString)
        && (relativePath.length() == rootDirectoryString.length()
            || (relativePath.charAt(rootDirectoryString.length()) == '/'));
  }

  @NotNull
  private static String pathToUrl(@NotNull String filePath) {
    filePath = FileUtil.toSystemIndependentName(filePath);
    if (filePath.endsWith(".srcjar") || filePath.endsWith(".jar")) {
      return URLUtil.JAR_PROTOCOL + URLUtil.SCHEME_SEPARATOR + filePath + URLUtil.JAR_SEPARATOR;
    } else if (filePath.contains("src.jar!")) {
      return URLUtil.JAR_PROTOCOL + URLUtil.SCHEME_SEPARATOR + filePath;
    } else {
      return VfsUtilCore.pathToUrl(filePath);
    }
  }

  @NotNull
  private static String pathToIdeaUrl(@NotNull File path) {
    return pathToUrl(toSystemIndependentName(path.getPath()));
  }

  @NotNull
  private static String toSystemIndependentName(@NonNls @NotNull String aFileName) {
    return FileUtilRt.toSystemIndependentName(aFileName);
  }
}
