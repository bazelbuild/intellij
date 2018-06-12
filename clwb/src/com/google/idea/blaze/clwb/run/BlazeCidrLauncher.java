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
package com.google.idea.blaze.clwb.run;

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
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.ExecutorType;
import com.google.idea.blaze.base.run.filter.BlazeTargetFilter;
import com.google.idea.blaze.base.run.processhandler.LineProcessingProcessAdapter;
import com.google.idea.blaze.base.run.processhandler.ScopedBlazeProcessHandler;
import com.google.idea.blaze.base.run.smrunner.BlazeTestUiSession;
import com.google.idea.blaze.base.run.smrunner.SmRunnerUtils;
import com.google.idea.blaze.base.run.smrunner.TestUiSessionProvider;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.scopes.IssuesScope;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.google.idea.blaze.base.settings.BuildSystem;
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
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
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
  private final ExecutionEnvironment env;

  BlazeCidrLauncher(
      BlazeCommandRunConfiguration configuration,
      BlazeCidrRunConfigurationRunner runner,
      ExecutionEnvironment env) {
    this.configuration = configuration;
    this.handlerState = (BlazeCidrRunConfigState) configuration.getHandler().getState();
    this.runner = runner;
    this.env = env;
    this.project = configuration.getProject();
  }

  @Override
  public ProcessHandler createProcess(CommandLineState state) throws ExecutionException {
    ImmutableList<String> testHandlerFlags = ImmutableList.of();
    BlazeTestUiSession testUiSession =
        useTestUi()
            ? TestUiSessionProvider.getInstance(project).getTestUiSession(configuration.getTarget())
            : null;
    if (testUiSession != null) {
      testHandlerFlags = testUiSession.getBlazeFlags();
    }

    ProjectViewSet projectViewSet =
        Preconditions.checkNotNull(ProjectViewManager.getInstance(project).getProjectViewSet());

    List<String> fixedBlazeFlags = getFixedBlazeFlags();

    BlazeCommand.Builder command =
        BlazeCommand.builder(
                Blaze.getBuildSystemProvider(project).getBinaryPath(),
                handlerState.getCommandState().getCommand())
            .addTargets(configuration.getTarget())
            .addBlazeFlags(
                BlazeFlags.blazeFlags(
                    project,
                    projectViewSet,
                    handlerState.getCommandState().getCommand(),
                    BlazeInvocationContext.NonSync,
                    ExecutorType.fromExecutor(env.getExecutor())))
            .addBlazeFlags(testHandlerFlags)
            .addBlazeFlags(fixedBlazeFlags)
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
            context.push(
                new IssuesScope(
                    project, BlazeUserSettings.getInstance().getShowProblemsViewForRunAction()));
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

    if (Kind.CC_TEST.equals(configuration.getTargetKind())) {
      convertBlazeTestFilterToExecutableFlag().ifPresent(commandLine::addParameters);
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

  /** Get the correct test prefix for blaze/bazel */
  private String getTestFilterArgument() {
    if (Blaze.getBuildSystem(project).equals(BuildSystem.Blaze)) {
      return "--gunit_filter";
    }
    return "--gtest_filter";
  }

  /**
   * Fix test flags for Bazel which doesn't support --test_filter
   *
   * @return The list of bazel/blaze flags, fixed for the current execution environment
   */
  private List<String> getFixedBlazeFlags() {
    List<String> originalBlazeFlags = handlerState.getBlazeFlagsState().getExpandedFlags();

    // Flags are fine as-is in this case
    if (Blaze.getBuildSystem(project).equals(BuildSystem.Blaze)) {
      return originalBlazeFlags;
    }

    // Only manipulate flags for test configurations
    if (!Kind.CC_TEST.equals(configuration.getTargetKind())) {
      return originalBlazeFlags;
    }

    // bazel does not support --test_filter so we need to convert to a argument that will be passed
    // to the binary. Other flags should be passed through as-is
    String testArgument = "--test_arg=" + getTestFilterArgument();
    return originalBlazeFlags
        .stream()
        .map(
            flag -> {
              if (flag.startsWith(BlazeFlags.TEST_FILTER)) {
                flag = flag.replaceFirst(BlazeFlags.TEST_FILTER, testArgument);
              }
              return flag;
            })
        .collect(Collectors.toList());
  }

  /**
   * Convert blaze/bazel test filter to the equivalent executable flag
   *
   * @return An (Optional) flag to append to the executable's flag list
   */
  private Optional<String> convertBlazeTestFilterToExecutableFlag() {
    String testArgument = getTestFilterArgument();
    String testFilter = handlerState.getTestFilter();

    if (testFilter == null) {
      return Optional.empty();
    }

    return Optional.of(testArgument + "=" + testFilter);
  }

  @Override
  protected Project getProject() {
    return project;
  }

  private ImmutableList<Filter> getConsoleFilters() {
    return ImmutableList.of(
        new BlazeTargetFilter(project, true),
        new UrlFilter(),
        new IssueOutputFilter(
            project, WorkspaceRoot.fromProject(project), BlazeInvocationContext.NonSync, false));
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
            configuration.getProject(), configuration, env.getExecutor(), testUiSession);
      }
      // When debugging, we run gdb manually on the debug binary, so the blaze test runners aren't
      // involved.
      CidrGoogleTestConsoleProperties consoleProperties =
          new CidrGoogleTestConsoleProperties(
              configuration, env.getExecutor(), env.getExecutionTarget());
      return createConsole(configuration.getType(), consoleProperties);
    }
  }
}
