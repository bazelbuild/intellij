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

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.async.process.LineProcessingOutputStream;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.issueparser.IssueOutputLineProcessor;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.processhandler.LineProcessingProcessAdapter;
import com.google.idea.blaze.base.run.processhandler.ScopedBlazeProcessHandler;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.scopes.IssuesScope;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.CommandLineState;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugSession;
import com.jetbrains.cidr.cpp.execution.CLionRunParameters;
import com.jetbrains.cidr.execution.CidrConsoleBuilder;
import com.jetbrains.cidr.execution.TrivialInstaller;
import com.jetbrains.cidr.execution.debugger.CidrDebugProcess;
import com.jetbrains.cidr.execution.debugger.CidrLocalDebugProcess;
import com.jetbrains.cidr.execution.testing.CidrLauncher;
import com.jetbrains.cidr.execution.testing.OCGoogleTestConsoleProperties;
import java.io.File;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * Handles running/debugging cc_test and cc_binary targets in CLion. Sets up gdb when debugging, and
 * uses the Google Test infrastructure for presenting test results.
 */
public final class BlazeCidrLauncher extends CidrLauncher {
  private static final Logger LOG = Logger.getInstance(BlazeCidrLauncher.class);

  private final Project project;
  private final BlazeCommandRunConfiguration configuration;
  private final BlazeCommandRunConfigurationCommonState handlerState;
  private final BlazeCidrRunConfigurationRunner runner;
  private final ExecutionEnvironment executionEnvironment;

  public BlazeCidrLauncher(
      BlazeCommandRunConfiguration configuration,
      BlazeCidrRunConfigurationRunner runner,
      ExecutionEnvironment environment) {
    this.configuration = configuration;
    this.handlerState =
        (BlazeCommandRunConfigurationCommonState) configuration.getHandler().getState();
    this.runner = runner;
    this.executionEnvironment = environment;
    this.project = configuration.getProject();
  }

  @Override
  public ProcessHandler createProcess(CommandLineState state) throws ExecutionException {
    BlazeImportSettings importSettings =
        BlazeImportSettingsManager.getInstance(project).getImportSettings();
    LOG.assertTrue(importSettings != null);

    ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
    LOG.assertTrue(projectViewSet != null);

    state.setConsoleBuilder(createConsoleBuilder());

    BlazeCommand blazeCommand =
        BlazeCommand.builder(Blaze.getBuildSystem(project), handlerState.getCommand())
            .addTargets(configuration.getTarget())
            .addBlazeFlags(BlazeFlags.buildFlags(project, ProjectViewSet.builder().build()))
            .addBlazeFlags(handlerState.getBlazeFlags())
            .addExeFlags(handlerState.getExeFlags())
            .build();

    WorkspaceRoot workspaceRoot = WorkspaceRoot.fromImportSettings(importSettings);
    return new ScopedBlazeProcessHandler(
        project,
        blazeCommand,
        workspaceRoot,
        new ScopedBlazeProcessHandler.ScopedProcessHandlerDelegate() {
          @Override
          public void onBlazeContextStart(BlazeContext context) {
            context
                .push(new IssuesScope(project));
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
  public CidrDebugProcess createDebugProcess(CommandLineState state, XDebugSession session)
      throws ExecutionException {
    TargetExpression target = configuration.getTarget();
    if (target == null) {
      return null;
    }
    if (runner.executableToDebug == null) {
      return null;
    }
    ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
    LOG.assertTrue(projectViewSet != null);
    WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProject(project);

    GeneralCommandLine commandLine = new GeneralCommandLine(runner.executableToDebug.getPath());
    File workingDir = workspaceRoot.directory();
    commandLine.setWorkDirectory(workingDir);
    commandLine.addParameters(handlerState.getExeFlags());

    TrivialInstaller installer = new TrivialInstaller(commandLine);
    ImmutableList<String> startupCommands = getGdbStartupCommands(workingDir);
    CLionRunParameters parameters =
        new CLionRunParameters(
            new BlazeGDBDriverConfiguration(project, startupCommands, workspaceRoot), installer);
    CidrDebugProcess result =
        new CidrLocalDebugProcess(parameters, session, state.getConsoleBuilder());

    return result;
  }

  @NotNull
  @Override
  protected Project getProject() {
    return project;
  }

  private CidrConsoleBuilder createConsoleBuilder() {
    if (Objects.equals(handlerState.getCommand(), BlazeCommandName.TEST)) {
      // Use the Google Test failure/success console instead of a standard console.
      return new GoogleTestConsoleBuilder(configuration.getProject());
    }
    return new CidrConsoleBuilder(configuration.getProject());
  }

  private ImmutableList<String> getGdbStartupCommands(File workingDir) {
    // Forge creates debug symbol paths rooted at /proc/self/cwd .
    // We need to tell gdb to translate this path prefix to the user's workspace
    // root so the IDE can find the files.
    String from = "/proc/self/cwd";
    String to = workingDir.getPath();
    String subPathCommand = String.format("set substitute-path %s %s", from, to);

    return ImmutableList.of(subPathCommand);
  }

  private final class GoogleTestConsoleBuilder extends CidrConsoleBuilder {
    private GoogleTestConsoleBuilder(Project project) {
      super(project);
      addFilter(new BlazeCidrTestOutputFilter(project));
    }

    @Override
    protected ConsoleView createConsole() {
      OCGoogleTestConsoleProperties consoleProperties =
          new OCGoogleTestConsoleProperties(
              configuration,
              executionEnvironment.getExecutor(),
              executionEnvironment.getExecutionTarget());
      return createConsole(configuration.getType(), consoleProperties);
    }
  }
}
