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
import com.google.idea.blaze.base.logging.EventLoggingService;
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
import com.google.idea.blaze.base.run.state.RunConfigurationState;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.scopes.IssuesScope;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.google.idea.blaze.base.settings.BuildSystem;
import com.google.idea.blaze.clwb.CidrGoogleTestUtilAdapter;
import com.google.idea.blaze.clwb.ToolchainUtils;
import com.google.idea.blaze.cpp.CppBlazeRules;
import com.google.idea.common.experiments.BoolExperiment;
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
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.PathUtil;
import com.intellij.xdebugger.XDebugSession;
import com.jetbrains.cidr.cpp.execution.CLionRunParameters;
import com.jetbrains.cidr.cpp.execution.debugger.backend.GDBDriverConfiguration;
import com.jetbrains.cidr.cpp.toolchains.CPPDebugger;
import com.jetbrains.cidr.cpp.toolchains.CPPToolSet;
import com.jetbrains.cidr.cpp.toolchains.CPPToolSetWithHome;
import com.jetbrains.cidr.cpp.toolchains.CPPToolchains;
import com.jetbrains.cidr.cpp.toolchains.CPPToolchains.Toolchain;
import com.jetbrains.cidr.execution.CidrConsoleBuilder;
import com.jetbrains.cidr.execution.TrivialInstaller;
import com.jetbrains.cidr.execution.debugger.CidrDebugProcess;
import com.jetbrains.cidr.execution.debugger.CidrDebuggerPathManager;
import com.jetbrains.cidr.execution.debugger.CidrLocalDebugProcess;
import com.jetbrains.cidr.execution.debugger.backend.DebuggerDriverConfiguration;
import com.jetbrains.cidr.execution.debugger.remote.CidrRemoteDebugParameters;
import com.jetbrains.cidr.execution.debugger.remote.CidrRemotePathMapping;
import com.jetbrains.cidr.execution.testing.CidrLauncher;
import com.jetbrains.cidr.execution.testing.google.CidrGoogleTestConsoleProperties;
import com.jetbrains.cidr.lang.toolchains.CidrToolEnvironment.PrepareFor;
import com.jetbrains.cidr.toolchains.OSType;
import java.io.File;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Handles running/debugging cc_test and cc_binary targets in CLion. Sets up gdb when debugging, and
 * uses the Google Test infrastructure for presenting test results.
 */
public final class BlazeCidrLauncher extends CidrLauncher {

  private static final NotNullLazyValue<String> GDBSERVER_WRAPPER =
      new NotNullLazyValue<String>() {
        @Override
        protected String compute() {
          String jarPath = PathUtil.getJarPathForClass(BlazeCidrLauncher.class);
          File pluginrootDirectory = new File(jarPath).getParentFile().getParentFile();
          return new File(pluginrootDirectory, "gdb/gdbserver").getPath();
        }
      };

  private final Project project;
  private final BlazeCommandRunConfiguration configuration;
  private final BlazeCidrRunConfigState handlerState;
  private final BlazeCidrRunConfigurationRunner runner;
  private final ExecutionEnvironment env;

  private static final String DISABLE_BAZEL_GOOGLETEST_FILTER_WARNING =
      "bazel.test_filter.googletest_update";

  static final BoolExperiment useRemoteDebugging = new BoolExperiment("cc.remote.debugging", true);

  private static final BoolExperiment useRemoteDebuggingWrapper =
      new BoolExperiment("cc.remote.debugging.wrapper", true);

  // These flags are used when debugging cc_binary targets when remote debugging
  // is enabled (cc.remote.debugging)
  private static final ImmutableList<String> EXTRA_FLAGS_FOR_DEBUG_RUN =
      ImmutableList.of(
          "--compilation_mode=dbg",
          "--strip=never",
          "--copt=-g",
          "--dynamic_mode=off",
          "--fission=yes");

