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
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.ScopedTask;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.util.SaveUtil;
import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.BeforeRunTaskProvider;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.jar.JarApplicationConfiguration;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import icons.BlazeIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

public class GenerateExecutableDeployableJarProviderTaskProvider
  extends BeforeRunTaskProvider<GenerateExecutableDeployableJarProviderTaskProvider.Task> {
  public static final Key<GenerateExecutableDeployableJarProviderTaskProvider.Task> ID = Key.create("CreateTempScalaBinaryTarget");

  static class Task extends BeforeRunTask<Task> {
    Task() {
      super(ID);
      setEnabled(true);
    }
  }

  private final Project project;

  public GenerateExecutableDeployableJarProviderTaskProvider(Project project) {
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
  public final boolean canExecuteTask(RunConfiguration configuration, GenerateExecutableDeployableJarProviderTaskProvider.Task task) {
    return isValidConfiguration(configuration);
  }

  private boolean isValidConfiguration(RunConfiguration runConfiguration) {
    return runConfiguration instanceof JarApplicationConfiguration;
  }

  @Nullable
  @Override
  public Task createTask(@NotNull RunConfiguration runConfiguration) {
    if (isValidConfiguration(runConfiguration)) {
      return new Task();
    }
    return null;
  }

  @Override
  public boolean executeTask(
    DataContext context,
    @NotNull RunConfiguration configuration,
    @NotNull ExecutionEnvironment env,
    @NotNull Task task) {
    ApplicationConfiguration runConfiguration = (ApplicationConfiguration)configuration;

    Label target = runConfiguration.getUserData(DeployableJarRunConfigurationProducer.TARGET_LABEL);
    if (target == null) {
      return false;
    }

    return executeBuild(target);
  }

  private boolean executeBuild(Label target) {
    // TODO this is taken almost verbatim from BuildPluginBeforeRunTaskProvider
    // should be extracted to a util function.
    return Scope.root(
      context -> {
        String binaryPath = Blaze.getBuildSystemProvider(project).getBinaryPath();
        ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
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
                        BlazeInvocationContext.NonSync))
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

        if (context.hasErrors() || context.isCancelled()) {
          return false;
        }
        return true;
      });

  }
}
