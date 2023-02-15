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

import static com.google.common.base.Verify.verify;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.google.idea.blaze.base.async.process.LineProcessingOutputStream;
import com.google.idea.blaze.base.bazel.BuildSystem.BuildInvoker;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeCommandRunnerExperiments;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.command.BlazeInvocationContext;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.console.BlazeConsoleLineProcessorProvider;
import com.google.idea.blaze.base.issueparser.ToolWindowTaskIssueOutputFilter;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.run.BlazeBeforeRunCommandHelper;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.ExecutorType;
import com.google.idea.blaze.base.run.processhandler.LineProcessingProcessAdapter;
import com.google.idea.blaze.base.run.processhandler.ScopedBlazeProcessHandler;
import com.google.idea.blaze.base.run.smrunner.BlazeTestEventsHandler;
import com.google.idea.blaze.base.run.smrunner.BlazeTestUiSession;
import com.google.idea.blaze.base.run.smrunner.SmRunnerUtils;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.google.idea.blaze.base.run.testlogs.BlazeTestResult;
import com.google.idea.blaze.base.run.testlogs.BlazeTestResult.TestStatus;
import com.google.idea.blaze.base.run.testlogs.BlazeTestResultFinderStrategy;
import com.google.idea.blaze.base.run.testlogs.BlazeTestResultHolder;
import com.google.idea.blaze.base.run.testlogs.BlazeTestResults;
import com.google.idea.blaze.base.run.testlogs.LocalBuildEventProtocolTestFinderStrategy;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.OutputSink;
import com.google.idea.blaze.base.scope.scopes.IdeaLogScope;
import com.google.idea.blaze.base.scope.scopes.ProblemsViewScope;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.common.PrintOutput.OutputType;
import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.CommandLineState;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.TextConsoleBuilderImpl;
import com.intellij.execution.filters.UrlFilter;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Generic runner for {@link BlazeCommandRunConfiguration}s, used as a fallback in the case where no
 * other runners are more relevant.
 */
