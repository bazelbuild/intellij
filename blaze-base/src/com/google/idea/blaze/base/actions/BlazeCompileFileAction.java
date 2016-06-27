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
package com.google.idea.blaze.base.actions;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.google.idea.blaze.base.experiments.ExperimentScope;
import com.google.idea.blaze.base.metrics.Action;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.rulemaps.SourceToRuleMap;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.ScopedTask;
import com.google.idea.blaze.base.scope.scopes.*;
import com.google.idea.blaze.base.util.SaveUtil;
import com.google.idea.blaze.base.sync.aspects.BlazeIdeInterface;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

public class BlazeCompileFileAction extends BlazeAction {
  private static final Logger LOG = Logger.getInstance(BlazeCompileFileAction.class);

  public BlazeCompileFileAction() {
    super("Compile file");
  }

  @Override
  protected void doUpdate(@NotNull AnActionEvent e) {
    // IntelliJ uses different logic for 1 vs many module selection. When many modules are selected
    // modules with more than 1 content root are ignored
    // (ProjectViewImpl#moduleBySingleContentRoot).
    if (getTargets(e).isEmpty()) {
      Presentation presentation = e.getPresentation();
      presentation.setEnabled(false);
    }
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    if (project != null) {
      ImmutableCollection<Label> targets = getTargets(e);
      buildSourceFile(project, targets);
    }
  }

  private ImmutableCollection<Label> getTargets(AnActionEvent e) {
    Project project = e.getProject();
    VirtualFile virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
    if (project != null && virtualFile != null) {
      return SourceToRuleMap.getInstance(project).getTargetsForSourceFile(new File(virtualFile.getPath()));
    }
    return ImmutableList.of();
  }

  private static void buildSourceFile(
    @NotNull Project project,
    @NotNull ImmutableCollection<Label> targets) {
    BlazeProjectData blazeProjectData = BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (blazeProjectData == null || targets.isEmpty()) {
      return;
    }
    final ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
    if (projectViewSet == null) {
      return;
    }
    BlazeExecutor.submitTask(project, new ScopedTask() {
      @Override
      public void execute(@NotNull BlazeContext context) {
        context
          .push(new ExperimentScope())
          .push(new BlazeConsoleScope.Builder(project).build())
          .push(new IssuesScope(project))
          .push(new TimingScope("Make"))
          .push(new LoggedTimingScope(project, Action.MAKE_MODULE_TOTAL_TIME))
          .push(new NotificationScope(
            project,
            "Make",
            "Make module",
            "Make module completed successfully",
            "Make module failed"
          ))
        ;

        WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProject(project);

        SaveUtil.saveAllFiles();
        BlazeIdeInterface blazeIdeInterface = BlazeIdeInterface.getInstance();

        List<TargetExpression> targetExpressions = Lists.newArrayList(targets);
        blazeIdeInterface.resolveIdeArtifacts(project, context, workspaceRoot, projectViewSet, targetExpressions);
        LocalFileSystem.getInstance().refresh(true);
      }
    });
  }
}
