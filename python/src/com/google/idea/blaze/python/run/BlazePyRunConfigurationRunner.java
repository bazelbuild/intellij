/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.python.run;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.run.BlazeBeforeRunCommandHelper;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.ExecutorType;
import com.google.idea.blaze.base.run.WithBrowserHyperlinkExecutionException;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandGenericRunConfigurationRunner.BlazeCommandRunProfileState;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationRunner;
import com.google.idea.blaze.base.run.filter.BlazeTargetFilter;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.google.idea.blaze.base.sync.aspects.BuildResult;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.util.SaveUtil;
import com.google.idea.blaze.python.PySdkUtils;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.RunCanceledByUserException;
import com.intellij.execution.configuration.EnvironmentVariablesData;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.WrappingRunConfiguration;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.UrlFilter;
import com.intellij.execution.process.KillableProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.util.PathUtil;
import com.intellij.util.execution.ParametersListUtil;
import com.jetbrains.python.console.PyDebugConsoleBuilder;
import com.jetbrains.python.console.PythonDebugLanguageConsoleView;
import com.jetbrains.python.run.CommandLinePatcher;
import com.jetbrains.python.run.PythonConfigurationType;
import com.jetbrains.python.run.PythonRunConfiguration;
import com.jetbrains.python.run.PythonScriptCommandLineState;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** Python-specific run configuration runner. */
public class BlazePyRunConfigurationRunner implements BlazeCommandRunConfigurationRunner {

  /** This inserts flags provided by any BlazePyDebugHelpers to the pydevd.py invocation */

  /** Used to store a runner to an {@link ExecutionEnvironment}. */
  private static final Key<AtomicReference<File>> EXECUTABLE_KEY =
      Key.create("blaze.debug.py.executable");

  /** Converts to the native python plugin debug configuration state */
  static class BlazePyDummyRunProfileState implements RunProfileState {
    final BlazeCommandRunConfiguration configuration;

    BlazePyDummyRunProfileState(BlazeCommandRunConfiguration configuration) {
      this.configuration = configuration;
    }

    PythonScriptCommandLineState toNativeState(ExecutionEnvironment env) throws ExecutionException {
      File executable = env.getCopyableUserData(EXECUTABLE_KEY).get();
      if (executable == null || StringUtil.isEmptyOrSpaces(executable.getPath())) {
        throw new ExecutionException("No blaze output script found");
      }
      PythonRunConfiguration nativeConfig =
          (PythonRunConfiguration)
              PythonConfigurationType.getInstance()
                  .getFactory()
                  .createTemplateConfiguration(env.getProject());
      nativeConfig.setScriptName(executable.getPath());
      nativeConfig.setAddContentRoots(false);
      nativeConfig.setAddSourceRoots(false);
      nativeConfig.setWorkingDirectory(
          Strings.nullToEmpty(
              getRunfilesPath(executable, WorkspaceRoot.fromProjectSafe(env.getProject()))));

      Sdk sdk = PySdkUtils.getPythonSdk(env.getProject());
      if (sdk == null) {
        throw new ExecutionException("Can't find a Python SDK when debugging a python target.");
      }
      nativeConfig.setModule(null);
      nativeConfig.setSdkHome(sdk.getHomePath());

      BlazePyRunConfigState handlerState =
          configuration.getHandlerStateIfType(BlazePyRunConfigState.class);
      if (handlerState != null) {
        nativeConfig.setScriptParameters(Strings.emptyToNull(getScriptParams(handlerState)));

        EnvironmentVariablesData envState = handlerState.getEnvVarsState().getData();
        nativeConfig.setPassParentEnvs(envState.isPassParentEnvs());
        nativeConfig.setEnvs(envState.getEnvs());
      }
      return new PythonScriptCommandLineState(nativeConfig, env) {

        private final CommandLinePatcher applyHelperPydevFlags =
            (commandLine) -> {
              BlazePyDebugHelper.doBlazeDebugCommandlinePatching(
                  nativeConfig.getProject(), configuration.getTarget(), commandLine);
            };

        @Override
        protected ProcessHandler startProcess(
            PythonProcessStarter starter, @Nullable CommandLinePatcher... patchers)
            throws ExecutionException {
          // Need to run after the other CommandLinePatchers
          List<CommandLinePatcher> modifiedPatchers = new ArrayList<>();
          if (patchers != null) {
            Collections.addAll(modifiedPatchers, patchers);
          }
          modifiedPatchers.add(applyHelperPydevFlags);
          return super.startProcess(starter, modifiedPatchers.toArray(new CommandLinePatcher[0]));
        }

        @Override
        public boolean isDebug() {
          return true;
        }

        @Override
        protected ConsoleView createAndAttachConsole(
            Project project, ProcessHandler processHandler, Executor executor)
            throws ExecutionException {
          ConsoleView consoleView = createConsoleBuilder(project, getSdk()).getConsole();
          consoleView.addMessageFilter(createUrlFilter(processHandler));

          consoleView.attachToProcess(processHandler);
          return consoleView;
        }

        @Override
        protected ProcessHandler doCreateProcess(GeneralCommandLine commandLine)
            throws ExecutionException {
          ProcessHandler handler = super.doCreateProcess(commandLine);
          if (handler instanceof KillableProcessHandler) {
            // SIGINT can cause the JVM to crash, when stopped at a breakpoint (IDEA-167432).
            ((KillableProcessHandler) handler).setShouldKillProcessSoftly(false);
          }
          return handler;
        }
      };
    }

