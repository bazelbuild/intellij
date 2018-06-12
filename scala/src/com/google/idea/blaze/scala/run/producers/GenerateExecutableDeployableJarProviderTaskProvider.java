/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.scala.run.producers;

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
import com.google.idea.blaze.base.console.BlazeConsoleLineProcessorProvider;
import com.google.idea.blaze.base.filecache.FileCaches;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.run.ExecutorType;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.ScopedTask;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.util.SaveUtil;
import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.BeforeRunTaskProvider;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import icons.BlazeIcons;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;
import javax.swing.Icon;

class GenerateExecutableDeployableJarProviderTaskProvider
    extends BeforeRunTaskProvider<GenerateExecutableDeployableJarProviderTaskProvider.Task> {
  private static final Key<GenerateExecutableDeployableJarProviderTaskProvider.Task> ID =
      Key.create("CreateTempScalaBinaryTarget");

  static class Task extends BeforeRunTask<Task> {
    Task() {
      super(ID);
      setEnabled(true);
    }
  }

  private final Project project;

  GenerateExecutableDeployableJarProviderTaskProvider(Project project) {
    this.project = project;
  }

  @Override
  public Key<Task> getId() {
    return ID;
  }

  @Override
  public Icon getIcon() {
    return BlazeIcons.Blaze;
  }

  @Override
  public Icon getTaskIcon(GenerateExecutableDeployableJarProviderTaskProvider.Task task) {
    return BlazeIcons.Blaze;
  }

  @Override
  public String getName() {
    return "Generate executable deployable JAR for custom target";
  }

  @Override
  public final boolean canExecuteTask(
      RunConfiguration configuration,
      GenerateExecutableDeployableJarProviderTaskProvider.Task task) {
    return isValidConfiguration(configuration);
  }

  private boolean isValidConfiguration(RunConfiguration runConfiguration) {
    return runConfiguration instanceof ApplicationConfiguration;
  }

  @Nullable
  @Override
  public Task createTask(RunConfiguration runConfiguration) {
    if (isValidConfiguration(runConfiguration)) {
      return new Task();
    }
    return null;
  }

  @Override
  public boolean executeTask(
      DataContext context, RunConfiguration configuration, ExecutionEnvironment env, Task task) {
    ApplicationConfiguration runConfiguration = (ApplicationConfiguration) configuration;

    Label target = runConfiguration.getUserData(DeployableJarRunConfigurationProducer.TARGET_LABEL);
    if (target == null) {
      return false;
    }

    return executeBuild(target, ExecutorType.fromExecutor(env.getExecutor()));
  }

  private boolean executeBuild(Label target, ExecutorType executorType) {
    return Scope.root(
        context -> {
          String binaryPath = Blaze.getBuildSystemProvider(project).getBinaryPath();
          ProjectViewSet projectViewSet =
              ProjectViewManager.getInstance(project).getProjectViewSet();
          WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProject(project);

          final ScopedTask<Void> buildTask =
              new ScopedTask<Void>(context) {
                @Override
                protected Void execute(BlazeContext context) {
                  try (BuildResultHelper buildResultHelper =
                      BuildResultHelper.forFiles(f -> true)) {
                    BlazeCommand command =
                        BlazeCommand.builder(binaryPath, BlazeCommandName.BUILD)
                            .addTargets(target.withTargetName(target.targetName() + "_deploy.jar"))
                            .addBlazeFlags(
                                BlazeFlags.blazeFlags(
                                    project,
                                    projectViewSet,
                                    BlazeCommandName.BUILD,
                                    BlazeInvocationContext.NonSync,
                                    executorType))
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
                    return null;
                  }
                }
              };

          ListenableFuture<Void> buildFuture =
              ProgressiveTaskWithProgressIndicator.builder(project)
                  .setTitle("Building deployable jar for " + target)
                  .submitTaskWithResult(buildTask);

          try {
            Futures.getChecked(buildFuture, ExecutionException.class);
          } catch (ExecutionException e) {
            context.setHasError();
          } catch (CancellationException e) {
            context.setCancelled();
          }
          return !context.hasErrors() && !context.isCancelled();
        });
  }
}
