/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.actions;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.Lists;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.google.idea.blaze.base.experiments.ExperimentScope;
import com.google.idea.blaze.base.filecache.FileCaches;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Label;
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
import com.google.idea.blaze.base.scope.scopes.NotificationScope;
import com.google.idea.blaze.base.scope.scopes.TimingScope;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.aspects.BlazeIdeInterface;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.util.SaveUtil;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import java.util.List;

/** Utility to build various collections of targets. */
public class BlazeBuildService {
  public static BlazeBuildService getInstance() {
    return ServiceManager.getService(BlazeBuildService.class);
  }

  public void buildFile(Project project, String fileName, ImmutableCollection<Label> targets) {
    if (project == null || !Blaze.isBlazeProject(project) || fileName == null) {
      return;
    }
    buildTargetExpressions(
        project,
        Lists.newArrayList(targets),
        ProjectViewManager.getInstance(project).getProjectViewSet(),
        new NotificationScope(
            project,
            "Make",
            "Make " + fileName,
            "Make " + fileName + " completed successfully",
            "Make " + fileName + " failed"));
  }

  public void buildProject(Project project) {
    if (project == null || !Blaze.isBlazeProject(project)) {
      return;
    }
    ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
    if (projectViewSet == null) {
      return;
    }
    buildTargetExpressions(
        project,
        projectViewSet.listItems(TargetSection.KEY),
        projectViewSet,
        new NotificationScope(
            project,
            "Make",
            "Make project",
            "Make project completed successfully",
            "Make project failed"));
  }

  @VisibleForTesting
  void buildTargetExpressions(
      Project project,
      List<TargetExpression> targets,
      ProjectViewSet projectViewSet,
      NotificationScope notificationScope) {
    if (targets.isEmpty() || projectViewSet == null) {
      return;
    }
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (blazeProjectData == null) {
      return;
    }
    BlazeExecutor.submitTask(
        project,
        new ScopedTask() {
          @Override
          public void execute(BlazeContext context) {
            context
                .push(new ExperimentScope())
                .push(new BlazeConsoleScope.Builder(project).build())
                .push(new IssuesScope(project))
                .push(new IdeaLogScope())
                .push(new TimingScope("Make"))
                .push(notificationScope);

            WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProject(project);
            BlazeIdeInterface blazeIdeInterface = BlazeIdeInterface.getInstance();

            SaveUtil.saveAllFiles();
            BlazeIdeInterface.BuildResult buildResult =
                blazeIdeInterface.compileIdeArtifacts(
                    project,
                    context,
                    workspaceRoot,
                    projectViewSet,
                    blazeProjectData.blazeVersionData,
                    targets);
            FileCaches.refresh(project);

            if (buildResult != BlazeIdeInterface.BuildResult.SUCCESS) {
              context.setHasError();
            }
          }
        });
  }
}
