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
package com.google.idea.blaze.clwb.run;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.google.idea.blaze.base.async.process.ExternalTask;
import com.google.idea.blaze.base.async.process.LineProcessingOutputStream;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.command.ExperimentalShowArtifactsLineProcessor;
import com.google.idea.blaze.base.issueparser.IssueOutputLineProcessor;
import com.google.idea.blaze.base.metrics.Action;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationRunner;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.ScopedTask;
import com.google.idea.blaze.base.scope.output.StatusOutput;
import com.google.idea.blaze.base.scope.scopes.BlazeConsoleScope;
import com.google.idea.blaze.base.scope.scopes.IssuesScope;
import com.google.idea.blaze.base.scope.scopes.LoggedTimingScope;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.util.SaveUtil;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.jetbrains.cidr.execution.CidrCommandLineState;
import java.io.File;
import java.util.List;

/** CLion-specific handler for {@link BlazeCommandRunConfiguration}s. */
public class BlazeCidrRunConfigurationRunner implements BlazeCommandRunConfigurationRunner {

  private static final Logger LOG = Logger.getInstance(ExternalTask.class);

  private static final BoolExperiment FORCE_DEBUG_BUILD_FOR_DEBUGGING_TEST =
      new BoolExperiment("clwb.force.debug.build.for.debugging.test", true);

  private final BlazeCommandRunConfiguration configuration;

  /** Calculated during the before-run task, and made available to the debugger. */
  File executableToDebug = null;

  BlazeCidrRunConfigurationRunner(BlazeCommandRunConfiguration configuration) {
    this.configuration = configuration;
  }

  @Override
  public RunProfileState getRunProfileState(Executor executor, ExecutionEnvironment env) {
    return new CidrCommandLineState(env, new BlazeCidrLauncher(configuration, this, env));
  }

  @Override
  public boolean executeBeforeRunTask(ExecutionEnvironment environment) {
    if (!isDebugging(environment)) {
      return true;
    }
    try {
      File executable = getExecutableToDebug();
      if (executable != null) {
        executableToDebug = executable;
        return true;
      }
    } catch (ExecutionException e) {
      LOG.error(e.getMessage());
    }
    return false;
  }

  private static boolean isDebugging(ExecutionEnvironment environment) {
    Executor executor = environment.getExecutor();
    return executor instanceof DefaultDebugExecutor;
  }

  /**
   * Builds blaze C/C++ target in debug mode, and returns the output build artifact.
   *
   * @throws ExecutionException if no unique output artifact was found.
   */
  private File getExecutableToDebug() throws ExecutionException {
    final Project project = configuration.getProject();
    final BlazeCommandRunConfigurationCommonState handlerState =
        (BlazeCommandRunConfigurationCommonState) configuration.getHandler().getState();
    final WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProject(project);
    final ProjectViewSet projectViewSet =
        ProjectViewManager.getInstance(project).getProjectViewSet();

    final List<File> outputArtifacts = Lists.newArrayList();
    final ListenableFuture<Void> buildOperation =
        BlazeExecutor.submitTask(
            project,
            new ScopedTask() {
              @Override
              protected void execute(BlazeContext context) {
                context
                    .push(new LoggedTimingScope(project, Action.BLAZE_COMMAND_USAGE))
                    .push(new IssuesScope(project))
                    .push(new BlazeConsoleScope.Builder(project).build());

                context.output(new StatusOutput("Building debug binary"));

                BlazeCommand.Builder command =
                    BlazeCommand.builder(Blaze.getBuildSystem(project), BlazeCommandName.BUILD)
                        .addTargets(configuration.getTarget())
                        .addBlazeFlags(BlazeFlags.buildFlags(project, projectViewSet))
                        .addBlazeFlags(handlerState.getBlazeFlags());

                command.addBlazeFlags("--experimental_show_artifacts");

                // If we are trying to debug, make sure we are building in debug mode.
                // This can cause a rebuild, so it is a heavyweight setting.
                if (FORCE_DEBUG_BUILD_FOR_DEBUGGING_TEST.getValue()) {
                  command.addBlazeFlags("-c", "dbg");
                }

                ExternalTask.builder(workspaceRoot)
                    .addBlazeCommand(command.build())
                    .context(context)
                    .stderr(
                        LineProcessingOutputStream.of(
                            new ExperimentalShowArtifactsLineProcessor(outputArtifacts),
                            new IssueOutputLineProcessor(project, context, workspaceRoot)))
                    .build()
                    .run();
              }
            });

    try {
      SaveUtil.saveAllFiles();
      buildOperation.get();
    } catch (InterruptedException | java.util.concurrent.ExecutionException e) {
      throw new ExecutionException(e);
    }
    if (outputArtifacts.isEmpty()) {
      throw new ExecutionException(
          String.format("No output artifacts found when building %s", configuration.getTarget()));
    }
    if (outputArtifacts.size() > 1) {
      throw new ExecutionException(
          String.format(
              "More than 1 executable was produced when building %s; don't know which one to debug",
              configuration.getTarget()));
    }
    LocalFileSystem.getInstance().refreshIoFiles(outputArtifacts);
    return Iterables.getOnlyElement(outputArtifacts);
  }
}
