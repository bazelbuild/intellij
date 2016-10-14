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

import com.google.common.collect.Lists;
import com.google.idea.blaze.base.actions.BlazeAction;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.rulemaps.SourceToRuleMap;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.BlazeSyncManager;
import com.google.idea.blaze.base.sync.BlazeSyncParams;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.util.List;
import javax.annotation.Nullable;

/** Allows a partial sync of the project depending on what's been selected. */
public class PartialSyncAction extends BlazeAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    if (project != null) {
      List<TargetExpression> targetExpressions = Lists.newArrayList();
      getTargets(e, targetExpressions);

      BlazeSyncParams syncParams =
          new BlazeSyncParams.Builder("Partial Sync", BlazeSyncParams.SyncMode.INCREMENTAL)
              .addTargetExpressions(targetExpressions)
              .build();

      BlazeSyncManager.getInstance(project).requestProjectSync(syncParams);
    }
  }

  @Override
  protected void doUpdate(AnActionEvent e) {
    super.doUpdate(e);
    List<TargetExpression> targets = Lists.newArrayList();
    String objectName = getTargets(e, targets);

    boolean enabled = objectName != null && !targets.isEmpty();
    Presentation presentation = e.getPresentation();
    presentation.setEnabled(enabled);

    if (enabled) {
      presentation.setText(
          String.format("Partially Sync %s with %s", objectName, buildSystemName(e.getProject())));
    } else {
      presentation.setText(String.format("Partial %s Sync", buildSystemName(e.getProject())));
    }
  }

  private static String buildSystemName(@Nullable Project project) {
    return Blaze.buildSystemName(project);
  }

  @Nullable
  private String getTargets(AnActionEvent e, List<TargetExpression> targets) {
    Project project = e.getProject();
    VirtualFile virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
    if (project == null || virtualFile == null || !virtualFile.isInLocalFileSystem()) {
      return null;
    }

    String objectName = null;
    WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProject(project);

    if (virtualFile.isDirectory()) {
      if (workspaceRoot.isInWorkspace(virtualFile)) {
        targets.add(
            TargetExpression.allFromPackageRecursive(workspaceRoot.workspacePathFor(virtualFile)));
      }
      objectName = "Package";
    } else {
      targets.addAll(
          SourceToRuleMap.getInstance(project)
              .getTargetsToBuildForSourceFile(new File(virtualFile.getPath())));

      // If empty, try to build parent package
      if (targets.isEmpty()) {
        VirtualFile parent = virtualFile.getParent();
        if (parent.isDirectory()) {
          if (workspaceRoot.isInWorkspace(parent)) {
            targets.add(
                TargetExpression.allFromPackageNonRecursive(
                    workspaceRoot.workspacePathFor(parent)));
          }
        }
      }
      objectName = "File";
    }

    return objectName;
  }
}
