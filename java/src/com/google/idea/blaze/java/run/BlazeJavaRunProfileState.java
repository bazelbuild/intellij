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
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.ExecutorType;
import com.google.idea.blaze.base.run.coverage.CoverageUtils;
import com.google.idea.blaze.base.run.filter.BlazeTargetFilter;
import com.google.idea.blaze.base.run.processhandler.LineProcessingProcessAdapter;
import com.google.idea.blaze.base.run.processhandler.ScopedBlazeProcessHandler;
import com.google.idea.blaze.base.run.smrunner.BlazeTestUiSession;
import com.google.idea.blaze.base.run.smrunner.SmRunnerUtils;
import com.google.idea.blaze.base.run.smrunner.TestUiSessionProvider;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.scopes.IdeaLogScope;
import com.google.idea.blaze.base.scope.scopes.IssuesScope;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.java.run.hotswap.HotSwapUtils;
import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.CommandLineState;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.configurations.RemoteState;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.WrappingRunConfiguration;
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
import java.util.UUID;

/**
 * A Blaze run configuration set up with a an executor, program runner, and other settings, ready to
 * be executed. This class creates a command line for Blaze and exposes debug connection information
 * when using a debug executor.
 */
final class BlazeJavaRunProfileState extends CommandLineState implements RemoteState {

  private static final int DEBUG_PORT = 5005; // default port for java debugging
  private static final String DEBUG_HOST_NAME = "localhost";

  private final BlazeCommandRunConfiguration configuration;
  private final ExecutorType executorType;

  public BlazeJavaRunProfileState(ExecutionEnvironment environment) {
    super(environment);
    this.configuration = getConfiguration(environment);
    this.executorType = ExecutorType.fromExecutor(environment.getExecutor());
  }

  private static BlazeCommandRunConfiguration getConfiguration(ExecutionEnvironment environment) {
    RunProfile runProfile = environment.getRunProfile();
    if (runProfile instanceof WrappingRunConfiguration) {
      runProfile = ((WrappingRunConfiguration) runProfile).getPeer();
    }
    return (BlazeCommandRunConfiguration) runProfile;
  }

  @Override
  protected ProcessHandler startProcess() throws ExecutionException {
    Project project = configuration.getProject();

    BlazeCommand.Builder blazeCommand;
    BlazeTestUiSession testUiSession =
        useTestUi()
            ? TestUiSessionProvider.createForTarget(project, configuration.getTarget())
            : null;
    if (testUiSession != null) {
      blazeCommand =
          getBlazeCommandBuilder(
              project, configuration, testUiSession.getBlazeFlags(), executorType);
      setConsoleBuilder(
          new TextConsoleBuilderImpl(project) {
            @Override
            protected ConsoleView createConsole() {
              return SmRunnerUtils.getConsoleView(
                  project, configuration, getEnvironment().getExecutor(), testUiSession);
            }
          });
    } else {
      blazeCommand =
          getBlazeCommandBuilder(project, configuration, ImmutableList.of(), executorType);
    }
    addConsoleFilters(new BlazeTargetFilter(project));

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
            context.push(new IssuesScope(project)).push(new IdeaLogScope());
          }

          @Override
          public ImmutableList<ProcessListener> createProcessListeners(BlazeContext context) {
            LineProcessingOutputStream outputStream =
                LineProcessingOutputStream.of(
                    BlazeConsoleLineProcessorProvider.getAllStderrLineProcessors(
                        project, context, workspaceRoot));
            return ImmutableList.of(new LineProcessingProcessAdapter(outputStream));
          }
        });
  }

  /** Appends '--script_path' to blaze flags, then runs 'bash -c blaze build ... && run_script' */
  private static List<String> getBashCommandsToRunScript(BlazeCommand.Builder blazeCommand) {
    File scriptFile = createTempOutputFile();
    blazeCommand.addBlazeFlags("--script_path=" + scriptFile.getPath());
    String blaze = ParametersListUtil.join(blazeCommand.build().toList());
    return ImmutableList.of("/bin/bash", "-c", blaze + " && " + scriptFile.getPath());
  }

  /** Creates a temporary output file to write the shell script to. */
  private static File createTempOutputFile() {
    File tempDir = new File(System.getProperty("java.io.tmpdir"));
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    String fileName = "blaze-script-" + suffix;
    File tempFile = new File(tempDir, fileName);
    tempFile.deleteOnExit();
    return tempFile;
  }

  @Override
  public ExecutionResult execute(Executor executor, ProgramRunner runner)
      throws ExecutionException {
    DefaultExecutionResult result = (DefaultExecutionResult) super.execute(executor, runner);
    return SmRunnerUtils.attachRerunFailedTestsAction(result);
  }

  private boolean useTestUi() {
    BlazeCommandRunConfigurationCommonState state =
        configuration.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
    return state != null && BlazeCommandName.TEST.equals(state.getCommandState().getCommand());
  }

  @Override
  public RemoteConnection getRemoteConnection() {
    if (executorType != ExecutorType.DEBUG) {
      return null;
    }
    return new RemoteConnection(
        /* useSockets */ true,
        DEBUG_HOST_NAME,
        Integer.toString(DEBUG_PORT),
        /* serverMode */ false);
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
    BlazeCommandRunConfigurationCommonState handlerState =
        Preconditions.checkNotNull(
            configuration.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class));

    String binaryPath =
        handlerState.getBlazeBinaryState().getBlazeBinary() != null
            ? handlerState.getBlazeBinaryState().getBlazeBinary()
            : Blaze.getBuildSystemProvider(project).getBinaryPath();

    BlazeCommandName blazeCommand =
        Preconditions.checkNotNull(handlerState.getCommandState().getCommand());
    if (executorType == ExecutorType.COVERAGE) {
      blazeCommand = BlazeCommandName.COVERAGE;
      blazeFlags.addAll(CoverageUtils.getBlazeFlags());
    }
    BlazeCommand.Builder command =
        BlazeCommand.builder(binaryPath, blazeCommand)
            .addTargets(configuration.getTarget())
            .addBlazeFlags(
                BlazeFlags.blazeFlags(
                    project, projectViewSet, blazeCommand, BlazeInvocationContext.RunConfiguration))
            .addBlazeFlags(blazeFlags)
            .addBlazeFlags(handlerState.getBlazeFlagsState().getExpandedFlags());

    if (executorType == ExecutorType.DEBUG) {
      Kind kind = configuration.getKindForTarget();
      boolean isBinary = kind != null && kind.isOneOf(Kind.JAVA_BINARY, Kind.SCALA_BINARY);
      if (isBinary) {
        command.addExeFlags(BlazeFlags.JAVA_BINARY_DEBUG);
      } else {
        command.addBlazeFlags(BlazeFlags.JAVA_TEST_DEBUG);
      }
    }

    command.addExeFlags(handlerState.getExeFlagsState().getExpandedFlags());
    return command;
  }
}
