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
import com.google.idea.blaze.base.command.BlazeInvocationContext;
import com.google.idea.blaze.base.console.BlazeConsoleLineProcessorProvider;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.filter.BlazeTargetFilter;
import com.google.idea.blaze.base.run.processhandler.LineProcessingProcessAdapter;
import com.google.idea.blaze.base.run.processhandler.ScopedBlazeProcessHandler;
import com.google.idea.blaze.base.run.smrunner.BlazeTestUiSession;
import com.google.idea.blaze.base.run.smrunner.SmRunnerUtils;
import com.google.idea.blaze.base.run.smrunner.TestUiSessionProvider;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.scopes.IssuesScope;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.Blaze.BuildSystem;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configuration.EnvironmentVariablesData;
import com.intellij.execution.configurations.CommandLineState;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.GeneralCommandLine.ParentEnvironmentType;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.UrlFilter;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugSession;
import com.jetbrains.cidr.cpp.execution.CLionRunParameters;
import com.jetbrains.cidr.execution.CidrConsoleBuilder;
import com.jetbrains.cidr.execution.TrivialInstaller;
import com.jetbrains.cidr.execution.debugger.CidrDebugProcess;
import com.jetbrains.cidr.execution.debugger.CidrLocalDebugProcess;
import com.jetbrains.cidr.execution.testing.CidrLauncher;
import com.jetbrains.cidr.execution.testing.google.CidrGoogleTestConsoleProperties;
import java.io.File;
import javax.annotation.Nullable;

/**
 * Handles running/debugging cc_test and cc_binary targets in CLion. Sets up gdb when debugging, and
 * uses the Google Test infrastructure for presenting test results.
 */
public final class BlazeCidrLauncher extends CidrLauncher {

  private final Project project;
  private final BlazeCommandRunConfiguration configuration;
  private final BlazeCidrRunConfigState handlerState;
  private final BlazeCidrRunConfigurationRunner runner;
  private final ExecutionEnvironment executionEnvironment;

  BlazeCidrLauncher(
      BlazeCommandRunConfiguration configuration,
      BlazeCidrRunConfigurationRunner runner,
      ExecutionEnvironment environment) {
    this.configuration = configuration;
    this.handlerState = (BlazeCidrRunConfigState) configuration.getHandler().getState();
    this.runner = runner;
    this.executionEnvironment = environment;
    this.project = configuration.getProject();
  }

