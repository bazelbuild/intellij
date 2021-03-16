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
package com.google.idea.blaze.base.build;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.Lists;
import com.google.idea.blaze.base.async.executor.ProgressiveTaskWithProgressIndicator;
import com.google.idea.blaze.base.command.BlazeInvocationContext;
import com.google.idea.blaze.base.experiments.ExperimentScope;
import com.google.idea.blaze.base.filecache.FileCaches;
import com.google.idea.blaze.base.issueparser.IssueOutputFilter;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.ScopedFunction;
import com.google.idea.blaze.base.scope.ScopedTask;
import com.google.idea.blaze.base.scope.scopes.BlazeConsoleScope;
import com.google.idea.blaze.base.scope.scopes.IdeaLogScope;
import com.google.idea.blaze.base.scope.scopes.NotificationScope;
import com.google.idea.blaze.base.scope.scopes.ProblemsViewScope;
import com.google.idea.blaze.base.scope.scopes.TimingScope;
import com.google.idea.blaze.base.scope.scopes.TimingScope.EventType;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.google.idea.blaze.base.settings.BlazeUserSettings.FocusBehavior;
import com.google.idea.blaze.base.sync.BlazeBuildParams;
import com.google.idea.blaze.base.sync.SyncProjectTargetsHelper;
import com.google.idea.blaze.base.sync.SyncScope.SyncCanceledException;
import com.google.idea.blaze.base.sync.SyncScope.SyncFailedException;
import com.google.idea.blaze.base.sync.aspects.BlazeIdeInterface;
import com.google.idea.blaze.base.sync.aspects.BuildResult;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.sharding.BlazeBuildTargetSharder;
import com.google.idea.blaze.base.sync.sharding.BlazeBuildTargetSharder.ShardedTargetsResult;
import com.google.idea.blaze.base.util.SaveUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import java.util.List;
import java.util.concurrent.Future;

/** Utility to build various collections of targets. */
public class BlazeBuildService {
  private static final Key<Long> PROJECT_LAST_BUILD_TIMESTAMP_KEY =
      Key.create("blaze.project.last.build.timestamp");

  public static BlazeBuildService getInstance(Project project) {
    return ServiceManager.getService(project, BlazeBuildService.class);
  }

  public static Long getLastBuildTimeStamp(Project project) {
    return project.getUserData(PROJECT_LAST_BUILD_TIMESTAMP_KEY);
  }

  private final Project project;

  public BlazeBuildService(Project project) {
    this.project = project;
  }

  public void buildFile(String fileName, ImmutableCollection<Label> targets) {
    if (!Blaze.isBlazeProject(project) || fileName == null) {
      return;
    }
    ProjectViewSet projectView = ProjectViewManager.getInstance(project).getProjectViewSet();
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (projectView == null || projectData == null) {
      return;
    }
    buildTargetExpressions(
        project,
        projectView,
        projectData,
        context -> Lists.newArrayList(targets),
        new NotificationScope(
            project,
            "Make",
            "Make " + fileName,
            "Make " + fileName + " completed successfully",
            "Make " + fileName + " failed"));
  }

  public void buildProject() {
    if (!Blaze.isBlazeProject(project)) {
      return;
    }
    ProjectViewSet projectView = ProjectViewManager.getInstance(project).getProjectViewSet();
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (projectView == null || projectData == null) {
      return;
    }

    ScopedFunction<List<TargetExpression>> targets =
        context -> {
          try {
            return SyncProjectTargetsHelper.getProjectTargets(
                    project,
                    context,
                    projectView,
                    projectData.getWorkspacePathResolver(),
                    projectData.getWorkspaceLanguageSettings())
                .getTargetsToSync();
          } catch (SyncCanceledException e) {
            context.setCancelled();
            return null;
          } catch (SyncFailedException e) {
            context.setHasError();
            return null;
          }
        };

    buildTargetExpressions(
        project,
        projectView,
        projectData,
        targets,
        new NotificationScope(
            project,
            "Make",
            "Make project",
            "Make project completed successfully",
            "Make project failed"));

    // In case the user touched a file, but didn't change its content. The user will get a false
    // positive for class file out of date. We need a way for the user to suppress the false
    // message. Clicking the "build project" link should at least make the message go away.
    project.putUserData(PROJECT_LAST_BUILD_TIMESTAMP_KEY, System.currentTimeMillis());
  }

  private static void buildTargetExpressions(
      Project project,
      ProjectViewSet projectView,
      BlazeProjectData projectData,
      ScopedFunction<List<TargetExpression>> targetsFunction,
      NotificationScope notificationScope) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      // a gross hack to avoid breaking change detector tests. We had a few tests which relied on
      // this never being called *and* relied on PROJECT_LAST_BUILD_TIMESTAMP_KEY being set
      return;
    }
    FocusBehavior problemsViewFocus = BlazeUserSettings.getInstance().getShowProblemsViewOnRun();
    @SuppressWarnings("unused") // go/futurereturn-lsc
    Future<?> possiblyIgnoredError =
        ProgressiveTaskWithProgressIndicator.builder(project, "Building targets")
            .submitTaskWithResult(
                new ScopedTask<Void>() {
                  @Override
                  public Void execute(BlazeContext context) {
                    context
                        .push(new ExperimentScope())
                        .push(
                            new BlazeConsoleScope.Builder(project)
                                .addConsoleFilters(
                                    new IssueOutputFilter(
                                        project,
                                        WorkspaceRoot.fromProject(project),
                                        BlazeInvocationContext.ContextType.Sync,
                                        true))
                                .build())
                        .push(new ProblemsViewScope(project, problemsViewFocus))
                        .push(new IdeaLogScope())
                        .push(new TimingScope("Make", EventType.BlazeInvocation))
                        .push(notificationScope);

                    List<TargetExpression> targets = targetsFunction.execute(context);
                    if (targets == null) {
                      return null;
                    }

                    BlazeBuildParams buildParams = BlazeBuildParams.fromProject(project);
                    WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProject(project);

                    SaveUtil.saveAllFiles();
                    BlazeBuildListener.EP_NAME.extensions().forEach(e -> e.buildStarting(project));

                    ShardedTargetsResult shardedTargets =
                        BlazeBuildTargetSharder.expandAndShardTargets(
                            project,
                            context,
                            workspaceRoot,
                            buildParams,
                            projectView,
                            projectData.getWorkspacePathResolver(),
                            targets);
                    if (shardedTargets.buildResult.status == BuildResult.Status.FATAL_ERROR) {
                      return null;
                    }
                    BuildResult buildResult =
                        BlazeIdeInterface.getInstance()
                            .compileIdeArtifacts(
                                project,
                                context,
                                workspaceRoot,
                                buildParams,
                                projectView,
                                projectData.getBlazeVersionData(),
                                projectData.getWorkspaceLanguageSettings(),
                                shardedTargets.shardedTargets);
                    FileCaches.refresh(project, context);

                    if (buildResult.status != BuildResult.Status.SUCCESS) {
                      context.setHasError();
                    }
                    BlazeBuildListener.EP_NAME
                        .extensions()
                        .forEach(ep -> ep.buildCompleted(project, buildResult));
                    return null;
                  }
                });
  }
}
