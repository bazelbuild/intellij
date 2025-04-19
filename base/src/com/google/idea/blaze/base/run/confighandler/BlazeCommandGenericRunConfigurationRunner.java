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
package com.google.idea.blaze.base.run.confighandler;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.bazel.BuildSystem.BuildInvoker;
import com.google.idea.blaze.base.buildview.BazelExecService;
import com.google.idea.blaze.base.buildview.BazelProcess;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.command.BlazeInvocationContext;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.ExecutorType;
import com.google.idea.blaze.base.run.smrunner.BlazeTestEventsHandler;
import com.google.idea.blaze.base.run.smrunner.BlazeTestUiSession;
import com.google.idea.blaze.base.run.smrunner.SmRunnerUtils;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.google.idea.blaze.base.run.testlogs.BlazeTestResultFinderStrategy;
import com.google.idea.blaze.base.run.testlogs.BlazeTestResultHolder;
import com.google.idea.blaze.base.run.testlogs.BlazeTestResults;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.CommandLineState;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.filters.TextConsoleBuilderImpl;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Generic runner for {@link BlazeCommandRunConfiguration}s, used as a fallback in the case where no other runners are
 * more relevant.
 */
public final class BlazeCommandGenericRunConfigurationRunner
    implements BlazeCommandRunConfigurationRunner {

  @Override
  public RunProfileState getRunProfileState(Executor executor, ExecutionEnvironment environment) {
    return new BlazeCommandRunProfileState(environment);
  }

  @Override
  public boolean executeBeforeRunTask(ExecutionEnvironment environment) {
    // Don't execute any tasks.
    return true;
  }

  /**
   * {@link RunProfileState} for generic blaze commands.
   */
  public static class BlazeCommandRunProfileState extends CommandLineState {

    private static final int BLAZE_BUILD_INTERRUPTED = 8;
    private final BlazeCommandRunConfiguration configuration;
    private final BlazeCommandRunConfigurationCommonState handlerState;
    private BlazeTestResultFinderStrategy testHolder = null;

    public BlazeCommandRunProfileState(ExecutionEnvironment environment) {
      super(environment);
      this.configuration = getConfiguration(environment);
      this.handlerState =
          (BlazeCommandRunConfigurationCommonState) configuration.getHandler().getState();
    }

    private static BlazeCommandRunConfiguration getConfiguration(ExecutionEnvironment environment) {
      return BlazeCommandRunConfigurationRunner.getConfiguration(environment);
    }

    @Override
    public ExecutionResult execute(Executor executor, ProgramRunner<?> runner)
        throws ExecutionException {
      DefaultExecutionResult result = (DefaultExecutionResult) super.execute(executor, runner);
      return SmRunnerUtils.attachRerunFailedTestsAction(result);
    }

    @Override
    protected @Nullable ConsoleView createConsole(@NotNull Executor executor) throws ExecutionException {
      if (isTest()) {
        Project project = configuration.getProject();
        BlazeTestUiSession testUiSession = null;
        if (BlazeTestEventsHandler.targetsSupported(project, configuration.getTargets())) {
          testUiSession =
              BlazeTestUiSession.create(
                  ImmutableList.<String>builder()
                      .add("--runs_per_test=1")
                      .add("--flaky_test_attempts=1")
                      .build(),
                  testHolder);
        }
        if (testUiSession != null) {
          ConsoleView consoleView =
              SmRunnerUtils.getConsoleView(
                  project, configuration, getEnvironment().getExecutor(), testUiSession);
          setConsoleBuilder(
              new TextConsoleBuilderImpl(project) {
                @Override
                protected ConsoleView createConsole() {
                  return consoleView;
                }
              });
          return consoleView;
        } else {
          return super.createConsole(executor);
        }
      } else {
        return super.createConsole(executor);
      }
    }

    @Override
    protected ProcessHandler startProcess() throws ExecutionException {
      Project project = configuration.getProject();
      BlazeImportSettings importSettings =
          BlazeImportSettingsManager.getInstance(project).getImportSettings();
      assert importSettings != null;

      ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
      assert projectViewSet != null;
      BlazeContext context = BlazeContext.create();
      BuildInvoker invoker =
          Blaze.getBuildSystemProvider(project).getBuildSystem().getBuildInvoker(project);
      BlazeCommand.Builder blazeCommand =
          getBlazeCommand(
              project,
              ExecutorType.fromExecutor(getEnvironment().getExecutor()),
              invoker,
              ImmutableList.of(),
              context);
      return isTest()
          ? getProcessHandlerForTests(project, invoker, blazeCommand, context)
          : getProcessHandlerForNonTests(project, blazeCommand, context);
    }

    private ProcessHandler getProcessHandlerForNonTests(
        Project project,
        BlazeCommand.Builder blazeCommandBuilder,
        BlazeContext context)
        throws ExecutionException {
      try {
        return BazelExecService.instance(project).run(context, blazeCommandBuilder).getHdl();
      } catch (IOException e) {
        throw new ExecutionException(e);
      }
    }

    private ProcessHandler getProcessHandlerForTests(
        Project project,
        BuildInvoker invoker,
        BlazeCommand.Builder blazeCommandBuilder,
        BlazeContext context) {
      testHolder = new BlazeTestResultHolder();
      @NotNull Map<String, String> envVars = handlerState.getUserEnvVarsState().getData().getEnvs();

      if (invoker.getCommandRunner().canUseCli()) {
        // If we can use the CLI, that means we will run through Bazel (as opposed to a raw process handler)
        // When running `bazel test`, bazel will not forward the environment to the tests themselves -- we need to use
        // the --test_env flag for that. Therefore, we convert all the env vars to --test_env flags here.
        for (Map.Entry<String, String> env : envVars.entrySet()) {
          blazeCommandBuilder.addBlazeFlags(BlazeFlags.TEST_ENV, String.format("%s=%s", env.getKey(), env.getValue()));
        }
      }
      return getCommandRunnerProcessHandlerForTests(
          project, blazeCommandBuilder, testHolder, context);
    }

    private ProcessHandler getCommandRunnerProcessHandlerForTests(
        Project project,
        BlazeCommand.Builder blazeCommandBuilder,
        BlazeTestResultFinderStrategy testResultFinderStrategy,
        BlazeContext context) {
      final BazelProcess<BlazeTestResults> process;
      try {
        process = BazelExecService.instance(project).test(context, blazeCommandBuilder, testResultFinderStrategy);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      return process.getHdl();
    }

    private BlazeCommand.Builder getBlazeCommand(
        Project project,
        ExecutorType executorType,
        BuildInvoker invoker,
        ImmutableList<String> testHandlerFlags,
        BlazeContext context) {
      ProjectViewSet projectViewSet =
          Preconditions.checkNotNull(ProjectViewManager.getInstance(project).getProjectViewSet());

      List<String> extraBlazeFlags = new ArrayList<>(testHandlerFlags);
      BlazeCommandName command = getCommand();
      if (executorType == ExecutorType.COVERAGE) {
        command = BlazeCommandName.COVERAGE;
      }

      return BlazeCommand.builder(invoker, command, project)
          .addTargets(configuration.getTargets())
          .addBlazeFlags(
              BlazeFlags.blazeFlags(
                  project,
                  projectViewSet,
                  getCommand(),
                  context,
                  BlazeInvocationContext.runConfigContext(
                      executorType, configuration.getType(), false)))
          .addBlazeFlags(extraBlazeFlags)
          .addBlazeFlags(handlerState.getBlazeFlagsState().getFlagsForExternalProcesses())
          .addExeFlags(handlerState.getExeFlagsState().getFlagsForExternalProcesses());
    }

    private BlazeCommandName getCommand() {
      return handlerState.getCommandState().getCommand();
    }

    private boolean isTest() {
      return BlazeCommandName.TEST.equals(getCommand());
    }
  }
}
