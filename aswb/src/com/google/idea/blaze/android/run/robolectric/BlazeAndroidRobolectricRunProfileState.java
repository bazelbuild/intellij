/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.run.robolectric;

import static com.google.common.base.Preconditions.checkState;

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
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.ExecutorType;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationRunner;
import com.google.idea.blaze.base.run.filter.BlazeTargetFilter;
import com.google.idea.blaze.base.run.processhandler.LineProcessingProcessAdapter;
import com.google.idea.blaze.base.run.processhandler.ScopedBlazeProcessHandler;
import com.google.idea.blaze.base.run.smrunner.BlazeTestUiSession;
import com.google.idea.blaze.base.run.smrunner.SmRunnerUtils;
import com.google.idea.blaze.base.run.smrunner.TestUiSessionProvider;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.scopes.IdeaLogScope;
import com.google.idea.blaze.base.scope.scopes.ProblemsViewScope;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.google.idea.blaze.java.run.MultiRunDebuggerSessionListener;
import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.CommandLineState;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.configurations.RemoteState;
import com.intellij.execution.filters.TextConsoleBuilderImpl;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.project.Project;
import java.util.ArrayList;
import java.util.List;

/** Run profile state for robolectric run configurations. */
public class BlazeAndroidRobolectricRunProfileState extends CommandLineState
    implements RemoteState {
  private static final String DEBUG_HOST_NAME = "localhost";

  private final BlazeCommandRunConfiguration cfg;
  private final ExecutorType executorType;

  public BlazeAndroidRobolectricRunProfileState(ExecutionEnvironment environment) {
    super(environment);
    this.cfg = BlazeCommandRunConfigurationRunner.getConfiguration(environment);
    this.executorType = ExecutorType.fromExecutor(environment.getExecutor());
  }

  @Override
  public RemoteConnection getRemoteConnection() {
    if (!executorType.isDebugType()) {
      return null;
    }
    BlazeAndroidRobolectricRunConfigurationState state =
        cfg.getHandlerStateIfType(BlazeAndroidRobolectricRunConfigurationState.class);
    checkState(state != null);
    return new RemoteConnection(
        /* useSockets= */ true,
        DEBUG_HOST_NAME,
        Integer.toString(state.getDebugPortState().port),
        /* serverMode= */ false);
  }

  @Override
  protected ProcessHandler startProcess() throws ExecutionException {
    Project project = cfg.getProject();

    BlazeCommand.Builder blazeCommand;
    BlazeTestUiSession testUiSession =
        TestUiSessionProvider.getInstance(project).getTestUiSession(cfg.getTargets());
    if (testUiSession != null) {
      blazeCommand =
          getBlazeCommandBuilder(project, cfg, testUiSession.getBlazeFlags(), executorType);
      setConsoleBuilder(
          new TextConsoleBuilderImpl(project) {
            @Override
            protected ConsoleView createConsole() {
              return SmRunnerUtils.getConsoleView(
                  project, cfg, getEnvironment().getExecutor(), testUiSession);
            }
          });
    } else {
      blazeCommand = getBlazeCommandBuilder(project, cfg, ImmutableList.of(), executorType);
    }
    addConsoleFilters(
        new BlazeTargetFilter(true),
        new IssueOutputFilter(
            project,
            WorkspaceRoot.fromProject(project),
            BlazeInvocationContext.ContextType.RunConfiguration,
            false));

    List<String> command = blazeCommand.build().toList();
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

  @Override
  public ExecutionResult execute(Executor executor, ProgramRunner runner)
      throws ExecutionException {
    if (BlazeCommandRunConfigurationRunner.isDebugging(getEnvironment())) {
      new MultiRunDebuggerSessionListener(getEnvironment(), this).startListening();
    }
    DefaultExecutionResult result = (DefaultExecutionResult) super.execute(executor, runner);
    return SmRunnerUtils.attachRerunFailedTestsAction(result);
  }

  private static BlazeAndroidRobolectricRunConfigurationState getState(
      BlazeCommandRunConfiguration config) {
    return Preconditions.checkNotNull(
        config.getHandlerStateIfType(BlazeAndroidRobolectricRunConfigurationState.class));
  }

  @VisibleForTesting
  static BlazeCommand.Builder getBlazeCommandBuilder(
      Project project,
      BlazeCommandRunConfiguration configuration,
      List<String> extraBlazeFlags,
      ExecutorType executorType) {
    ProjectViewSet projectViewSet =
        Preconditions.checkNotNull(ProjectViewManager.getInstance(project).getProjectViewSet());
    BlazeAndroidRobolectricRunConfigurationState handlerState = getState(configuration);

    String binaryPath =
        handlerState.getBlazeBinaryState().getBlazeBinary() != null
            ? handlerState.getBlazeBinaryState().getBlazeBinary()
            : Blaze.getBuildSystemProvider(project).getBinaryPath(project);

    BlazeCommandName blazeCommand =
        Preconditions.checkNotNull(handlerState.getCommandState().getCommand());

    if (executorType == ExecutorType.COVERAGE) {
      blazeCommand = BlazeCommandName.COVERAGE;
    }

    ArrayList<String> allBlazeFlags = new ArrayList<>();
    allBlazeFlags.addAll(
        BlazeFlags.blazeFlags(
            project,
            projectViewSet,
            blazeCommand,
            BlazeInvocationContext.runConfigContext(executorType, configuration.getType(), false)));
    allBlazeFlags.addAll(extraBlazeFlags);
    allBlazeFlags.addAll(handlerState.getBlazeFlagsState().getFlagsForExternalProcesses());

    if (executorType == ExecutorType.DEBUG) {
      int debugPort = handlerState.getDebugPortState().port;
      allBlazeFlags.add(BlazeFlags.JAVA_TEST_DEBUG);
      allBlazeFlags.add(debugPortFlag(debugPort));
    }

    for (BlazeRobolectricFlagProvider flagProvider :
        BlazeRobolectricFlagProvider.EP_NAME.getExtensions()) {
      flagProvider.addBuildFlags(configuration.getSingleTarget(), allBlazeFlags);
    }

    return BlazeCommand.builder(binaryPath, blazeCommand)
        .addTargets(configuration.getTargets())
        .addBlazeFlags(allBlazeFlags)
        .addExeFlags(handlerState.getExeFlagsState().getFlagsForExternalProcesses());
  }

  private static String debugPortFlag(int port) {
    return "--test_arg=--wrapper_script_flag=--debug=" + port;
  }
}
