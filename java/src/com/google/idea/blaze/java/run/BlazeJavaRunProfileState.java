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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.async.process.LineProcessingOutputStream;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.command.BlazeInvocationContext;
import com.google.idea.blaze.base.console.BlazeConsoleLineProcessorProvider;
import com.google.idea.blaze.base.issueparser.IssueOutputFilter;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.RuleType;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.run.BlazeBeforeRunCommandHelper;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.ExecutorType;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationRunner;
import com.google.idea.blaze.base.run.filter.BlazeTargetFilter;
import com.google.idea.blaze.base.run.processhandler.LineProcessingProcessAdapter;
import com.google.idea.blaze.base.run.processhandler.ScopedBlazeProcessHandler;
import com.google.idea.blaze.base.run.smrunner.BlazeTestUiSession;
import com.google.idea.blaze.base.run.smrunner.SmRunnerUtils;
import com.google.idea.blaze.base.run.smrunner.TestUiSessionProvider;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.scopes.IdeaLogScope;
import com.google.idea.blaze.base.scope.scopes.ProblemsViewScope;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.google.idea.blaze.java.run.hotswap.HotSwapUtils;
import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.filters.TextConsoleBuilderImpl;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.project.Project;
import com.intellij.util.execution.ParametersListUtil;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * A Blaze run configuration set up with a an executor, program runner, and other settings, ready to
 * be executed. This class creates a command line for Blaze and exposes debug connection information
 * when using a debug executor.
 */
final class BlazeJavaRunProfileState extends BlazeJavaDebuggableRunProfileState {

  BlazeJavaRunProfileState(ExecutionEnvironment environment) {
    super(environment);
  }

  @Override
  protected ProcessHandler startProcess() throws ExecutionException {
    Project project = getConfiguration().getProject();

    BlazeCommand.Builder blazeCommand;
    BlazeTestUiSession testUiSession =
        useTestUi()
            ? TestUiSessionProvider.getInstance(project)
                .getTestUiSession(getConfiguration().getTargets())
            : null;
    if (testUiSession != null) {
      blazeCommand =
          getBlazeCommandBuilder(
              project, getConfiguration(), testUiSession.getBlazeFlags(), getExecutorType());
      setConsoleBuilder(
          new TextConsoleBuilderImpl(project) {
            @Override
            protected ConsoleView createConsole() {
              return SmRunnerUtils.getConsoleView(
                  project, getConfiguration(), getEnvironment().getExecutor(), testUiSession);
            }
          });
    } else {
      blazeCommand =
          getBlazeCommandBuilder(
              project, getConfiguration(), ImmutableList.of(), getExecutorType());
    }
    addConsoleFilters(
        new BlazeTargetFilter(true),
        new IssueOutputFilter(
            project,
            WorkspaceRoot.fromProject(project),
            BlazeInvocationContext.ContextType.RunConfiguration,
            false));

    List<String> command =
        HotSwapUtils.canHotSwap(getEnvironment())
            ? getBashCommandsToRunScript(blazeCommand)
            : blazeCommand.build().toList();

    WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProject(project);
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

  /** Appends '--script_path' to blaze flags, then runs 'bash -c blaze build ... && run_script' */
  private static List<String> getBashCommandsToRunScript(BlazeCommand.Builder blazeCommand) {
    File scriptFile = BlazeBeforeRunCommandHelper.createScriptPathFile();
    blazeCommand.addBlazeFlags("--script_path=" + scriptFile.getPath());
    String blaze = ParametersListUtil.join(blazeCommand.build().toList());
    return ImmutableList.of("/bin/bash", "-c", blaze + " && " + scriptFile.getPath());
  }

  @Override
  @SuppressWarnings("rawtypes") // #api193: Use ProgramRunner<?> as super method from 2020.1 on.
  public ExecutionResult execute(Executor executor, ProgramRunner runner)
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

  private static BlazeJavaRunConfigState getState(BlazeCommandRunConfiguration config) {
    return Preconditions.checkNotNull(config.getHandlerStateIfType(BlazeJavaRunConfigState.class));
  }

  @VisibleForTesting
  static BlazeCommand.Builder getBlazeCommandBuilder(
      Project project,
      BlazeCommandRunConfiguration configuration,
      List<String> extraBlazeFlags,
      ExecutorType executorType) {

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
}
