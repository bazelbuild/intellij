/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.sync.workspace;

import com.google.common.annotations.VisibleForTesting;
import com.google.idea.blaze.base.bazel.BuildSystemProvider;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetName;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.sync.SyncCache;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.annotation.Nullable;

/**
 * External-workspace-aware resolution of workspace paths.
 */
public class WorkspaceHelper {

  private static BlazeProjectData blazeProjectData;
  private static final Logger logger = Logger.getInstance(WorkspaceHelper.class);

  private static class Workspace {

    private final WorkspaceRoot root;
    @Nullable
    private final String externalWorkspaceName;

    private Workspace(WorkspaceRoot root, @Nullable String externalWorkspaceName) {
      this.root = root;
      this.externalWorkspaceName = externalWorkspaceName;
    }
  }

  private static synchronized BlazeProjectData getBlazeProjectData(Project project) {
    if (blazeProjectData == null) {
      blazeProjectData = BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    }
    return blazeProjectData;
  }

  @Nullable
  public static WorkspaceRoot resolveExternalWorkspace(Project project, String workspaceName) {
    return getExternalWorkspaceRootsFile(workspaceName, project);
  }

  /**
   * Resolves the parent blaze package corresponding to this label.
   */
  @Nullable
  public static File resolveBlazePackage(Project project, Label label) {
    logger.debug("resolveBlazePackage: " + label + " in project " + project.getName());
    if (!label.isExternal()) {
      WorkspacePathResolver pathResolver =
          WorkspacePathResolverProvider.getInstance(project).getPathResolver();
      return pathResolver != null ? pathResolver.resolveToFile(label.blazePackage()) : null;
    }

    WorkspaceRoot root = getExternalWorkspaceRootsFile(label.externalWorkspaceName(), project);
    return root != null ? root.fileForPath(label.blazePackage()) : null;
  }

  @Nullable
  public static WorkspacePath resolveWorkspacePath(Project project, File absoluteFile) {
    Workspace workspace = resolveWorkspace(project, absoluteFile);
    return workspace != null ? workspace.root.workspacePathForSafe(absoluteFile) : null;
  }

  /**
   * Converts a file to the corresponding BUILD label for this project, if valid.
   */
  @Nullable
  public static Label getBuildLabel(Project project, File absoluteFile) {
    logger.debug("getBuildLabel for file " + absoluteFile.getAbsolutePath());
    Workspace workspace = resolveWorkspace(project, absoluteFile);
    if (workspace == null) {
      return null;
    }
    WorkspacePath workspacePath = workspace.root.workspacePathForSafe(absoluteFile);
    if (workspacePath == null) {
      return null;
    }
    return deriveLabel(project, workspace, workspacePath);
  }

  @Nullable
  private static Workspace resolveWorkspace(Project project, File absoluteFile) {
    WorkspacePathResolver pathResolver =
        WorkspacePathResolverProvider.getInstance(project).getPathResolver();
    if (pathResolver == null) {
      return null;
    }

    // try project workspace first
    WorkspaceRoot root = pathResolver.findWorkspaceRoot(absoluteFile);
    if (root != null) {
      logger.debug("resolveWorkspace: " + root.directory().getAbsolutePath());
      return new Workspace(root, null);
    }

    BlazeProjectData blazeProjectData = getBlazeProjectData(project);
    Path bazelRootPath = Paths.get(
        blazeProjectData.getBlazeInfo().getOutputBase().getAbsolutePath(),
        "external").normalize();

    logger.debug("the bazelRootPath is " + bazelRootPath);
    Path path = Paths.get(absoluteFile.getAbsolutePath()).normalize();

    // Check if the file path starts with the root directory path
    if (!path.startsWith(bazelRootPath)) {
      return null;
    }

    Path relativePath = bazelRootPath.relativize(path);
    if (relativePath.getNameCount() > 0) {
      String firstFolder = relativePath.getName(0).toString();
      Path workspaceRootPath = bazelRootPath.resolve(firstFolder);
      if (workspaceRootPath.toFile().exists()) {
        logger.debug("resolveWorkspace: " + workspaceRootPath + " firstFolder: " + firstFolder);
        return new Workspace(new WorkspaceRoot(workspaceRootPath.toFile()), firstFolder);
      }
    }
    return null;
  }

  private static Label deriveLabel(
      Project project, Workspace workspace, WorkspacePath workspacePath) {
    BuildSystemProvider provider = Blaze.getBuildSystemProvider(project);
    File file = workspace.root.fileForPath(workspacePath);
    if (provider.isBuildFile(file.getName())) {
      return Label.create(
          workspace.externalWorkspaceName,
          workspace.root.workspacePathFor(file.getParentFile()),
          TargetName.create("__pkg__"));
    }
    WorkspacePath packagePath = getPackagePath(provider, workspace.root, workspacePath);
    if (packagePath == null) {
      return null;
    }
    TargetName targetName =
        TargetName.createIfValid(
            FileUtil.getRelativePath(workspace.root.fileForPath(packagePath), file));
    return targetName != null
        ? Label.create(workspace.externalWorkspaceName, packagePath, targetName)
        : null;
  }

  private static WorkspacePath getPackagePath(
      BuildSystemProvider provider, WorkspaceRoot root, WorkspacePath workspacePath) {
    File file = root.fileForPath(workspacePath).getParentFile();
    while (file != null && FileUtil.isAncestor(root.directory(), file, false)) {
      ProgressManager.checkCanceled();
      if (provider.findBuildFileInDirectory(file) != null) {
        return root.workspacePathFor(file);
      }
      file = file.getParentFile();
    }
    return null;
  }

  @VisibleForTesting
  public static File getExternalSourceRoot(BlazeProjectData projectData) {
    return new File(projectData.getBlazeInfo().getOutputBase(), "external");
  }

  @Nullable
  private static synchronized WorkspaceRoot getExternalWorkspaceRootsFile(String workspaceName,
      Project project) {
    if (Blaze.getBuildSystemName(project) == BuildSystemName.Blaze) {
      return null;
    }
    logger.debug("getExternalWorkspaceRootsFile for " + workspaceName);
    File baseDir = SyncCache.getInstance(project)
        .get(workspaceName, WorkspaceHelper::getWorkspaceRootDir);

    if (baseDir == null) {
      return null;
    }
    File rootFile = new File(baseDir, "external/" + workspaceName);
    return rootFile.exists() ? new WorkspaceRoot(rootFile) : null;
  }

  private static File getWorkspaceRootDir(Project project, BlazeProjectData blazeProjectData) {
    if (blazeProjectData == null) {
      logger.debug("the blazeProjectData is null " + project.getName());
      return null;
    }
    File root = blazeProjectData.getBlazeInfo().getOutputBase();
    return root;
  }

}