  // These flags are used when debugging cc_test targets when remote debugging
  // is enabled (cc.remote.debugging)
  private static final ImmutableList<String> EXTRA_FLAGS_FOR_DEBUG_TEST =
      ImmutableList.of(
          "--compilation_mode=dbg",
          "--strip=never",
          "--copt=-g",
          "--dynamic_mode=off",
          "--fission=yes",
          "--test_timeout=3600",
          BlazeFlags.NO_CACHE_TEST_RESULTS,
          BlazeFlags.EXCLUSIVE_TEST_EXECUTION,
          BlazeFlags.DISABLE_TEST_SHARDING);

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
    return createProcess(state, ImmutableList.of(), false);
  }

  private ProcessHandler createProcess(
      CommandLineState state, List<String> extraBlazeFlags, boolean isInferiorProcess)
      throws ExecutionException {
    ImmutableList<String> testHandlerFlags = ImmutableList.of();
    BlazeTestUiSession testUiSession =
        !isInferiorProcess && useTestUi()
            ? TestUiSessionProvider.getInstance(project).getTestUiSession(configuration.getTarget())
            : null;
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
            .addTargets(configuration.getTarget())
            .addBlazeFlags(extraBlazeFlags)
            .addBlazeFlags(
                BlazeFlags.blazeFlags(
                    project,
                    projectViewSet,
                    handlerState.getCommandState().getCommand(),
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

    return new ScopedBlazeProcessHandler(
        project,
        command,
        workspaceRoot,
        new ScopedBlazeProcessHandler.ScopedProcessHandlerDelegate() {
          @Override
          public void onBlazeContextStart(BlazeContext context) {
            context.push(
                new IssuesScope(
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

  static ImmutableList<String> getExtraFlagsForDebugging(RunConfigurationState state) {
    if (!(state instanceof BlazeCidrRunConfigState)) {
      return ImmutableList.of();
    }
    BlazeCidrRunConfigState handlerState = (BlazeCidrRunConfigState) state;
    BlazeCommandName commandName = handlerState.getCommandState().getCommand();
    ImmutableList.Builder<String> builder = ImmutableList.builder();

    if (useRemoteDebuggingWrapper.getValue()) {
      String runUnderOption =
          String.format(
              "--run_under='%s' gdbserver --once localhost:%d --target",
              GDBSERVER_WRAPPER.getValue(), handlerState.getDebugPortState().port);
      builder.add(runUnderOption);
    } else {
      String runUnderOption =
          String.format(
              "--run_under=gdbserver --once localhost:%d", handlerState.getDebugPortState().port);
      builder.add(runUnderOption);
    }
    if (BlazeCommandName.RUN.equals(commandName)) {
      builder.addAll(EXTRA_FLAGS_FOR_DEBUG_RUN);
      return builder.build();
    }
    if (BlazeCommandName.TEST.equals(commandName)) {
      builder.addAll(EXTRA_FLAGS_FOR_DEBUG_TEST);
      return builder.build();
    }
    return ImmutableList.of();
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
    EventLoggingService.getInstance().logEvent(getClass(), "debugging-cpp");

    WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProject(project);
    File workspaceRootDirectory = workspaceRoot.directory();

    // Mac does not have gdbserver, so use the old gdb method for debugging
    if (!useRemoteDebugging.getValue() || SystemInfo.isMac) {

      File workingDir =
          new File(runner.executableToDebug + ".runfiles", workspaceRootDirectory.getName());

      if (!workingDir.exists()) {
        workingDir = workspaceRootDirectory;
      }

      GeneralCommandLine commandLine = new GeneralCommandLine(runner.executableToDebug.getPath());

      commandLine.setWorkDirectory(workingDir);
      commandLine.addParameters(handlerState.getExeFlagsState().getFlagsForExternalProcesses());
      commandLine.addParameters(handlerState.getTestArgs());

      EnvironmentVariablesData envState = handlerState.getEnvVarsState().getData();
      commandLine.withParentEnvironmentType(
          envState.isPassParentEnvs() ? ParentEnvironmentType.SYSTEM : ParentEnvironmentType.NONE);
      commandLine.getEnvironment().putAll(envState.getEnvs());

      if (CppBlazeRules.RuleTypes.CC_TEST.getKind().equals(configuration.getTargetKind())) {
        convertBlazeTestFilterToExecutableFlag().ifPresent(commandLine::addParameters);
      }

      TrivialInstaller installer = new TrivialInstaller(commandLine);
      ImmutableList<String> startupCommands = getGdbStartupCommands(workspaceRootDirectory);
      CLionRunParameters parameters =
          new CLionRunParameters(
              new BlazeGDBDriverConfiguration(project, startupCommands, workspaceRoot), installer);

      state.setConsoleBuilder(createConsoleBuilder(null));
      state.addConsoleFilters(getConsoleFilters().toArray(new Filter[0]));
      return new CidrLocalDebugProcess(parameters, session, state.getConsoleBuilder());
    }

    List<String> extraDebugFlags = getExtraFlagsForDebugging(handlerState);

    ProcessHandler targetProcess = createProcess(state, extraDebugFlags, true);

    configProcessHandler(targetProcess, false, true, getProject());

    targetProcess.startNotify();

    CidrRemoteDebugParameters parameters =
        new CidrRemoteDebugParameters(
            "tcp:localhost:" + handlerState.getDebugPortState().port,
            runner.executableToDebug.getPath(),
            workspaceRootDirectory.getPath(),
            ImmutableList.of(
                new CidrRemotePathMapping("/proc/self/cwd", workspaceRootDirectory.getParent())));

    CPPToolchains.Toolchain toolchainForDebugger =
        new Toolchain(OSType.getCurrent()) {
          private final CPPToolSet blazeToolSet = new BlazeToolSet(workspaceRootDirectory);

          @Override
          public CPPToolSet getToolSet() {
            return blazeToolSet;
          }
        };

    ToolchainUtils.setDebuggerToDefault(toolchainForDebugger);

    DebuggerDriverConfiguration debuggerDriverConfiguration =
        new GDBDriverConfiguration(project, toolchainForDebugger);

    return new BlazeCidrRemoteDebugProcess(
        targetProcess, debuggerDriverConfiguration, parameters, session, state.getConsoleBuilder());
  }

  /**
   * There is currently no way to override the working directory for the debug process when we
   * create it. By creating a CPPToolSet, we have an opportunity to alter the commandline before it
   * launches. See https://youtrack.jetbrains.com/issue/CPP-8362
   */
  private static class BlazeToolSet extends CPPToolSetWithHome {
    private static final char[] separators = {'/'};

    private BlazeToolSet(File workingDirectory) {
      super(Kind.MINGW, workingDirectory);
    }

    @Override
    public String readVersion() {
      return "no version";
    }

    @Override
    public String checkVersion(String s) {
      return null;
    }

    @Override
    public char[] getSupportedFileSeparators() {
      return separators;
    }

    @Override
    public File getGDBPath() {
      Toolchain toolchain = ToolchainUtils.getToolchain();
      if (toolchain.getDebuggerKind() == CPPDebugger.Kind.BUNDLED_GDB) {
        return CidrDebuggerPathManager.getBundledGDBBinary();
      }

      String gdbPath = toolchain.getCustomGDBExecutablePath();
      if (gdbPath == null) {
        return CidrDebuggerPathManager.getBundledGDBBinary();
      }
      File gdbFile = new File(gdbPath);
      if (!gdbFile.exists()) {
        return CidrDebuggerPathManager.getBundledGDBBinary();
      }
      return gdbFile;
    }

    @Override
    public void prepareEnvironment(
        GeneralCommandLine cl, PrepareFor prepareFor, List<CPPToolSet.Option> options)
        throws ExecutionException {
      super.prepareEnvironment(cl, prepareFor, options);
      if (prepareFor.equals(PrepareFor.RUN)) {
        cl.setWorkDirectory(super.getHome());
      }
    }

    @Override
    public boolean supportsDebugger(CPPDebugger.Kind kind) {
      return kind == CPPDebugger.Kind.CUSTOM_GDB;
    }
  }

  /** Get the correct test prefix for blaze/bazel */
  private String getTestFilterArgument() {
    if (Blaze.getBuildSystem(project).equals(BuildSystem.Blaze)) {
      return "--gunit_filter";
    }
    return "--gtest_filter";
  }

  private boolean shouldDisplayBazelTestFilterWarning() {
    return Blaze.getBuildSystem(getProject()).equals(BuildSystem.Bazel)
        && CppBlazeRules.RuleTypes.CC_TEST.getKind().equals(configuration.getTargetKind())
        && handlerState.getTestFilterFlag() != null
        && !PropertiesComponent.getInstance()
            .getBoolean(DISABLE_BAZEL_GOOGLETEST_FILTER_WARNING, false)
        && CidrGoogleTestUtilAdapter.findGoogleTestSymbol(getProject()) != null;
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
        new BlazeTargetFilter(true),
        new UrlFilter(),
        new IssueOutputFilter(
            project,
            WorkspaceRoot.fromProject(project),
            BlazeInvocationContext.ContextType.RunConfiguration,
            false));
  }

  private CidrConsoleBuilder createConsoleBuilder(@Nullable BlazeTestUiSession testUiSession) {
    if (BlazeCommandName.TEST.equals(handlerState.getCommandState().getCommand())) {
      // hook up the test tree UI
      return new GoogleTestConsoleBuilder(configuration.getProject(), testUiSession);
    }
    return new CidrConsoleBuilder(
        configuration.getProject(), /* CidrToolEnvironment */ null, /* baseDir */ null);
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
      // when launching GDB directly the blaze test runners aren't involved
      CidrGoogleTestConsoleProperties consoleProperties =
          new CidrGoogleTestConsoleProperties(
              configuration, env.getExecutor(), env.getExecutionTarget());
      return createConsole(configuration.getType(), consoleProperties);
    }
  }
}
