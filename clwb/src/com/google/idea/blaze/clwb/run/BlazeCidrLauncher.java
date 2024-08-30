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
import com.google.idea.blaze.base.bazel.BuildSystem.BuildInvoker;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.command.BlazeInvocationContext;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.console.BlazeConsoleLineProcessorProvider;
import com.google.idea.blaze.base.issueparser.ToolWindowTaskIssueOutputFilter;
import com.google.idea.blaze.base.logging.EventLoggingService;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.ExecutorType;
import com.google.idea.blaze.base.run.processhandler.LineProcessingProcessAdapter;
import com.google.idea.blaze.base.run.processhandler.ScopedBlazeProcessHandler;
import com.google.idea.blaze.base.run.smrunner.BlazeTestEventsHandler;
import com.google.idea.blaze.base.run.smrunner.BlazeTestUiSession;
import com.google.idea.blaze.base.run.smrunner.SmRunnerUtils;
import com.google.idea.blaze.base.run.testlogs.LocalBuildEventProtocolTestFinderStrategy;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.scopes.ProblemsViewScope;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.clwb.ToolchainUtils;
import com.google.idea.blaze.cpp.CppBlazeRules;
import com.google.idea.sdkcompat.clion.CidrDebugProcessCreator;
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
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.xdebugger.XDebugSession;
import com.jetbrains.cidr.execution.CidrConsoleBuilder;
import com.jetbrains.cidr.execution.CidrLauncher;
import com.jetbrains.cidr.execution.TrivialInstaller;
import com.jetbrains.cidr.execution.TrivialRunParameters;
import com.jetbrains.cidr.execution.debugger.CidrDebugProcess;
import com.jetbrains.cidr.execution.debugger.CidrLocalDebugProcess;
import com.jetbrains.cidr.execution.debugger.backend.DebuggerDriverConfiguration;
import com.jetbrains.cidr.execution.debugger.remote.CidrRemoteDebugParameters;
import com.jetbrains.cidr.execution.debugger.remote.CidrRemotePathMapping;
import com.jetbrains.cidr.execution.testing.google.CidrGoogleTestConsoleProperties;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;

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

  private static final String DISABLE_BAZEL_GOOGLETEST_FILTER_WARNING =
      "bazel.test_filter.googletest_update";

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
    return createProcess(state, ImmutableList.of());
  }

  private ProcessHandler createProcess(CommandLineState state, List<String> extraBlazeFlags)
      throws ExecutionException {
    ImmutableList<String> testHandlerFlags = ImmutableList.of();
    BlazeContext context = BlazeContext.create();
    BuildInvoker invoker =
        Blaze.getBuildSystemProvider(project).getBuildSystem().getBuildInvoker(project, context);
    BlazeTestUiSession testUiSession = null;
    if (useTestUi()
        && BlazeTestEventsHandler.targetsSupported(project, configuration.getTargets())) {
      try (BuildResultHelper buildResultHelper = invoker.createBuildResultHelper()) {
        testUiSession =
            BlazeTestUiSession.create(
                ImmutableList.<String>builder()
                    .add("--runs_per_test=1")
                    .add("--flaky_test_attempts=1")
                    .addAll(buildResultHelper.getBuildFlags())
                    .build(),
                new LocalBuildEventProtocolTestFinderStrategy(buildResultHelper));
      }
    }
    if (testUiSession != null) {
      testHandlerFlags = testUiSession.getBlazeFlags();
    }

    ProjectViewSet projectViewSet =
        Preconditions.checkNotNull(ProjectViewManager.getInstance(project).getProjectViewSet());

    if (shouldDisplayBazelTestFilterWarning()) {
      String messageContents =
          "<html>The Google Test framework did not apply test filtering correctly before "
              + "git commit <a href='https://github.com/google/googletest/commit/"
              + "ba96d0b1161f540656efdaed035b3c062b60e006"
              + "'>ba96d0b<a>.<br/>"
              + "Please ensure you are past this commit if you are using it.<br/><br/>"
              + "More information on the bazel <a href='https://github.com/bazelbuild/bazel/issues/"
              + "4411'>issue</a></html>";

      int selectedOption =
          Messages.showDialog(
              getProject(),
              messageContents,
              "Please update 'Google Test' past ba96d0b...",
              new String[] {"Close", "Don't show again"},
              0, // Default to "Close"
              Messages.getWarningIcon());
      if (selectedOption == 1) {
        PropertiesComponent.getInstance().setValue(DISABLE_BAZEL_GOOGLETEST_FILTER_WARNING, "true");
      }
    }

    BlazeCommand.Builder commandBuilder =
        BlazeCommand.builder(
                Blaze.getBuildSystemProvider(project).getBinaryPath(project),
                handlerState.getCommandState().getCommand())
            .addTargets(configuration.getTargets())
            .addBlazeFlags(extraBlazeFlags)
            .addBlazeFlags(
                BlazeFlags.blazeFlags(
                    project,
                    projectViewSet,
                    handlerState.getCommandState().getCommand(),
                    context,
                    BlazeInvocationContext.runConfigContext(
                        ExecutorType.fromExecutor(env.getExecutor()),
                        configuration.getType(),
                        false)))
            .addBlazeFlags(testHandlerFlags)
            .addBlazeFlags(handlerState.getBlazeFlagsState().getFlagsForExternalProcesses())
            .addExeFlags(handlerState.getExeFlagsState().getFlagsForExternalProcesses());

    state.setConsoleBuilder(createConsoleBuilder(testUiSession));
    state.addConsoleFilters(getConsoleFilters().toArray(new Filter[0]));

    WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProject(project);
    final BlazeCommand command = commandBuilder.build();

    final GeneralCommandLine commandLine = new GeneralCommandLine(command.toList());

    EnvironmentVariablesData envState = handlerState.getEnvVarsState().getData();
    commandLine.withParentEnvironmentType(
        envState.isPassParentEnvs() ? ParentEnvironmentType.SYSTEM : ParentEnvironmentType.NONE);
    commandLine.getEnvironment().putAll(envState.getEnvs());

    return new ScopedBlazeProcessHandler(
        project,
        commandLine,
        workspaceRoot,
        new ScopedBlazeProcessHandler.ScopedProcessHandlerDelegate() {
          @Override
          public void onBlazeContextStart(BlazeContext context) {
            context.push(
                new ProblemsViewScope(
                    project, BlazeUserSettings.getInstance().getShowProblemsViewOnRun()));
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

  @NotNull
  private File selectWorkingDir(@Nonnull Optional<Path> runConfigWorkingDir,
      File workspaceRootDirectory) {
    if (runConfigWorkingDir.isPresent()) {
      return runConfigWorkingDir.get().toFile();
    }

    File workingDir =
        new File(runner.executableToDebug + ".runfiles", workspaceRootDirectory.getName());

    if (workingDir.exists()) {
      return workingDir;
    }

    return workspaceRootDirectory;
  }

  @Override
  public CidrDebugProcess createDebugProcess(CommandLineState state, XDebugSession session)
      throws ExecutionException {
    TargetExpression target = configuration.getSingleTarget();
    if (target == null) {
      throw new ExecutionException("Cannot parse run configuration target.");
    }
    if (runner.executableToDebug == null) {
      throw new ExecutionException("No debug binary found.");
    }
    EventLoggingService.getInstance().logEvent(getClass(), "debugging-cpp");

    WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProject(project);

    final var debuggerKind = RunConfigurationUtils.getDebuggerKind(configuration);
    if (debuggerKind != BlazeDebuggerKind.GDB_SERVER) {

      Optional<Path> runConfigWorkingDir = handlerState.getWorkingDirState().getWorkingDirectory();
      File workingDir = selectWorkingDir(runConfigWorkingDir, workspaceRoot.directory());

      GeneralCommandLine commandLine =
          new GeneralCommandLine(runner.executableToDebug.getPath()).withWorkDirectory(workingDir);

      commandLine.addParameters(handlerState.getExeFlagsState().getFlagsForExternalProcesses());
      commandLine.addParameters(handlerState.getTestArgs());

      if (CppBlazeRules.RuleTypes.CC_TEST.getKind().equals(configuration.getTargetKind())) {
        convertBlazeTestFilterToExecutableFlag().ifPresent(commandLine::addParameters);
      }

      final DebuggerDriverConfiguration debuggerDriver;
      if (debuggerKind == BlazeDebuggerKind.BUNDLED_LLDB) {
        debuggerDriver = new BlazeLLDBDriverConfiguration(project, workspaceRoot);
      } else {
        final var startupCommands = getGdbStartupCommands(workspaceRoot.directory());
        debuggerDriver = new BlazeGDBDriverConfiguration(project, startupCommands, workspaceRoot);
      }

      final var parameters = new TrivialRunParameters(debuggerDriver, new TrivialInstaller(commandLine));

      state.setConsoleBuilder(createConsoleBuilder(null));
      state.addConsoleFilters(getConsoleFilters().toArray(new Filter[0]));
      return CidrDebugProcessCreator.create(() -> new CidrLocalDebugProcess(parameters, session, state.getConsoleBuilder()));
    }
    List<String> extraDebugFlags = BlazeGDBServerProvider.getFlagsForDebugging(handlerState);

    ProcessHandler targetProcess = createProcess(state, extraDebugFlags);

    configProcessHandler(targetProcess, false, true, getProject());

    targetProcess.startNotify();

    // CidrRemoteDebugParameters can't be constructed with a null sysroot, so pass in the default
    // value "target:". Causes paths/files to be resolved in the context of the target.
    CidrRemoteDebugParameters parameters =
        new CidrRemoteDebugParameters(
            "tcp:localhost:" + handlerState.getDebugPortState().port,
            runner.executableToDebug.getPath(),
            "target:",
            ImmutableList.of(
                new CidrRemotePathMapping("/proc/self/cwd", workspaceRoot.directory().getParent())));

    BlazeCLionGDBDriverConfiguration debuggerDriverConfiguration =
        new BlazeCLionGDBDriverConfiguration(project);

    return CidrDebugProcessCreator.create(() -> new BlazeCidrRemoteDebugProcess(
        targetProcess, debuggerDriverConfiguration, parameters, session, state.getConsoleBuilder()));
  }

  /** Get the correct test prefix for blaze/bazel */
  private String getTestFilterArgument() {
    if (Blaze.getBuildSystemName(project).equals(BuildSystemName.Blaze)) {
      return "--gunit_filter";
    }
    return "--gtest_filter";
  }

  private boolean shouldDisplayBazelTestFilterWarning() {
    return Blaze.getBuildSystemName(getProject()).equals(BuildSystemName.Bazel)
        && CppBlazeRules.RuleTypes.CC_TEST.getKind().equals(configuration.getTargetKind())
        && handlerState.getTestFilterFlag() != null
        && !PropertiesComponent.getInstance()
            .getBoolean(DISABLE_BAZEL_GOOGLETEST_FILTER_WARNING, false)
        && GoogleTestUtilAdapter.findGoogleTestSymbol(getProject()) != null;
  }

  /**
   * Convert blaze/bazel test filter to the equivalent executable flag
   *
   * @return An (Optional) flag to append to the executable's flag list
   */
  private Optional<String> convertBlazeTestFilterToExecutableFlag() {
    String testArgument = getTestFilterArgument();
    String testFilter = handlerState.getTestFilterFlag();

    if (testFilter == null) {
      return Optional.empty();
    }

    return Optional.of(testFilter.replaceFirst(BlazeFlags.TEST_FILTER, testArgument));
  }

  @Override
  public Project getProject() {
    return project;
  }

  private ImmutableList<Filter> getConsoleFilters() {
    return ImmutableList.of(
        new UrlFilter(),
        ToolWindowTaskIssueOutputFilter.createWithDefaultParsers(
            project,
            WorkspaceRoot.fromProject(project),
            BlazeInvocationContext.ContextType.RunConfiguration));
  }

  private CidrConsoleBuilder createConsoleBuilder(@Nullable BlazeTestUiSession testUiSession) {
    if (BlazeCommandName.TEST.equals(handlerState.getCommandState().getCommand())) {
      // hook up the test tree UI
      return new GoogleTestConsoleBuilder(configuration.getProject(), testUiSession);
    }
    return new CidrConsoleBuilder(configuration.getProject(), null, null);
  }

  private ImmutableList<String> getGdbStartupCommands(File workspaceRootDirectory) {
    // Forge creates debug symbol paths rooted at /proc/self/cwd .
    // We need to tell gdb to translate this path prefix to the user's workspace
    // root so the IDE can find the files.
    String from = "/proc/self/cwd";
    String to = workspaceRootDirectory.getPath();
    String subPathCommand = String.format("set substitute-path %s %s", from, to);

    return ImmutableList.of(subPathCommand);
  }

  private boolean useTestUi() {
    return BlazeCommandName.TEST.equals(handlerState.getCommandState().getCommand());
  }

  private final class GoogleTestConsoleBuilder extends CidrConsoleBuilder {
    @Nullable private final BlazeTestUiSession testUiSession;

    private GoogleTestConsoleBuilder(Project project, @Nullable BlazeTestUiSession testUiSession) {
      super(project, null, null);
      this.testUiSession = testUiSession;
      addFilter(new BlazeCidrTestOutputFilter(project));
    }

    @Override
    protected ConsoleView createConsole() {
      if (testUiSession != null) {
        return SmRunnerUtils.getConsoleView(
            configuration.getProject(), configuration, env.getExecutor(), testUiSession);
      }
      // when launching GDB directly the blaze test runners aren't involved
      CidrGoogleTestConsoleProperties consoleProperties =
          new CidrGoogleTestConsoleProperties(
              configuration, env.getExecutor(), env.getExecutionTarget());
      return createConsole(configuration.getType(), consoleProperties);
    }
  }
}
