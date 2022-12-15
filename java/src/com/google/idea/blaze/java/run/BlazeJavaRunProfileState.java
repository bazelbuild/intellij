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
package com.google.idea.blaze.java.run;

import static com.google.common.base.Verify.verify;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.google.idea.blaze.base.async.process.LineProcessingOutputStream;
import com.google.idea.blaze.base.bazel.BuildSystem;
import com.google.idea.blaze.base.bazel.BuildSystem.BuildInvoker;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeCommandRunner;
import com.google.idea.blaze.base.command.BlazeCommandRunnerExperiments;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.command.BlazeInvocationContext;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.console.BlazeConsoleLineProcessorProvider;
import com.google.idea.blaze.base.issueparser.ToolWindowTaskIssueOutputFilter;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.RuleType;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.ExecutorType;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationRunner;
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
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.common.PrintOutput.OutputType;
import com.google.idea.blaze.java.run.hotswap.HotSwapCommandBuilder;
import com.google.idea.blaze.java.run.hotswap.HotSwapUtils;
import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.filters.TextConsoleBuilderImpl;
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
import com.intellij.openapi.util.SystemInfo;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * A Blaze run configuration set up with an executor, program runner, and other settings, ready to
 * be executed. This class creates a command line for Blaze and exposes debug connection information
 * when using a debug executor.
 */
public final class BlazeJavaRunProfileState extends BlazeJavaDebuggableRunProfileState {
  private static final Logger logger = Logger.getInstance(BlazeJavaRunProfileState.class);
  @Nullable private String kotlinxCoroutinesJavaAgent;

  BlazeJavaRunProfileState(ExecutionEnvironment environment) {
    super(environment);
  }

  public void addKotlinxCoroutinesJavaAgent(String kotlinxCoroutinesJavaAgent) {
    this.kotlinxCoroutinesJavaAgent = kotlinxCoroutinesJavaAgent;
  }

