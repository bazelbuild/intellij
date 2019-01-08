/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.golang.run;

import com.goide.execution.application.GoApplicationConfiguration;
import com.goide.execution.application.GoApplicationConfiguration.Kind;
import com.goide.execution.application.GoApplicationRunConfigurationType;
import com.goide.execution.application.GoApplicationRunningState;
import com.goide.util.GoExecutor;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeInvocationContext;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper.GetArtifactsException;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelperProvider;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.run.BlazeBeforeRunCommandHelper;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.ExecutorType;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandGenericRunConfigurationRunner.BlazeCommandRunProfileState;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationRunner;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BuildSystem;
import com.google.idea.blaze.base.sync.aspects.BuildResult;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.util.SaveUtil;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.RunCanceledByUserException;
import com.intellij.execution.RunManager;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.util.PathUtil;
import com.intellij.util.execution.ParametersListUtil;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** Go-specific run configuration runner. */
public class BlazeGoRunConfigurationRunner implements BlazeCommandRunConfigurationRunner {

  /** Used to store a runner to an {@link ExecutionEnvironment}. */
  private static final Key<AtomicReference<File>> EXECUTABLE_KEY =
      Key.create("blaze.debug.golang.executable");

  private static final Logger logger = Logger.getInstance(BlazeGoRunConfigurationRunner.class);

  /** Converts to the native go plugin debug configuration state */
  static class BlazeGoDummyDebugProfileState implements RunProfileState {
    private final BlazeCommandRunConfiguration configuration;
    private final BlazeCommandRunConfigurationCommonState state;

    BlazeGoDummyDebugProfileState(BlazeCommandRunConfiguration configuration)
        throws ExecutionException {
      this.configuration = configuration;
      this.state =
          configuration.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
      if (this.state == null) {
        throw new ExecutionException("Missing run configuration common state");
      }
    }

    private static File getExecutable(ExecutionEnvironment env) {
      return env.getCopyableUserData(EXECUTABLE_KEY).get();
    }

    private ImmutableList<String> getParameters() {
      return ImmutableList.<String>builder()
          .addAll(state.getExeFlagsState().getExpandedFlags())
          .addAll(state.getTestArgs())
          .build();
    }

    /**
     * Similar to {@link com.google.idea.blaze.python.run.BlazePyRunConfigurationRunner}.
     *
     * <p>Working directory should be the runfiles directory of the debug executable.
     *
     * <p>If the runfiles directory does not exist (unlikely) fall back to workspace root.
     */
    private static String getWorkingDirectory(Project project, File executable)
        throws ExecutionException {
      WorkspaceRoot root;
      try {
        root = WorkspaceRoot.fromProject(project);
      } catch (IllegalStateException e) {
        throw new ExecutionException(e);
      }
      String workspaceName = root.directory().getName();
      File expectedPath = new File(executable.getPath() + ".runfiles", workspaceName);
      if (FileOperationProvider.getInstance().isDirectory(expectedPath)) {
        return expectedPath.getPath();
      }
      return root.directory().getPath();
    }

    @Nullable
    private String getTestFilter() {
      return state.getTestFilter();
    }

    GoApplicationRunningState toNativeState(ExecutionEnvironment env) throws ExecutionException {
      File executable = getExecutable(env);
      if (executable == null || StringUtil.isEmptyOrSpaces(executable.getPath())) {
        throw new ExecutionException("Blaze output binary not found");
      }
      Project project = env.getProject();
      BlazeProjectData projectData =
          BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
      if (projectData == null) {
        throw new ExecutionException("Project data not found");
      }
      Label label = (Label) configuration.getTarget();
      TargetKey key = TargetKey.forPlainTarget(label);
      TargetIdeInfo target = projectData.getTargetMap().get(key);
      if (target == null
          || target.getGoIdeInfo() == null
          || target.getGoIdeInfo().getImportPath() == null) {
        throw new ExecutionException("Go target import path not found");
      }

      GoApplicationConfiguration nativeConfig =
          (GoApplicationConfiguration)
              GoApplicationRunConfigurationType.getInstance()
                  .getConfigurationFactories()[0]
                  .createTemplateConfiguration(project, RunManager.getInstance(project));
      nativeConfig.setKind(Kind.PACKAGE);
      nativeConfig.setPackage(target.getGoIdeInfo().getImportPath());
      nativeConfig.setOutputDirectory(null);
      nativeConfig.setParams(ParametersListUtil.join(getParameters()));
      nativeConfig.setWorkingDirectory(getWorkingDirectory(project, executable));

      String testFilter = getTestFilter();
      if (testFilter != null) {
        Map<String, String> customEnvironment = new HashMap<>(nativeConfig.getCustomEnvironment());
        customEnvironment.put("TESTBRIDGE_TEST_ONLY", testFilter);
        nativeConfig.setCustomEnvironment(customEnvironment);
      }

      Module module =
          ModuleManager.getInstance(project)
              .findModuleByName(BlazeDataStorage.WORKSPACE_MODULE_NAME);
      if (module == null) {
        throw new ExecutionException("Workspace module not found");
      }
      GoApplicationRunningState nativeState =
          new GoApplicationRunningState(env, module, nativeConfig) {
            @Override
            public boolean isDebug() {
              return true;
            }

            @Nullable
            @Override
            public List<String> getBuildingTarget() {
              return null;
            }

            @Nullable
            @Override
            public GoExecutor createBuildExecutor() {
              return null;
            }
          };
      nativeState.setOutputFilePath(executable.getPath());
      return nativeState;
    }

