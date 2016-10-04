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

import com.google.common.collect.Lists;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.google.idea.blaze.base.experiments.ExperimentScope;
import com.google.idea.blaze.base.filecache.FileCaches;
import com.google.idea.blaze.base.metrics.Action;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.sections.TargetSection;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.ScopedTask;
import com.google.idea.blaze.base.scope.scopes.BlazeConsoleScope;
import com.google.idea.blaze.base.scope.scopes.IdeaLogScope;
import com.google.idea.blaze.base.scope.scopes.IssuesScope;
import com.google.idea.blaze.base.scope.scopes.LoggedTimingScope;
import com.google.idea.blaze.base.scope.scopes.NotificationScope;
import com.google.idea.blaze.base.scope.scopes.TimingScope;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.aspects.BlazeIdeInterface;
import com.google.idea.blaze.base.sync.aspects.BlazeIdeInterface.BuildResult;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.util.SaveUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import java.util.List;
import org.jetbrains.annotations.NotNull;

class BlazeMakeProjectAction extends BlazeAction {

  public BlazeMakeProjectAction() {
    super("Make Project");
  }

  @Override
  public final void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    if (project != null && Blaze.isBlazeProject(project)) {
      buildBlazeProject(project);
    }
  }

  protected void buildBlazeProject(@NotNull final Project project) {

    BlazeExecutor.submitTask(
        project,
        new ScopedTask() {
          @Override
          public void execute(@NotNull BlazeContext context) {
            context
                .push(new ExperimentScope())
                .push(new BlazeConsoleScope.Builder(project).build())
                .push(new IssuesScope(project))
                .push(new IdeaLogScope())
                .push(new TimingScope("Make"))
                .push(new LoggedTimingScope(project, Action.MAKE_PROJECT_TOTAL_TIME))
                .push(
                    new NotificationScope(
                        project,
                        "Make",
                        "Make project",
                        "Make project completed successfully",
                        "Make project failed"));

            BlazeProjectData blazeProjectData =
                BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
            if (blazeProjectData == null) {
              return;
            }
            SaveUtil.saveAllFiles();

            ProjectViewSet projectViewSet =
                ProjectViewManager.getInstance(project)
                    .reloadProjectView(context, blazeProjectData.workspacePathResolver);
            if (projectViewSet == null) {
              return;
            }

            WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProject(project);

            List<TargetExpression> targets = Lists.newArrayList();
            targets.addAll(projectViewSet.listItems(TargetSection.KEY));

            BlazeIdeInterface blazeIdeInterface = BlazeIdeInterface.getInstance();

            BuildResult buildResult =
                blazeIdeInterface.resolveIdeArtifacts(
                    project, context, workspaceRoot, projectViewSet, targets);
            FileCaches.refresh(project);

            if (buildResult != BuildResult.SUCCESS) {
              context.setHasError();
              ;
            }
          }
        });
  }
}
