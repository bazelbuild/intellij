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
package com.google.idea.blaze.base.run.confighandler;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.async.process.LineProcessingOutputStream;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.issueparser.IssueOutputLineProcessor;
import com.google.idea.blaze.base.metrics.Action;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.DistributedExecutorSupport;
import com.google.idea.blaze.base.run.processhandler.LineProcessingProcessAdapter;
import com.google.idea.blaze.base.run.processhandler.ScopedBlazeProcessHandler;
import com.google.idea.blaze.base.run.smrunner.BlazeTestEventsHandler;
import com.google.idea.blaze.base.run.smrunner.BlazeTestEventsHandlerProvider;
import com.google.idea.blaze.base.run.smrunner.SmRunnerUtils;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.scopes.IdeaLogScope;
import com.google.idea.blaze.base.scope.scopes.IssuesScope;
import com.google.idea.blaze.base.scope.scopes.LoggedTimingScope;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.CommandLineState;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.WrappingRunConfiguration;
import com.intellij.execution.filters.TextConsoleBuilderImpl;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.project.Project;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * Generic runner for {@link BlazeCommandRunConfiguration}s, used as a fallback in the case where no
 * other runners are more relevant.
 */
public final class BlazeCommandGenericRunConfigurationRunner
    implements BlazeCommandRunConfigurationRunner {

  @Override
  public RunProfileState getRunProfileState(Executor executor, ExecutionEnvironment environment) {
    return new BlazeCommandRunProfileState(environment, null);
  }

  @Override
  public boolean executeBeforeRunTask(ExecutionEnvironment environment) {
    // Don't execute any tasks.
    return true;
  }

  /** {@link RunProfileState} for generic blaze commands. */
  public static class BlazeCommandRunProfileState extends CommandLineState {
    private final BlazeCommandRunConfiguration configuration;
    private final BlazeCommandRunConfigurationCommonState handlerState;
    @Nullable private final BlazeTestEventsHandlerProvider testEventsHandlerProvider;

    public BlazeCommandRunProfileState(
        ExecutionEnvironment environment,
        @Nullable BlazeTestEventsHandlerProvider testEventsHandlerProvider) {
      super(environment);
      this.configuration = getConfiguration(environment);
      this.handlerState =
          (BlazeCommandRunConfigurationCommonState) configuration.getHandler().getState();
      this.testEventsHandlerProvider = testEventsHandlerProvider;
    }

    private static BlazeCommandRunConfiguration getConfiguration(ExecutionEnvironment environment) {
      RunProfile runProfile = environment.getRunProfile();
      if (runProfile instanceof WrappingRunConfiguration) {
        runProfile = ((WrappingRunConfiguration) runProfile).getPeer();
      }
      return (BlazeCommandRunConfiguration) runProfile;
    }

    @Override
    public ExecutionResult execute(Executor executor, ProgramRunner runner)
        throws ExecutionException {
      DefaultExecutionResult result = (DefaultExecutionResult) super.execute(executor, runner);
      return SmRunnerUtils.attachRerunFailedTestsAction(result);
    }

    @Override
    @NotNull
    protected ProcessHandler startProcess() throws ExecutionException {
      Project project = configuration.getProject();
      BlazeImportSettings importSettings =
          BlazeImportSettingsManager.getInstance(project).getImportSettings();
      assert importSettings != null;

      ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
      assert projectViewSet != null;

      ImmutableList<String> testHandlerFlags = ImmutableList.of();
      BlazeTestEventsHandler testEventsHandler =
          canUseTestUi() && testEventsHandlerProvider != null
              ? testEventsHandlerProvider.getHandler()
              : null;
      if (testEventsHandler != null) {
        testHandlerFlags = testEventsHandler.getBlazeFlags();
        setConsoleBuilder(
            new TextConsoleBuilderImpl(project) {
              @Override
              protected ConsoleView createConsole() {
                return SmRunnerUtils.getConsoleView(
                    project, configuration, getEnvironment().getExecutor(), testEventsHandler);
              }
            });
      }

      BlazeCommand blazeCommand = getBlazeCommand(project, testHandlerFlags);

      WorkspaceRoot workspaceRoot = WorkspaceRoot.fromImportSettings(importSettings);
      return new ScopedBlazeProcessHandler(
          project,
          blazeCommand,
          workspaceRoot,
          new ScopedBlazeProcessHandler.ScopedProcessHandlerDelegate() {
            @Override
            public void onBlazeContextStart(BlazeContext context) {
              context
                  .push(new LoggedTimingScope(project, Action.BLAZE_COMMAND_USAGE))
                  .push(new IssuesScope(project))
                  .push(new IdeaLogScope());
            }

            @Override
            public ImmutableList<ProcessListener> createProcessListeners(BlazeContext context) {
              LineProcessingOutputStream outputStream =
                  LineProcessingOutputStream.of(
                      new IssueOutputLineProcessor(project, context, workspaceRoot));
              return ImmutableList.of(new LineProcessingProcessAdapter(outputStream));
            }
          });
    }

    private BlazeCommand getBlazeCommand(Project project, ImmutableList<String> testHandlerFlags) {
      ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
      assert projectViewSet != null;

      return BlazeCommand.builder(Blaze.getBuildSystem(project), handlerState.getCommand())
          .setBlazeBinary(handlerState.getBlazeBinary())
          .addTargets(configuration.getTarget())
          .addBlazeFlags(BlazeFlags.buildFlags(project, projectViewSet))
          .addBlazeFlags(testHandlerFlags)
          .addBlazeFlags(handlerState.getBlazeFlags())
          .addBlazeFlags(
              DistributedExecutorSupport.getBlazeFlags(
                  project, handlerState.getRunOnDistributedExecutor()))
          .addExeFlags(handlerState.getExeFlags())
          .build();
    }

    private boolean canUseTestUi() {
      return testEventsHandlerProvider != null
          && BlazeCommandName.TEST.equals(handlerState.getCommand())
          && !Boolean.TRUE.equals(handlerState.getRunOnDistributedExecutor());
    }
  }
}
