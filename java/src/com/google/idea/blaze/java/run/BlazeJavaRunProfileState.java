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
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.async.process.LineProcessingOutputStream;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.command.BlazeInvocationContext;
import com.google.idea.blaze.base.issueparser.IssueOutputLineProcessor;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
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
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
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

/**
 * A Blaze run configuration set up with a an executor, program runner, and other settings, ready to
 * be executed. This class creates a command line for Blaze and exposes debug connection information
 * when using a debug executor.
 */
final class BlazeJavaRunProfileState extends CommandLineState implements RemoteState {

  // Blaze seems to always use this port for --java_debug.
  // TODO(joshgiles): Look at manually identifying and setting port.
  private static final int DEBUG_PORT = 5005;
  private static final String DEBUG_HOST_NAME = "localhost";

  private final BlazeCommandRunConfiguration configuration;
  private final boolean debug;

  public BlazeJavaRunProfileState(ExecutionEnvironment environment, boolean debug) {
    super(environment);
    this.configuration = getConfiguration(environment);
    this.debug = debug;
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
    BlazeImportSettings importSettings =
        BlazeImportSettingsManager.getInstance(project).getImportSettings();
    assert importSettings != null;

    ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
    assert projectViewSet != null;

    BlazeCommand blazeCommand;
    BlazeTestUiSession testUiSession =
        useTestUi()
            ? TestUiSessionProvider.createForTarget(project, configuration.getTarget())
            : null;
    if (testUiSession != null) {
      blazeCommand =
          getBlazeCommand(
              project, configuration, projectViewSet, testUiSession.getBlazeFlags(), debug);
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
          getBlazeCommand(project, configuration, projectViewSet, ImmutableList.of(), debug);
    }
    addConsoleFilters(new BlazeTargetFilter(project));

    WorkspaceRoot workspaceRoot = WorkspaceRoot.fromImportSettings(importSettings);
    return new ScopedBlazeProcessHandler(
        project,
        blazeCommand,
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
                    new IssueOutputLineProcessor(project, context, workspaceRoot));
            return ImmutableList.of(new LineProcessingProcessAdapter(outputStream));
          }
        });
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
    if (!debug) {
      return null;
    }
    return new RemoteConnection(
        /* useSockets */ true,
        DEBUG_HOST_NAME,
        Integer.toString(DEBUG_PORT),
        /* serverMode */ false);
  }

  @VisibleForTesting
  static BlazeCommand getBlazeCommand(
      Project project,
      BlazeCommandRunConfiguration configuration,
      ProjectViewSet projectViewSet,
      ImmutableList<String> extraBlazeFlags,
      boolean debug) {

    BlazeCommandRunConfigurationCommonState handlerState =
        configuration.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
    assert handlerState != null;

    String binaryPath =
        handlerState.getBlazeBinaryState().getBlazeBinary() != null
            ? handlerState.getBlazeBinaryState().getBlazeBinary()
            : Blaze.getBuildSystemProvider(project).getBinaryPath();

    BlazeCommandName blazeCommand = handlerState.getCommandState().getCommand();
    assert blazeCommand != null;
    BlazeCommand.Builder command =
        BlazeCommand.builder(binaryPath, blazeCommand)
            .addTargets(configuration.getTarget())
            .addBlazeFlags(
                BlazeFlags.blazeFlags(
                    project, projectViewSet, blazeCommand, BlazeInvocationContext.RunConfiguration))
            .addBlazeFlags(extraBlazeFlags)
            .addBlazeFlags(handlerState.getBlazeFlagsState().getExpandedFlags());

    if (debug) {
      Kind kind = configuration.getKindForTarget();
      boolean isBinary = kind != null && kind.isOneOf(Kind.JAVA_BINARY, Kind.SCALA_BINARY);
      if (isBinary) {
        command.addExeFlags(BlazeFlags.JAVA_BINARY_DEBUG);
      } else {
        command.addBlazeFlags(BlazeFlags.JAVA_TEST_DEBUG);
      }
    }

    command.addExeFlags(handlerState.getExeFlagsState().getExpandedFlags());
    return command.build();
  }
}