    @Nullable
    @Override
    public ExecutionResult execute(Executor executor, ProgramRunner runner)
        throws ExecutionException {
      return null;
    }

    private static TextConsoleBuilder createConsoleBuilder(Project project, Sdk sdk) {
      return new PyDebugConsoleBuilder(project, sdk) {
        @Override
        protected ConsoleView createConsole() {
          PythonDebugLanguageConsoleView consoleView =
              new PythonDebugLanguageConsoleView(project, sdk);
          for (Filter filter : getFilters(project)) {
            consoleView.addMessageFilter(filter);
          }
          return consoleView;
        }
      };
    }

    private static String getScriptParams(BlazeCommandRunConfigurationCommonState state) {
      List<String> params = Lists.newArrayList(state.getExeFlagsState().getExpandedFlags());
      String filterFlag = state.getTestFilterFlag();
      if (filterFlag != null) {
        params.add(filterFlag.substring((BlazeFlags.TEST_FILTER + "=").length()));
      }
      return ParametersListUtil.join(params);
    }
  }

  private static ImmutableList<Filter> getFilters(Project project) {
    return ImmutableList.<Filter>builder()
        .add(new BlazeTargetFilter(project, true))
        .add(new UrlFilter())
        .build();
  }

  @Override
  public RunProfileState getRunProfileState(Executor executor, ExecutionEnvironment env) {
    BlazeCommandRunConfiguration configuration = getConfiguration(env);
    if (!BlazeCommandRunConfigurationRunner.isDebugging(env)
        || BlazeCommandRunConfigurationRunner.getBlazeCommand(env) == BlazeCommandName.BUILD) {
      return new BlazeCommandRunProfileState(env);
    }
    env.putCopyableUserData(EXECUTABLE_KEY, new AtomicReference<>());
    return new BlazePyDummyRunProfileState(configuration);
  }

  @Override
  public boolean executeBeforeRunTask(ExecutionEnvironment env) {
    if (!BlazeCommandRunConfigurationRunner.isDebugging(env)) {
      return true;
    }
    env.getCopyableUserData(EXECUTABLE_KEY).set(null);
    try {
      File executable = getExecutableToDebug(env);
      env.getCopyableUserData(EXECUTABLE_KEY).set(executable);
      if (executable != null) {
        return true;
      }
    } catch (ExecutionException e) {
      ExecutionUtil.handleExecutionError(
          env.getProject(), env.getExecutor().getToolWindowId(), env.getRunProfile(), e);
    }
    return false;
  }

