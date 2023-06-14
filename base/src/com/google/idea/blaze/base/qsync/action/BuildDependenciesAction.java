/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.qsync.action;

import com.google.idea.blaze.base.actions.BlazeProjectAction;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.qsync.QuerySyncManager;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.status.BlazeSyncStatus;
import com.intellij.icons.AllIcons.Actions;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.nio.file.Path;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Action to build dependencies and enable analysis.
 *
 * <p>It can operate on a source file, BUILD file or package. See {@link
 * com.google.idea.blaze.base.qsync.DependencyTracker#getProjectTargets(BlazeContext, List)} for a
 * description of what targets dependencies aren built for in each case.
 */
public class BuildDependenciesAction extends BlazeProjectAction {

  private static final String NAME = "Build dependencies";

  @Override
  @NotNull
  public ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  protected QuerySyncStatus querySyncSupport() {
    return QuerySyncStatus.REQUIRED;
  }

  @Override
  protected void updateForBlazeProject(Project project, AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    presentation.setIcon(Actions.Compile);
    presentation.setText(NAME);
    VirtualFile virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
    if (virtualFile == null || !virtualFile.isInLocalFileSystem()) {
      presentation.setEnabledAndVisible(false);
      return;
    }
    Path workspaceRoot = WorkspaceRoot.fromProject(project).path();
    Path filePath = virtualFile.toNioPath();
    if (!filePath.startsWith(workspaceRoot)) {
      presentation.setEnabledAndVisible(false);
      return;
    }
    if (BlazeSyncStatus.getInstance(project).syncInProgress()) {
      presentation.setEnabled(false);
      return;
    }
    QuerySyncManager syncManager = QuerySyncManager.getInstance(project);
    Path relative = workspaceRoot.relativize(filePath);
    presentation.setEnabled(syncManager.canEnableAnalysisFor(relative));
  }

  @Override
  protected void actionPerformedInBlazeProject(Project project, AnActionEvent e) {
    QuerySyncManager syncManager = QuerySyncManager.getInstance(project);
    Path workspaceRoot = WorkspaceRoot.fromProject(project).path();
    VirtualFile virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
    Path filePath = virtualFile.toNioPath();
    Path relative = workspaceRoot.relativize(filePath);
    syncManager.enableAnalysis(relative);
  }
}