public final class BlazeCommandGenericRunConfigurationRunner
    implements BlazeCommandRunConfigurationRunner {

  private static final Logger logger = Logger.getInstance(BlazeCommandGenericRunConfigurationRunner.class);

  @Override
  public RunProfileState getRunProfileState(Executor executor, ExecutionEnvironment environment) {
    return new BlazeCommandRunProfileState(environment);
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
    private final ImmutableList<Filter> consoleFilters;

    public BlazeCommandRunProfileState(ExecutionEnvironment environment) {
      super(environment);
      this.configuration = getConfiguration(environment);
      this.handlerState =
          (BlazeCommandRunConfigurationCommonState) configuration.getHandler().getState();
      Project project = environment.getProject();
      this.consoleFilters =
          ImmutableList.of(
              new UrlFilter(),
              ToolWindowTaskIssueOutputFilter.createWithDefaultParsers(
                  project,
                  WorkspaceRoot.fromProject(project),
                  BlazeInvocationContext.ContextType.RunConfiguration));
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
    protected ProcessHandler startProcess() throws ExecutionException {
      Project project = configuration.getProject();
      BlazeImportSettings importSettings =
          BlazeImportSettingsManager.getInstance(project).getImportSettings();
      assert importSettings != null;

      ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
      assert projectViewSet != null;
      BlazeContext context = BlazeContext.create();
      BuildInvoker invoker =
          Blaze.getBuildSystemProvider(project).getBuildSystem().getBuildInvoker(project, context);
      ProcessHandler processHandler;
      String customWorkspace = handlerState.getBlazeWorkspaceState().getBlazeWorkspace();
      WorkspaceRoot workspaceRoot;
      if(customWorkspace == null) {
        workspaceRoot = WorkspaceRoot.fromImportSettings(importSettings);
      } else {
        logger.info("customWorkspace is " + customWorkspace);
        workspaceRoot = new WorkspaceRoot(new File(customWorkspace));
      }
      try (BuildResultHelper buildResultHelper = invoker.createBuildResultHelper()) {
        BlazeTestUiSession testUiSession = null;
        BlazeCommand.Builder blazeCommand =
            getBlazeCommand(
                project,
                ExecutorType.fromExecutor(getEnvironment().getExecutor()),
                invoker,
                ImmutableList.copyOf(buildResultHelper.getBuildFlags()),
                context);

        BlazeTestResultFinderStrategy testResultFinderStrategy;
        if (BlazeCommandRunnerExperiments.isEnabledForTests(invoker.getCommandRunner())) {
          testResultFinderStrategy = new BlazeTestResultHolder();
          // Initialize with empty test results and NO_STATUS to avoid IllegalStateException
          ((BlazeTestResultHolder) testResultFinderStrategy)
              .setTestResults(
                  BlazeTestResults.fromFlatList(
                      ImmutableList.of(
                          BlazeTestResult.create(
                              Label.create(configuration.getSingleTarget().toString()),
                              configuration.getTargetKind(),
                              TestStatus.NO_STATUS,
                              ImmutableSet.of()))));
        } else {
          testResultFinderStrategy =
              new LocalBuildEventProtocolTestFinderStrategy(buildResultHelper);
        }

        if (canUseTestUi()
            && BlazeTestEventsHandler.targetsSupported(project, configuration.getTargets())) {
          testUiSession =
              BlazeTestUiSession.create(
                  ImmutableList.<String>builder()
                      .addAll(ImmutableList.copyOf(buildResultHelper.getBuildFlags()))
                      .add("--runs_per_test=1")
                      .add("--flaky_test_attempts=1")
                      .build(),
                  testResultFinderStrategy);
        }
        if (testUiSession != null) {
          ConsoleView console =
              SmRunnerUtils.getConsoleView(
                  project, configuration, getEnvironment().getExecutor(), testUiSession);
          setConsoleBuilder(
              new TextConsoleBuilderImpl(project) {
                @Override
                protected ConsoleView createConsole() {
                  return console;
                }
              });
          context.addOutputSink(PrintOutput.class, new WritingOutputSink(console));
        }
        addConsoleFilters(consoleFilters.toArray(new Filter[0]));
        if (!BlazeCommandRunnerExperiments.isEnabledForTests(invoker.getCommandRunner())) {
          return getScopedProcessHandler(project, blazeCommand.build(), workspaceRoot);
        }

        processHandler = getGenericProcessHandler();
        ListenableFuture<BlazeTestResults> blazeTestResultsFuture =
            BlazeExecutor.getInstance()
                .submit(
                    () ->
                        invoker
                            .getCommandRunner()
                            .runTest(project, blazeCommand, buildResultHelper, context));
        Futures.addCallback(
            blazeTestResultsFuture,
            new FutureCallback<BlazeTestResults>() {
              @Override
              public void onSuccess(BlazeTestResults blazeTestResults) {
                verify(testResultFinderStrategy instanceof BlazeTestResultHolder);
                ((BlazeTestResultHolder) testResultFinderStrategy).setTestResults(blazeTestResults);
                processHandler.detachProcess();
              }

              @Override
              public void onFailure(Throwable throwable) {
                processHandler.detachProcess();
              }
            },
            BlazeExecutor.getInstance().getExecutor());

        processHandler.addProcessListener(
            new ProcessAdapter() {
              @Override
              @SuppressWarnings("Interruption")
              public void processWillTerminate(
                  @NotNull ProcessEvent event, boolean willBeDestroyed) {
                if (willBeDestroyed) {
                  blazeTestResultsFuture.cancel(true);
                  context.output(
                      PrintOutput.error(
                          "Error: Tests interrupted, could not parse the test results for "
                              + configuration.getName()));
                }
              }
            });
      }
      return processHandler;
    }

    private ProcessHandler getGenericProcessHandler() {
      return new ProcessHandler() {
        @Override
        protected void destroyProcessImpl() {
          notifyProcessTerminated(0);
        }

        @Override
        protected void detachProcessImpl() {
          ApplicationManager.getApplication().executeOnPooledThread(this::notifyProcessDetached);
        }

        @Override
        public boolean detachIsDefault() {
          return false;
        }

        @Nullable
        @Override
        public OutputStream getProcessInput() {
          return null;
        }
      };
    }

    private ProcessHandler getScopedProcessHandler(
        Project project, BlazeCommand blazeCommand, WorkspaceRoot workspaceRoot)
        throws ExecutionException {
      return new ScopedBlazeProcessHandler(
          project,
          blazeCommand,
          workspaceRoot,
          new ScopedBlazeProcessHandler.ScopedProcessHandlerDelegate() {
            @Override
            public void onBlazeContextStart(BlazeContext context) {
              context
                  .push(
                      new ProblemsViewScope(
                          project, BlazeUserSettings.getInstance().getShowProblemsViewOnRun()))
                  .push(new IdeaLogScope());
            }

            @Override
            public ImmutableList<ProcessListener> createProcessListeners(BlazeContext context) {
              LineProcessingOutputStream outputStream =
                  LineProcessingOutputStream.of(
                      BlazeConsoleLineProcessorProvider.getAllStderrLineProcessors(context));
              return ImmutableList.of(new LineProcessingProcessAdapter(outputStream));
            }
          });
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

      return BlazeCommand.builder(invoker, command)
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

    private boolean canUseTestUi() {
      return BlazeCommandName.TEST.equals(getCommand());
    }
  }

  private static class WritingOutputSink implements OutputSink<PrintOutput> {
    private final ConsoleView console;

    public WritingOutputSink(ConsoleView console) {
      this.console = console;
    }

    @Override
    public Propagation onOutput(PrintOutput output) {
      // Add ANSI support to the console to view colored output
      console.print(
          output.getText().replaceAll("\u001B\\[[;\\d]*m", "") + "\n",
          output.getOutputType() == OutputType.ERROR
              ? ConsoleViewContentType.ERROR_OUTPUT
              : ConsoleViewContentType.NORMAL_OUTPUT);
      return Propagation.Continue;
    }
  }
}