  private static BlazeCommandRunConfiguration getConfiguration(ExecutionEnvironment environment) {
    RunProfile runProfile = environment.getRunProfile();
    if (runProfile instanceof WrappingRunConfiguration) {
      runProfile = ((WrappingRunConfiguration) runProfile).getPeer();
    }
    return (BlazeCommandRunConfiguration) runProfile;
  }

  /** Make a best-effort attempt to get the runfiles path. Returns null if it can't be found. */
  @Nullable
  private static String getRunfilesPath(File executable, @Nullable WorkspaceRoot root) {
    if (root == null) {
      return null;
    }
    String workspaceName = root.directory().getName();
    File expectedPath = new File(executable.getPath() + ".runfiles", workspaceName);
    if (FileOperationProvider.getInstance().exists(expectedPath)) {
      return expectedPath.getPath();
    }
    return null;
  }

  /**
   * Builds blaze python target and returns the output build artifact.
   *
   * @throws ExecutionException if the target cannot be debugged.
   */
  private static File getExecutableToDebug(ExecutionEnvironment env) throws ExecutionException {
    BlazeCommandRunConfiguration configuration = getConfiguration(env);
    Project project = configuration.getProject();
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (blazeProjectData == null) {
      throw new ExecutionException("Not synced yet, please sync project");
    }

    String validationError =
        BlazePyDebugHelper.validateDebugTarget(env.getProject(), configuration.getTarget());
    if (validationError != null) {
      throw new WithBrowserHyperlinkExecutionException(validationError);
    }

    try (BuildResultHelper buildResultHelper = BuildResultHelper.forFiles(file -> true)) {

      ListenableFuture<BuildResult> buildOperation =
          BlazeBeforeRunCommandHelper.runBlazeBuild(
              configuration,
              buildResultHelper,
              BlazePyDebugHelper.getAllBlazeDebugFlags(
                  configuration.getProject(), configuration.getTarget()),
              ImmutableList.of(),
              ExecutorType.fromExecutor(env.getExecutor()),
              "Building debug binary");

      try {
        SaveUtil.saveAllFiles();
        BuildResult result = buildOperation.get();
        if (result.status != BuildResult.Status.SUCCESS) {
          throw new ExecutionException("Blaze failure building debug binary");
        }
      } catch (InterruptedException | CancellationException e) {
        buildOperation.cancel(true);
        throw new RunCanceledByUserException();
      } catch (java.util.concurrent.ExecutionException e) {
        throw new ExecutionException(e);
      }
      List<File> candidateFiles =
          buildResultHelper
              .getBuildArtifactsForTarget((Label) configuration.getTarget())
              .stream()
              .filter(File::canExecute)
              .collect(Collectors.toList());
      if (candidateFiles.isEmpty()) {
        throw new ExecutionException(
            String.format("No output artifacts found when building %s", configuration.getTarget()));
      }
      File file = findExecutable((Label) configuration.getTarget(), candidateFiles);
      if (file == null) {
        throw new ExecutionException(
            String.format(
                "More than 1 executable was produced when building %s; "
                    + "don't know which one to debug",
                configuration.getTarget()));
      }
      LocalFileSystem.getInstance().refreshIoFiles(ImmutableList.of(file));
      return file;
    }
  }

  /**
   * Basic heuristic for choosing between multiple output files. Currently just looks for a filename
   * matching the target name.
   */
  @VisibleForTesting
  @Nullable
  static File findExecutable(Label target, List<File> outputs) {
    if (outputs.size() == 1) {
      return outputs.get(0);
    }
    String name = PathUtil.getFileName(target.targetName().toString());
    for (File file : outputs) {
      if (file.getName().equals(name)) {
        return file;
      }
    }
    return null;
  }
}
