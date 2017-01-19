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
package com.google.idea.blaze.base.sync.actions;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.idea.blaze.base.actions.BlazeProjectAction;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.Blaze.BuildSystem;
import com.google.idea.blaze.base.sync.BlazeSyncManager;
import com.google.idea.blaze.base.sync.BuildTargetFinder;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.base.sync.status.BlazeSyncStatus;
import com.google.idea.blaze.base.targetmaps.SourceToTargetMap;
import com.google.idea.common.actionhelper.ActionPresentationHelper;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.util.List;
import javax.annotation.Nullable;

/** Allows a partial sync of the project depending on what's been selected. */
public class PartialSyncAction extends BlazeProjectAction {

  @Override
  protected void actionPerformedInBlazeProject(Project project, AnActionEvent e) {
    VirtualFile virtualFile = getSelectedFile(e);
    List<TargetExpression> targets = getTargets(project, virtualFile);
    BlazeSyncManager.getInstance(project).partialSync(targets);
  }

  @Override
  protected void updateForBlazeProject(Project project, AnActionEvent e) {
    VirtualFile virtualFile = getSelectedFile(e);
    List<TargetExpression> targets = getTargets(project, virtualFile);
    ActionPresentationHelper.of(e)
        .disableIf(BlazeSyncStatus.getInstance(project).syncInProgress())
        .disableIf(targets.isEmpty())
        .setTextWithSubject("Partially Sync File", "Partially Sync %s", virtualFile)
        .disableWithoutSubject()
        .commit();
  }

  private VirtualFile getSelectedFile(AnActionEvent e) {
    VirtualFile virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
    if (virtualFile == null || !virtualFile.isInLocalFileSystem()) {
      return null;
    }
    return virtualFile;
  }

  private static List<TargetExpression> getTargets(
      Project project, @Nullable VirtualFile virtualFile) {
    if (virtualFile == null) {
      return ImmutableList.of();
    }
    List<TargetExpression> targets = Lists.newArrayList();
    WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProject(project);
    SourceToTargetMap.getInstance(project);
    if (!virtualFile.isDirectory()) {
      targets.addAll(
          SourceToTargetMap.getInstance(project)
              .getTargetsToBuildForSourceFile(new File(virtualFile.getPath())));
    }
    if (targets.isEmpty()) {
      ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
      if (projectViewSet != null) {
        BuildSystem buildSystem = Blaze.getBuildSystem(project);
        ImportRoots importRoots =
            ImportRoots.builder(workspaceRoot, buildSystem).add(projectViewSet).build();
        BuildTargetFinder buildTargetFinder =
            new BuildTargetFinder(project, workspaceRoot, importRoots);
        TargetExpression targetExpression =
            buildTargetFinder.findTargetForFile(new File(virtualFile.getPath()));
        if (targetExpression != null) {
          targets.add(targetExpression);
        }
      }
    }
    return targets;
  }
}