    @Nullable
    @Override
    @SuppressWarnings("rawtypes")
    public ExecutionResult execute(Executor executor, ProgramRunner runner) {
      return null;
    }
  }

  @Override
  public RunProfileState getRunProfileState(Executor executor, ExecutionEnvironment env)
      throws ExecutionException {
    BlazeCommandRunConfiguration configuration =
        BlazeCommandRunConfigurationRunner.getConfiguration(env);
    if (!BlazeCommandRunConfigurationRunner.isDebugging(env)
        || Objects.equal(
            BlazeCommandRunConfigurationRunner.getBlazeCommand(env), BlazeCommandName.BUILD)) {
      return new BlazeCommandRunProfileState(env);
    }
    env.putCopyableUserData(EXECUTABLE_KEY, new AtomicReference<>());
    return new BlazeGoDummyDebugProfileState(configuration);
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
      logger.info(e);
    }
    return false;
  }

  /**
   * Builds blaze go target and returns the output build artifact.
   *
   * @throws ExecutionException if the target cannot be debugged.
   */
  private static File getExecutableToDebug(ExecutionEnvironment env) throws ExecutionException {
    BlazeCommandRunConfiguration configuration =
        BlazeCommandRunConfigurationRunner.getConfiguration(env);
    Project project = configuration.getProject();
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (blazeProjectData == null) {
      throw new ExecutionException("Not synced yet, please sync project");
    }

    try (BuildResultHelper buildResultHelper =
        BuildResultHelperProvider.forFiles(project, file -> true)) {
      List<String> debugFlags =
          Blaze.getBuildSystem(project) == BuildSystem.Blaze
              // $ go tool compile
              //   -N    disable optimizations
              //   -l    disable inlining
              ? ImmutableList.of("--gc_goopt=-N", "--gc_goopt=-l")
              // bazel build adds these flags themselves with -c dbg
              // https://github.com/bazelbuild/rules_go/issues/741
              : ImmutableList.of("--compilation_mode=dbg");

      ListenableFuture<BuildResult> buildOperation =
          BlazeBeforeRunCommandHelper.runBlazeBuild(
              configuration,
              buildResultHelper,
              debugFlags,
              ImmutableList.of("--dynamic_mode=off"),
              BlazeInvocationContext.runConfigContext(
                  ExecutorType.fromExecutor(env.getExecutor()), configuration.getType(), true),
              "Building debug binary");

      try {
        SaveUtil.saveAllFiles();
        buildOperation.get();
      } catch (InterruptedException | CancellationException e) {
        buildOperation.cancel(true);
        throw new RunCanceledByUserException();
      } catch (java.util.concurrent.ExecutionException e) {
        throw new ExecutionException(e);
      }
      List<File> candidateFiles;
      try {
        candidateFiles =
            buildResultHelper.getBuildArtifactsForTarget((Label) configuration.getTarget()).stream()
                .filter(File::canExecute)
                .collect(Collectors.toList());
      } catch (GetArtifactsException e) {
        throw new ExecutionException(
            String.format(
                "Failed to get output artifacts when building %s: %s",
                configuration.getTarget(), e.getMessage()));
      }
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
  @Nullable
  private static File findExecutable(Label target, List<File> outputs) {
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