  @Override
  public ProcessHandler createProcess(CommandLineState state) throws ExecutionException {
    ImmutableList<String> testHandlerFlags = ImmutableList.of();
    BlazeTestUiSession testUiSession =
        useTestUi()
            ? TestUiSessionProvider.createForTarget(project, configuration.getTarget())
            : null;
    if (testUiSession != null) {
      testHandlerFlags = testUiSession.getBlazeFlags();
    }

    BlazeCommand.Builder command =
        BlazeCommand.builder(
                Blaze.getBuildSystemProvider(project).getBinaryPath(),
                handlerState.getCommandState().getCommand())
            .addTargets(configuration.getTarget())
            .addBlazeFlags(
                BlazeFlags.blazeFlags(
                    project,
                    ProjectViewSet.builder().build(),
                    handlerState.getCommandState().getCommand(),
                    BlazeInvocationContext.RunConfiguration))
            .addBlazeFlags(testHandlerFlags)
            .addBlazeFlags(handlerState.getBlazeFlagsState().getExpandedFlags())
            .addExeFlags(handlerState.getExeFlagsState().getExpandedFlags());

    state.setConsoleBuilder(createConsoleBuilder(testUiSession));
    state.addConsoleFilters(getConsoleFilters().toArray(new Filter[0]));

    WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProject(project);
    return new ScopedBlazeProcessHandler(
        project,
        command.build(),
        workspaceRoot,
        new ScopedBlazeProcessHandler.ScopedProcessHandlerDelegate() {
          @Override
          public void onBlazeContextStart(BlazeContext context) {
            context.push(new IssuesScope(project));
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

  @Override
  public CidrDebugProcess createDebugProcess(CommandLineState state, XDebugSession session)
      throws ExecutionException {
    TargetExpression target = configuration.getTarget();
    if (target == null) {
      throw new ExecutionException("Cannot parse run configuration target.");
    }
    if (runner.executableToDebug == null) {
      throw new ExecutionException("No debug binary found.");
    }
    WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProject(project);

    GeneralCommandLine commandLine = new GeneralCommandLine(runner.executableToDebug.getPath());
    File workingDir = workspaceRoot.directory();
    commandLine.setWorkDirectory(workingDir);
    commandLine.addParameters(handlerState.getExeFlagsState().getExpandedFlags());

    EnvironmentVariablesData envState = handlerState.getEnvVarsState().getData();
    commandLine.withParentEnvironmentType(
        envState.isPassParentEnvs() ? ParentEnvironmentType.SYSTEM : ParentEnvironmentType.NONE);
    commandLine.getEnvironment().putAll(envState.getEnvs());

    String testPrefix = "--gtest";
    if (Blaze.getBuildSystem(project).equals(BuildSystem.Blaze)) {
      testPrefix = "--gunit";
    }
    // Disable colored output, to workaround parsing bug (CPP-10054)
    // Note: cc_test runner currently only supports GUnit tests.
    if (Kind.CC_TEST.equals(configuration.getKindForTarget())) {
      commandLine.addParameter(testPrefix + "_color=no");
    }

    String testFilter = convertToGUnitTestFilter(handlerState.getTestFilterFlag(), testPrefix);
    if (testFilter != null) {
      commandLine.addParameter(testFilter);
    }

    TrivialInstaller installer = new TrivialInstaller(commandLine);
    ImmutableList<String> startupCommands = getGdbStartupCommands(workingDir);
    CLionRunParameters parameters =
        new CLionRunParameters(
            new BlazeGDBDriverConfiguration(project, startupCommands, workspaceRoot), installer);

    state.setConsoleBuilder(createConsoleBuilder(null));
    state.addConsoleFilters(getConsoleFilters().toArray(new Filter[0]));
    return new CidrLocalDebugProcess(parameters, session, state.getConsoleBuilder());
  }

  /** Convert Blaze test filter to gunit/gtest test filter */
  @Nullable
  private static String convertToGUnitTestFilter(
      @Nullable String blazeTestFilter, String testPrefix) {
    if (blazeTestFilter == null || !blazeTestFilter.startsWith(BlazeFlags.TEST_FILTER)) {
      return null;
    }
    return testPrefix + "_filter" + blazeTestFilter.substring(BlazeFlags.TEST_FILTER.length());
  }

  @Override
  protected Project getProject() {
    return project;
  }

  private ImmutableList<Filter> getConsoleFilters() {
    return ImmutableList.of(new BlazeTargetFilter(project), new UrlFilter());
  }

  private CidrConsoleBuilder createConsoleBuilder(@Nullable BlazeTestUiSession testUiSession) {
    if (BlazeCommandName.TEST.equals(handlerState.getCommandState().getCommand())) {
      // hook up the test tree UI
      return new GoogleTestConsoleBuilder(configuration.getProject(), testUiSession);
    }
    return new CidrConsoleBuilder(
        configuration.getProject(), /* CidrToolEnvironment */ null, /* baseDir */ null);
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

  private boolean useTestUi() {
    return BlazeCommandName.TEST.equals(handlerState.getCommandState().getCommand());
  }

  private final class GoogleTestConsoleBuilder extends CidrConsoleBuilder {
    @Nullable private final BlazeTestUiSession testUiSession;

    private GoogleTestConsoleBuilder(Project project, @Nullable BlazeTestUiSession testUiSession) {
      super(project, /* CidrToolEnvironment */ null, /* baseDir */ null);
      this.testUiSession = testUiSession;
      addFilter(new BlazeCidrTestOutputFilter(project));
    }

    @Override
    protected ConsoleView createConsole() {
      if (testUiSession != null) {
        return SmRunnerUtils.getConsoleView(
            configuration.getProject(),
            configuration,
            executionEnvironment.getExecutor(),
            testUiSession);
      }
      // When debugging, we run gdb manually on the debug binary, so the blaze test runners aren't
      // involved.
      CidrGoogleTestConsoleProperties consoleProperties =
          new CidrGoogleTestConsoleProperties(
              configuration,
              executionEnvironment.getExecutor(),
              executionEnvironment.getExecutionTarget());
      return createConsole(configuration.getType(), consoleProperties);
    }
  }
}
