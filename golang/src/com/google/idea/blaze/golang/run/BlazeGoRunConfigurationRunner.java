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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.goide.execution.GoBuildingRunConfiguration.Kind;
import com.goide.execution.application.GoApplicationConfiguration;
import com.goide.execution.application.GoApplicationRunConfigurationType;
import com.goide.execution.application.GoApplicationRunningState;
import com.goide.util.GoCommandLineParameter;
import com.goide.util.GoExecutor;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.io.MoreFiles;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeInvocationContext;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper.GetArtifactsException;
import com.google.idea.blaze.base.command.buildresult.LocalFileArtifact;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.run.BlazeBeforeRunCommandHelper;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.ExecutorType;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandGenericRunConfigurationRunner.BlazeCommandRunProfileState;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationRunner;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.sync.aspects.BuildResult;
import com.google.idea.blaze.base.sync.aspects.BuildResult.Status;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.util.SaveUtil;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.RunCanceledByUserException;
import com.intellij.execution.RunManager;
import com.intellij.execution.configuration.EnvironmentVariablesData;
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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** Go-specific run configuration runner. */
public class BlazeGoRunConfigurationRunner implements BlazeCommandRunConfigurationRunner {

  private static class ExecutableInfo {
    final File binary;
    final File workingDir;
    final ImmutableList<String> args;
    final ImmutableMap<String, String> envVars;

    private ExecutableInfo(
        File binary,
        File workingDir,
        ImmutableList<String> args,
        ImmutableMap<String, String> envVars) {
      this.binary = binary;
      this.workingDir = workingDir;
      this.args = args;
      this.envVars = envVars;
    }
  }

  private static final BoolExperiment scriptPathEnabled =
      new BoolExperiment("blaze.go.script.path.enabled.2", true);

  /** Used to store a runner to an {@link ExecutionEnvironment}. */
  private static final Key<AtomicReference<ExecutableInfo>> EXECUTABLE_KEY =
      Key.create("blaze.debug.golang.executable");

  private static final Logger logger = Logger.getInstance(BlazeGoRunConfigurationRunner.class);

  /** Converts to the native go plugin debug configuration state */
  static class BlazeGoDummyDebugProfileState implements RunProfileState {
    private final BlazeCommandRunConfigurationCommonState state;

    BlazeGoDummyDebugProfileState(BlazeCommandRunConfiguration configuration)
        throws ExecutionException {
      this.state =
          configuration.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
      if (this.state == null) {
        throw new ExecutionException("Missing run configuration common state");
      }
    }

    private static ExecutableInfo getExecutableInfo(ExecutionEnvironment env) {
      return env.getCopyableUserData(EXECUTABLE_KEY).get();
    }

    private ImmutableList<String> getParameters(ExecutableInfo executable) {
      return ImmutableList.<String>builder()
          .addAll(state.getExeFlagsState().getFlagsForExternalProcesses())
          .addAll(state.getTestArgs())
          .addAll(executable.args)
          .build();
    }

    @Nullable
    private String getTestFilter() {
      return state.getTestFilterForExternalProcesses();
    }

