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

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.async.executor.ProgressiveTaskWithProgressIndicator;
import com.google.idea.blaze.base.async.process.ExternalTask;
import com.google.idea.blaze.base.async.process.LineProcessingOutputStream;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.command.BlazeInvocationContext;
import com.google.idea.blaze.base.command.buildresult.BlazeArtifact;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper.GetArtifactsException;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelperProvider;
import com.google.idea.blaze.base.console.BlazeConsoleLineProcessorProvider;
import com.google.idea.blaze.base.issueparser.BlazeIssueParser;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.run.ExecutorType;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.ScopedTask;
import com.google.idea.blaze.base.scope.output.StatusOutput;
import com.google.idea.blaze.base.scope.scopes.ProblemsViewScope;
import com.google.idea.blaze.base.scope.scopes.ToolWindowScope;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.google.idea.blaze.base.sync.aspects.BuildResult;
import com.google.idea.blaze.base.util.SaveUtil;
import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.BeforeRunTaskProvider;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunCanceledByUserException;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.LocalFileSystem;
import icons.BlazeIcons;
import java.io.File;
import java.util.List;
import java.util.concurrent.CancellationException;
import javax.annotation.Nullable;
import javax.swing.Icon;

class GenerateDeployableJarTaskProvider
    extends BeforeRunTaskProvider<GenerateDeployableJarTaskProvider.Task> {
  private static final Key<GenerateDeployableJarTaskProvider.Task> ID =
      Key.create("GenerateDeployableJarTarget");

  static class Task extends BeforeRunTask<Task> {
    Task() {
      super(ID);
      setEnabled(true);
    }
  }

  private final Project project;

  GenerateDeployableJarTaskProvider(Project project) {
    this.project = project;
  }

  @Override
  public Key<Task> getId() {
    return ID;
  }

  @Override
  public Icon getIcon() {
    return BlazeIcons.Logo;
  }

  @Override
  public Icon getTaskIcon(GenerateDeployableJarTaskProvider.Task task) {
    return BlazeIcons.Logo;
  }

  @Override
  public String getName() {
    return "Generate executable deployable JAR for custom target";
  }

  @Override
  public final boolean canExecuteTask(
      RunConfiguration configuration, GenerateDeployableJarTaskProvider.Task task) {
    return isValidConfiguration(configuration);
  }

  private boolean isValidConfiguration(RunConfiguration config) {
    return Blaze.isBlazeProject(project) && getTarget(config) != null;
  }

  @Nullable
  @Override
  public Task createTask(RunConfiguration config) {
    if (isValidConfiguration(config)) {
      return new Task();
    }
    return null;
  }

  @Nullable
  private static Label getTarget(RunConfiguration config) {
    return config instanceof ApplicationConfiguration
        ? ((ApplicationConfiguration) config)
            .getUserData(DeployableJarRunConfigurationProducer.TARGET_LABEL)
        : null;
  }

  @Override
  public boolean executeTask(
      DataContext context, RunConfiguration configuration, ExecutionEnvironment env, Task task) {
    Label target = getTarget(configuration);
    if (target == null) {
      return false;
    }

    try {
      File outputJar = getDeployableJar(configuration, env, target);
      LocalFileSystem.getInstance().refreshIoFiles(ImmutableList.of(outputJar));
      ((ApplicationConfiguration) configuration).setVMParameters("-cp " + outputJar.getPath());
      return true;
    } catch (ExecutionException e) {
      ExecutionUtil.handleExecutionError(
          env.getProject(), env.getExecutor().getToolWindowId(), env.getRunProfile(), e);
    }
    return false;
  }

  /**
   * Builds a deployable jar for the given target, and returns the corresponding output artifact.
   *
   * @throws ExecutionException if the build failed, or the output artifact cannot be found.
   */
  private static File getDeployableJar(
      RunConfiguration configuration, ExecutionEnvironment env, Label target)
      throws ExecutionException {
    Project project = env.getProject();
    try (BuildResultHelper buildResultHelper =
        BuildResultHelperProvider.createForLocalBuild(project)) {

      SaveUtil.saveAllFiles();

      ListenableFuture<BuildResult> buildOperation =
          runBazelBuild(
              project,
              target,
              buildResultHelper,
              BlazeInvocationContext.runConfigContext(
                  ExecutorType.fromExecutor(env.getExecutor()), configuration.getType(), true));

      try {
        BuildResult result = buildOperation.get();
        if (result.status != BuildResult.Status.SUCCESS) {
          throw new ExecutionException("Bazel failure building deployable jar");
        }
      } catch (InterruptedException e) {
        cancelBuildFutureWithInterrupt(buildOperation);
        throw new RunCanceledByUserException();
      } catch (CancellationException unused) {
        buildOperation.cancel(/* mayInterruptIfRunning= */ false);
        throw new RunCanceledByUserException();
      } catch (java.util.concurrent.ExecutionException e) {
        throw new ExecutionException(e);
      }

      List<File> outputs =
          BlazeArtifact.getLocalFiles(
              buildResultHelper.getBuildArtifactsForTarget(
                  target.withTargetName(target.targetName() + "_deploy.jar"), file -> true));
      if (outputs.isEmpty()) {
        throw new ExecutionException(
            String.format("Failed to find deployable jar when building %s", target));
      }
      return outputs.get(0);
    } catch (GetArtifactsException e) {
      throw new ExecutionException(
          String.format(
              "Failed to find deployable jar when building %s: %s", target, e.getMessage()));
    }
  }

  @SuppressWarnings("Interruption") // propagating a cancellation-with-interrupt from another caller
  private static void cancelBuildFutureWithInterrupt(ListenableFuture<BuildResult> buildOperation) {
    buildOperation.cancel(/* mayInterruptIfRunning= */ true);
  }

  /** Kicks off the bazel build task, returning a corresponding {@link ListenableFuture}. */
  private static ListenableFuture<BuildResult> runBazelBuild(
      Project project,
      Label target,
      BuildResultHelper buildResultHelper,
      BlazeInvocationContext invocationContext) {

    String binaryPath = Blaze.getBuildSystemProvider(project).getBinaryPath(project);
    ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
    WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProject(project);

    String title = "Building deployable jar for " + target;
    return ProgressiveTaskWithProgressIndicator.builder(project, title)
        .submitTaskWithResult(
            new ScopedTask<BuildResult>() {
              @Override
              protected BuildResult execute(BlazeContext context) {
                context
                    .push(
                        new ToolWindowScope.Builder(
                                project,
                                new com.google.idea.blaze.base.toolwindow.Task(
                                    project,
                                    title,
                                    com.google.idea.blaze.base.toolwindow.Task.Type.DEPLOYABLE_JAR))
                            .setPopupBehavior(
                                BlazeUserSettings.getInstance().getShowBlazeConsoleOnRun())
                            .setIssueParsers(
                                BlazeIssueParser.defaultIssueParsers(
                                    project, workspaceRoot, invocationContext.type()))
                            .build())
                    .push(
                        new ProblemsViewScope(
                            project, BlazeUserSettings.getInstance().getShowProblemsViewOnRun()));

                context.output(new StatusOutput(title));
                BlazeCommand command =
                    BlazeCommand.builder(binaryPath, BlazeCommandName.BUILD)
                        .addTargets(target.withTargetName(target.targetName() + "_deploy.jar"))
                        .addBlazeFlags(
                            BlazeFlags.blazeFlags(
                                project,
                                projectViewSet,
                                BlazeCommandName.BUILD,
                                context,
                                invocationContext))
                        .addBlazeFlags(buildResultHelper.getBuildFlags())
                        .build();
                int exitCode =
                    ExternalTask.builder(workspaceRoot)
                        .addBlazeCommand(command)
                        .context(context)
                        .stderr(
                            LineProcessingOutputStream.of(
                                BlazeConsoleLineProcessorProvider.getAllStderrLineProcessors(
                                    context)))
                        .build()
                        .run();
                return BuildResult.fromExitCode(exitCode);
              }
            });
  }
}
