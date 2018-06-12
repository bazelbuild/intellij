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
package com.google.idea.blaze.plugin.run;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.async.executor.ProgressiveTaskWithProgressIndicator;
import com.google.idea.blaze.base.async.process.ExternalTask;
import com.google.idea.blaze.base.async.process.LineProcessingOutputStream;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.command.BlazeInvocationContext;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.command.info.BlazeInfoRunner;
import com.google.idea.blaze.base.console.BlazeConsoleLineProcessorProvider;
import com.google.idea.blaze.base.experiments.ExperimentScope;
import com.google.idea.blaze.base.filecache.FileCaches;
import com.google.idea.blaze.base.issueparser.IssueOutputFilter;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.run.ExecutorType;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.ScopedTask;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.scopes.BlazeConsoleScope;
import com.google.idea.blaze.base.scope.scopes.IdeaLogScope;
import com.google.idea.blaze.base.scope.scopes.IssuesScope;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.google.idea.blaze.base.settings.BlazeUserSettings.BlazeConsolePopupBehavior;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.util.SaveUtil;
import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.BeforeRunTaskProvider;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import icons.BlazeIcons;
import java.io.File;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;
import javax.swing.Icon;

/**
 * Builds the intellij_plugin jar via 'blaze build', for Blaze Intellij Plugin run configurations
 */