    GoApplicationRunningState toNativeState(ExecutionEnvironment env) throws ExecutionException {
      ExecutableInfo executable = getExecutableInfo(env);
      if (executable == null || StringUtil.isEmptyOrSpaces(executable.binary.getPath())) {
        throw new ExecutionException("Blaze output binary not found");
      }
      Project project = env.getProject();
      BlazeProjectData projectData =
          BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
      if (projectData == null) {
        throw new ExecutionException("Project data not found. Please run blaze sync.");
      }
      GoApplicationConfiguration nativeConfig =
          (GoApplicationConfiguration)
              GoApplicationRunConfigurationType.getInstance()
                  .getConfigurationFactories()[0]
                  .createTemplateConfiguration(project, RunManager.getInstance(project));
      nativeConfig.setKind(Kind.PACKAGE);
      // prevents binary from being deleted by
      // GoBuildingRunningState$ProcessHandler#processTerminated
      nativeConfig.setOutputDirectory(executable.binary.getParent());
      nativeConfig.setParams(ParametersListUtil.join(getParameters(executable)));

      EnvironmentVariablesData envVarsState = state.getUserEnvVarsState().getData();
      nativeConfig.setCustomEnvironment(envVarsState.getEnvs());
      nativeConfig.setPassParentEnvironment(envVarsState.isPassParentEnvs());

      nativeConfig.setWorkingDirectory(executable.workingDir.getPath());

      Map<String, String> customEnvironment = new HashMap<>(nativeConfig.getCustomEnvironment());
      for (Map.Entry<String, String> entry : executable.envVars.entrySet()) {
        customEnvironment.put(entry.getKey(), entry.getValue());
      }
      String testFilter = getTestFilter();
      if (testFilter != null) {
        customEnvironment.put("TESTBRIDGE_TEST_ONLY", testFilter);
      }
      nativeConfig.setCustomEnvironment(customEnvironment);

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
            public List<GoCommandLineParameter> getBuildingTarget() {
              return null;
            }

            @Nullable
            @Override
            public GoExecutor createBuildExecutor() {
              return null;
            }
          };
      nativeState.setOutputFilePath(executable.binary.getPath());
      return nativeState;
    }