  @Override
  protected ProcessHandler startProcess() throws ExecutionException {
    Project project = getConfiguration().getProject();

    BlazeCommand.Builder blazeCommand;
    BlazeContext context = BlazeContext.create();
    WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProject(project);
    BuildSystem buildSystem = Blaze.getBuildSystemProvider(project).getBuildSystem();
    BuildInvoker invoker = buildSystem.getBuildInvoker(project, context);
    ProcessHandler processHandler;
    BlazeTestUiSession testUiSession = null;
    ImmutableList<String> extraFlags =
        ImmutableList.of("--runs_per_test=1", "--flaky_test_attempts=1");
    try (BuildResultHelper buildResultHelper = invoker.createBuildResultHelper()) {
      blazeCommand =
          getBlazeCommandBuilder(
              project,
              getConfiguration(),
              ImmutableList.<String>builder()
                  .addAll(extraFlags)
                  .addAll(buildResultHelper.getBuildFlags())
                  .build(),
              getExecutorType(),
              kotlinxCoroutinesJavaAgent);

      BlazeTestResultFinderStrategy testResultFinderStrategy;
      if (useBlazeCommandRunner(invoker.getCommandRunner())) {
        testResultFinderStrategy = new BlazeTestResultHolder();
        // Initialize with empty test results and NO_STATUS to avoid IllegalStateException
        ((BlazeTestResultHolder) testResultFinderStrategy)
            .setTestResults(
                BlazeTestResults.fromFlatList(
                    ImmutableList.of(
                        BlazeTestResult.create(
                            Label.create(getConfiguration().getSingleTarget().toString()),
                            getConfiguration().getTargetKind(),
                            TestStatus.NO_STATUS,
                            ImmutableSet.of()))));
      } else {
        testResultFinderStrategy = new LocalBuildEventProtocolTestFinderStrategy(buildResultHelper);
      }
      if (useTestUi()
          && BlazeTestEventsHandler.targetsSupported(project, getConfiguration().getTargets())) {
        testUiSession =
            BlazeTestUiSession.create(
                ImmutableList.<String>builder()
                    .addAll(extraFlags)
                    .addAll(buildResultHelper.getBuildFlags())
                    .build(),
                testResultFinderStrategy);
      }


    if (testUiSession != null) {
        ConsoleView console =
            SmRunnerUtils.getConsoleView(
                project, getConfiguration(), getEnvironment().getExecutor(), testUiSession);
        setConsoleBuilder(
            new TextConsoleBuilderImpl(project) {
              @Override
              protected ConsoleView createConsole() {
                return console;
              }
            });
        context.addOutputSink(PrintOutput.class, new WritingOutputSink(console));
    }
    addConsoleFilters(
        ToolWindowTaskIssueOutputFilter.createWithDefaultParsers(
            project,
            WorkspaceRoot.fromProject(project),
            BlazeInvocationContext.ContextType.RunConfiguration));

    List<String> command;
    if (HotSwapUtils.canHotSwap(getEnvironment())) {
      try {
        command = HotSwapCommandBuilder.getBashCommandsToRunScript(project, blazeCommand);
      } catch (IOException e) {
        logger.warn("Failed to create script path. Hot swap will be disabled.", e);
        command = blazeCommand.build().toList();
      }
    } else {
      command = blazeCommand.build().toList();
    }
      if (!useBlazeCommandRunner(invoker.getCommandRunner())) {
        return getScopedProcessHandler(project, command, workspaceRoot);
      }

      processHandler = getGenericProcessHandler();
      ListenableFuture<BlazeTestResults> blazeTestResultsFuture =
          BlazeExecutor.getInstance()
              .submit(
                  () ->
                      invoker
                          .getCommandRunner()
                          .runTest(
                              project, blazeCommand, buildResultHelper, workspaceRoot, context));
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
              logger.warn(throwable);
              processHandler.detachProcess();
            }
          },
          BlazeExecutor.getInstance().getExecutor());
      processHandler.addProcessListener(
          new ProcessAdapter() {
            @Override
            @SuppressWarnings("Interruption")
            public void processWillTerminate(@NotNull ProcessEvent event, boolean willBeDestroyed) {
              if (willBeDestroyed) {
                blazeTestResultsFuture.cancel(true);
                context.output(
                    PrintOutput.error(
                        "Error: Tests interrupted, could not parse the test results for "
                            + getConfiguration().getName()));
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
      Project project, List<String> command, WorkspaceRoot workspaceRoot)
      throws ExecutionException {
    return new ScopedBlazeProcessHandler(
        project,
        command,
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

  @Override
  public ExecutionResult execute(Executor executor, ProgramRunner<?> runner)
      throws ExecutionException {
    if (BlazeCommandRunConfigurationRunner.isDebugging(getEnvironment())) {
      new MultiRunDebuggerSessionListener(getEnvironment(), this).startListening();
    }
    DefaultExecutionResult result = (DefaultExecutionResult) super.execute(executor, runner);
    return SmRunnerUtils.attachRerunFailedTestsAction(result);
  }

  private boolean useTestUi() {
    BlazeCommandRunConfigurationCommonState state =
        getConfiguration().getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
    return state != null && BlazeCommandName.TEST.equals(state.getCommandState().getCommand());
  }

  private boolean useBlazeCommandRunner(BlazeCommandRunner runner) {
    if (SystemInfo.isMac) {
      // Debugging java tests and android local tests isn't supported on Mac yet
      return false;
    }
    return BlazeCommandRunnerExperiments.isEnabledForTests(runner);
  }

  private static BlazeJavaRunConfigState getState(BlazeCommandRunConfiguration config) {
    return Preconditions.checkNotNull(config.getHandlerStateIfType(BlazeJavaRunConfigState.class));
  }

  @VisibleForTesting
  static BlazeCommand.Builder getBlazeCommandBuilder(
      Project project,
      BlazeCommandRunConfiguration configuration,
      List<String> extraBlazeFlags,
      ExecutorType executorType,
      @Nullable String kotlinxCoroutinesJavaAgent) {

    List<String> blazeFlags = new ArrayList<>(extraBlazeFlags);

    ProjectViewSet projectViewSet =
        Preconditions.checkNotNull(ProjectViewManager.getInstance(project).getProjectViewSet());
    BlazeJavaRunConfigState handlerState = getState(configuration);

    String binaryPath =
        handlerState.getBlazeBinaryState().getBlazeBinary() != null
            ? handlerState.getBlazeBinaryState().getBlazeBinary()
            : Blaze.getBuildSystemProvider(project).getBinaryPath(project);

    BlazeCommandName blazeCommand =
        Preconditions.checkNotNull(handlerState.getCommandState().getCommand());
    if (executorType == ExecutorType.COVERAGE) {
      blazeCommand = BlazeCommandName.COVERAGE;
    }
    BlazeCommand.Builder command =
        BlazeCommand.builder(binaryPath, blazeCommand)
            .addTargets(configuration.getTargets())
            .addBlazeFlags(
                BlazeFlags.blazeFlags(
                    project,
                    projectViewSet,
                    blazeCommand,
                    BlazeContext.create(),
                    BlazeInvocationContext.runConfigContext(
                        executorType, configuration.getType(), false)))
            .addBlazeFlags(blazeFlags)
            .addBlazeFlags(handlerState.getBlazeFlagsState().getFlagsForExternalProcesses());

    if (executorType == ExecutorType.DEBUG) {
      Kind kind = configuration.getTargetKind();
      boolean isBinary = kind != null && kind.getRuleType() == RuleType.BINARY;
      int debugPort = handlerState.getDebugPortState().port;
      if (isBinary) {
        command.addExeFlags(debugPortFlag(false, debugPort));
      } else {
        command.addBlazeFlags(BlazeFlags.JAVA_TEST_DEBUG);
        command.addBlazeFlags(debugPortFlag(true, debugPort));
      }
      if (kotlinxCoroutinesJavaAgent != null) {
        command.addBlazeFlags("--jvmopt=-javaagent:" + kotlinxCoroutinesJavaAgent);
      }
    }

    command.addExeFlags(handlerState.getExeFlagsState().getFlagsForExternalProcesses());
    return command;
  }

  private static String debugPortFlag(boolean isTest, int port) {
    String flag = "--wrapper_script_flag=--debug=" + port;
    return isTest ? testArg(flag) : flag;
  }

  private static String testArg(String flag) {
    return "--test_arg=" + flag;
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