public final class BuildPluginBeforeRunTaskProvider
    extends BeforeRunTaskProvider<BuildPluginBeforeRunTaskProvider.Task> {
  public static final Key<Task> ID = Key.create("Blaze.Intellij.Plugin.BeforeRunTask");

  static class Task extends BeforeRunTask<Task> {
    private Task() {
      super(ID);
      setEnabled(true);
    }
  }

  private final Project project;

  public BuildPluginBeforeRunTaskProvider(Project project) {
    this.project = project;
  }

  @Override
  public Icon getIcon() {
    return BlazeIcons.Blaze;
  }

  @Override
  public Icon getTaskIcon(Task task) {
    return BlazeIcons.Blaze;
  }

  @Override
  public boolean isConfigurable() {
    return false;
  }

  @Override
  public boolean configureTask(RunConfiguration runConfiguration, Task task) {
    return false;
  }

  @Override
  public Key<Task> getId() {
    return ID;
  }

  @Override
  public String getName() {
    return taskName();
  }

  @Override
  public String getDescription(Task task) {
    return taskName();
  }

  private String taskName() {
    return Blaze.buildSystemName(project) + " build plugin before-run task";
  }

  @Override
  public final boolean canExecuteTask(RunConfiguration configuration, Task task) {
    return isValidConfiguration(configuration);
  }

  @Nullable
  @Override
  public Task createTask(RunConfiguration runConfiguration) {
    if (isValidConfiguration(runConfiguration)) {
      return new Task();
    }
    return null;
  }

  private static boolean isValidConfiguration(RunConfiguration runConfiguration) {
    return runConfiguration instanceof BlazeIntellijPluginConfiguration;
  }

  @Override
  public final boolean executeTask(
      final DataContext dataContext,
      final RunConfiguration configuration,
      final ExecutionEnvironment env,
      Task task) {
    if (!canExecuteTask(configuration, task)) {
      return false;
    }
    BlazeConsolePopupBehavior consolePopupBehavior =
        BlazeUserSettings.getInstance().getSuppressConsoleForRunAction()
            ? BlazeConsolePopupBehavior.NEVER
            : BlazeConsolePopupBehavior.ALWAYS;
    return Scope.root(
        context -> {
          WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProject(project);
          context
              .push(new ExperimentScope())
              .push(new IssuesScope(project, true))
              .push(
                  new BlazeConsoleScope.Builder(project)
                      .setPopupBehavior(consolePopupBehavior)
                      .addConsoleFilters(
                          new IssueOutputFilter(
                              project, workspaceRoot, BlazeInvocationContext.NonSync, true))
                      .build())
              .push(new IdeaLogScope());

          BlazeIntellijPluginDeployer deployer =
              env.getUserData(BlazeIntellijPluginDeployer.USER_DATA_KEY);
          if (deployer == null) {
            IssueOutput.error("Could not find BlazeIntellijPluginDeployer in env.").submit(context);
            return false;
          }
          deployer.buildStarted();

          final ProjectViewSet projectViewSet =
              ProjectViewManager.getInstance(project).getProjectViewSet();
          if (projectViewSet == null) {
            IssueOutput.error("Could not load project view. Please resync project").submit(context);
            return false;
          }

          final ScopedTask<Void> buildTask =
              new ScopedTask<Void>(context) {
                @Override
                protected Void execute(BlazeContext context) {
                  String binaryPath = Blaze.getBuildSystemProvider(project).getBinaryPath();
                  BlazeIntellijPluginConfiguration config =
                      (BlazeIntellijPluginConfiguration) configuration;

                  ListenableFuture<String> executionRootFuture =
                      BlazeInfoRunner.getInstance()
                          .runBlazeInfo(
                              context,
                              binaryPath,
                              workspaceRoot,
                              config.getBlazeFlagsState().getExpandedFlags(),
                              BlazeInfo.EXECUTION_ROOT_KEY);

                  String executionRoot;
                  try {
                    executionRoot = executionRootFuture.get();
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    context.setCancelled();
                    return null;
                  } catch (ExecutionException e) {
                    IssueOutput.error(e.getMessage()).submit(context);
                    context.setHasError();
                    return null;
                  }
                  if (executionRoot == null) {
                    IssueOutput.error("Could not determine execution root").submit(context);
                    return null;
                  }
                  BlazeProjectData blazeProjectData =
                      BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
                  if (blazeProjectData == null) {
                    IssueOutput.error("Could not determine execution root").submit(context);
                    return null;
                  }

                  try (BuildResultHelper buildResultHelper =
                      BuildResultHelper.forFiles(f -> true)) {
                    BlazeCommand command =
                        BlazeCommand.builder(binaryPath, BlazeCommandName.BUILD)
                            .addTargets(config.getTarget())
                            .addBlazeFlags(
                                BlazeFlags.blazeFlags(
                                    project,
                                    projectViewSet,
                                    BlazeCommandName.BUILD,
                                    BlazeInvocationContext.NonSync,
                                    ExecutorType.fromExecutor(env.getExecutor())))
                            .addBlazeFlags(config.getBlazeFlagsState().getExpandedFlags())
                            .addExeFlags(config.getExeFlagsState().getExpandedFlags())
                            .addBlazeFlags(buildResultHelper.getBuildFlags())
                            .build();
                    if (command == null || context.hasErrors() || context.isCancelled()) {
                      return null;
                    }
                    SaveUtil.saveAllFiles();
                    int retVal =
                        ExternalTask.builder(workspaceRoot)
                            .addBlazeCommand(command)
                            .context(context)
                            .stderr(
                                LineProcessingOutputStream.of(
                                    BlazeConsoleLineProcessorProvider.getAllStderrLineProcessors(
                                        context)))
                            .build()
                            .run();
                    if (retVal != 0) {
                      context.setHasError();
                    }
                    FileCaches.refresh(project);
                    deployer.reportBuildComplete(new File(executionRoot), buildResultHelper);
                    return null;
                  }
                }
              };

          ListenableFuture<Void> buildFuture =
              ProgressiveTaskWithProgressIndicator.builder(project)
                  .setTitle("Executing blaze build for IntelliJ plugin jar")
                  .submitTaskWithResult(buildTask);

          try {
            Futures.getChecked(buildFuture, ExecutionException.class);
          } catch (ExecutionException e) {
            context.setHasError();
          } catch (CancellationException e) {
            context.setCancelled();
          }

          if (context.hasErrors() || context.isCancelled()) {
            return false;
          }
          return true;
        });
  }
}