    @Nullable
    @Override
    public ExecutionResult execute(Executor executor, ProgramRunner<?> runner) {
      return null;
    }
  }

  @Override
  public RunProfileState getRunProfileState(Executor executor, ExecutionEnvironment env)
      throws ExecutionException {
    BlazeCommandRunConfiguration configuration =
        BlazeCommandRunConfigurationRunner.getConfiguration(env);
    if (!BlazeCommandRunConfigurationRunner.isDebugging(env)
        || BlazeCommandName.BUILD.equals(BlazeCommandRunConfigurationRunner.getBlazeCommand(env))) {
      return new BlazeCommandRunProfileState(env);
    }
    env.putCopyableUserData(EXECUTABLE_KEY, new AtomicReference<>());
    return new BlazeGoDummyDebugProfileState(configuration);
  }

  @Override
  public boolean executeBeforeRunTask(ExecutionEnvironment env) {
    if (!BlazeCommandRunConfigurationRunner.isDebugging(env)
        || BlazeCommandName.BUILD.equals(BlazeCommandRunConfigurationRunner.getBlazeCommand(env))) {
      return true;
    }
    env.getCopyableUserData(EXECUTABLE_KEY).set(null);
    try {
      ExecutableInfo executableInfo = getExecutableToDebug(env);
      env.getCopyableUserData(EXECUTABLE_KEY).set(executableInfo);
      if (executableInfo.binary != null) {
        return true;
      }
    } catch (ExecutionException e) {
      ExecutionUtil.handleExecutionError(
          env.getProject(), env.getExecutor().getToolWindowId(), env.getRunProfile(), e);
      logger.info(e);
    }
    return false;
  }

  private static Label getSingleTarget(BlazeCommandRunConfiguration config)
      throws ExecutionException {
    ImmutableList<? extends TargetExpression> targets = config.getTargets();
    if (targets.size() != 1 || !(targets.get(0) instanceof Label)) {
      throw new ExecutionException("Invalid configuration: doesn't have a single target label");
    }
    return (Label) targets.get(0);
  }

  /**
   * Builds blaze go target and returns the output build artifact.
   *
   * @throws ExecutionException if the target cannot be debugged.
   */
  private static ExecutableInfo getExecutableToDebug(ExecutionEnvironment env)
      throws ExecutionException {
    BlazeCommandRunConfiguration configuration =
        BlazeCommandRunConfigurationRunner.getConfiguration(env);
    Project project = configuration.getProject();
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (blazeProjectData == null) {
      throw new ExecutionException("Not synced yet, please sync project");
    }
    Label label = getSingleTarget(configuration);

    SaveUtil.saveAllFiles();
    // Explicitly create local build helper, because the debuggable script is expected to be present
    // locally
    try (final var buildResultHelper = new BuildResultHelper()) {
      ImmutableList.Builder<String> flags = ImmutableList.builder();
      if (Blaze.getBuildSystemName(project) == BuildSystemName.Blaze) {
        // $ go tool compile
        //   -N    disable optimizations
        //   -l    disable inlining
        flags.add("--gc_goopt=-N").add("--gc_goopt=-l");
      } else {
        // bazel build adds these flags themselves with -c dbg
        // https://github.com/bazelbuild/rules_go/issues/741
        flags.add("--compilation_mode=dbg");
      }

      Optional<Path> scriptPath = Optional.empty();
      if (scriptPathEnabled.getValue()) {
        try {
          scriptPath = Optional.of(BlazeBeforeRunCommandHelper.createScriptPathFile());
          flags.add("--script_path=" + scriptPath.get());
        } catch (IOException e) {
          // Could still work without script path.
          // Script path is only needed to parse arguments from target.
          logger.warn("Failed to create script path file. Target arguments will not be parsed.", e);
        }
      }

      ListenableFuture<BuildResult> buildOperation =
          BlazeBeforeRunCommandHelper.runBlazeCommand(
              scriptPath.isPresent() ? BlazeCommandName.RUN : BlazeCommandName.BUILD,
              configuration,
              buildResultHelper,
              flags.build(),
              ImmutableList.of("--dynamic_mode=off", "--test_sharding_strategy=disabled"),
              BlazeInvocationContext.runConfigContext(
                  ExecutorType.fromExecutor(env.getExecutor()), configuration.getType(), true),
              "Building debug binary");

      try {
        BuildResult result = buildOperation.get();
        if (result.outOfMemory()) {
          throw new ExecutionException("Out of memory while trying to build debug target");
        } else if (result.status == Status.BUILD_ERROR) {
          throw new ExecutionException("Build error while trying to build debug target");
        } else if (result.status == Status.FATAL_ERROR) {
          throw new ExecutionException(
              String.format(
                  "Fatal error (%d) while trying to build debug target", result.exitCode));
        }
      } catch (InterruptedException | CancellationException e) {
        buildOperation.cancel(true);
        throw new RunCanceledByUserException();
      } catch (java.util.concurrent.ExecutionException e) {
        throw new ExecutionException(e);
      }
      if (scriptPath.isPresent()) {
        if (!Files.exists(scriptPath.get())) {
          throw new ExecutionException(
              String.format(
                  "No debugger executable script path file produced. Expected file at: %s",
                  scriptPath.get()));
        }
        WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProject(project);
        BlazeInfo blazeInfo =
            BlazeProjectDataManager.getInstance(project).getBlazeProjectData().getBlazeInfo();
        return parseScriptPathFile(workspaceRoot, blazeInfo.getExecutionRoot(), scriptPath.get());
      } else {
        List<File> candidateFiles;
        try {
          candidateFiles =
              LocalFileArtifact.getLocalFiles(
                      buildResultHelper.getBuildArtifactsForTarget(label, file -> true))
                  .stream()
                  .filter(File::canExecute)
                  .collect(Collectors.toList());
        } catch (GetArtifactsException e) {
          throw new ExecutionException(
              String.format(
                  "Failed to get output artifacts when building %s: %s", label, e.getMessage()));
        }
        if (candidateFiles.isEmpty()) {
          throw new ExecutionException(
              String.format("No output artifacts found when building %s", label));
        }
        File binary = findExecutable(label, candidateFiles);
        if (binary == null) {
          throw new ExecutionException(
              String.format(
                  "More than 1 executable was produced when building %s; "
                      + "don't know which one to debug",
                  label));
        }
        LocalFileSystem.getInstance().refreshIoFiles(ImmutableList.of(binary));
        File workingDir = getWorkingDirectory(WorkspaceRoot.fromProject(project), binary);
        return new ExecutableInfo(binary, workingDir, ImmutableList.of(), ImmutableMap.of());
      }
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

  // Matches TEST_SRCDIR=<dir>
  private static final Pattern TEST_SRCDIR = Pattern.compile("TEST_SRCDIR=([^ ]+)");
  // Matches RUNFILES_<NAME>=<value>
  private static final Pattern RUNFILES_VAR = Pattern.compile("RUNFILES_([A-Z_]+)=([^ ]+)");
  // Matches TEST_TARGET=//<package_path>:<target>
  private static final Pattern TEST_TARGET = Pattern.compile("TEST_TARGET=//([^:]*):([^\\s]+)");
  // Matches a space-delimited arg list. Supports wrapping arg in single quotes.
  private static final Pattern ARGS = Pattern.compile("([^\']\\S*|\'.+?\')\\s*");

  private static ExecutableInfo parseScriptPathFile(
      WorkspaceRoot workspaceRoot, File execRoot, Path scriptPath) throws ExecutionException {
    String text;
    try {
      text = MoreFiles.asCharSource(scriptPath, UTF_8).read();
    } catch (IOException e) {
      throw new ExecutionException("Could not read script_path: " + scriptPath, e);
    }
    String lastLine = Iterables.getLast(Splitter.on('\n').split(text));
    List<String> args = new ArrayList<>();
    Matcher argsMatcher = ARGS.matcher(lastLine.trim());
    while (argsMatcher.find()) {
      String arg = argsMatcher.group(1);
      // Strip arg quotes
      arg = StringUtil.trimLeading(StringUtil.trimEnd(arg, '\''), '\'');
      args.add(arg);
    }
    ImmutableMap.Builder<String, String> envVars = ImmutableMap.builder();
    final File binary;
    final File workingDir;
    Matcher testScrDir = TEST_SRCDIR.matcher(text);
    if (testScrDir.find()) {
      // Format is <wrapper-script> <executable> arg0 arg1 arg2 ... argN "@"
      if (args.size() < 3) {
        throw new ExecutionException("Failed to parse args in script_path: " + scriptPath);
      }
      // Make paths used for runfiles discovery absolute as the working directory is changed below.
      envVars.put("TEST_SRCDIR", workspaceRoot.absolutePathFor(testScrDir.group(1)).toString());
      Matcher runfilesVars = RUNFILES_VAR.matcher(text);
      while (runfilesVars.find()) {
        envVars.put(
            String.format("RUNFILES_%s", runfilesVars.group(1)),
            workspaceRoot.absolutePathFor(runfilesVars.group(2)).toString());
      }
      String workspaceName = execRoot.getName();
      binary =
          Paths.get(
                  workspaceRoot.directory().getPath(),
                  testScrDir.group(1),
                  workspaceName,
                  args.get(1))
              .toFile();

      Matcher testTarget = TEST_TARGET.matcher(text);
      if (testTarget.find()) {
        String packagePath = testTarget.group(1);
        workingDir = 
            Paths.get(
                    workspaceRoot.directory().getPath(),
                    testScrDir.group(1),
                    workspaceName,
                    packagePath)
                .toFile();
      } else {
        workingDir = workspaceRoot.directory();
      }

      // Remove everything except the args.
      args = args.subList(2, args.size() - 1);
    } else {
      // Format is <executable> [arg0 arg1 arg2 ... argN] "@"
      if (args.size() < 2) {
        throw new ExecutionException("Failed to parse args in script_path: " + scriptPath);
      }
      binary = new File(args.get(0));
      workingDir = getWorkingDirectory(workspaceRoot, binary);
      // Remove everything except the args.
      args = args.subList(1, args.size() - 1);
    }
    return new ExecutableInfo(binary, workingDir, ImmutableList.copyOf(args), envVars.build());
  }

  /**
   * Similar to {@link com.google.idea.blaze.python.run.BlazePyRunConfigurationRunner}.
   *
   * <p>Working directory should be the runfiles directory of the debug executable.
   *
   * <p>If the runfiles directory does not exist (unlikely) fall back to workspace root.
   */
  private static File getWorkingDirectory(WorkspaceRoot root, File executable) {
    String workspaceName = root.directory().getName();
    File expectedPath = new File(executable.getPath() + ".runfiles", workspaceName);
    if (FileOperationProvider.getInstance().isDirectory(expectedPath)) {
      return expectedPath;
    }
    return root.directory();
  }
}
