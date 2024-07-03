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

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeInvocationContext;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper.GetArtifactsException;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelperProvider;
import com.google.idea.blaze.base.command.buildresult.LocalFileArtifact;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.run.BlazeBeforeRunCommandHelper;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.ExecutorType;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationRunner;
import com.google.idea.blaze.base.sync.aspects.BuildResult;
import com.google.idea.blaze.base.util.SaveUtil;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.RunCanceledByUserException;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.util.PathUtil;
import com.jetbrains.cidr.execution.CidrCommandLineState;
import com.jetbrains.cidr.lang.workspace.compiler.ClangCompilerKind;

import javax.annotation.Nullable;
import java.io.File;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.stream.Collectors;

/** CLion-specific handler for {@link BlazeCommandRunConfiguration}s. */
public class BlazeCidrRunConfigurationRunner implements BlazeCommandRunConfigurationRunner {
  private final BlazeCommandRunConfiguration configuration;

  /** Calculated during the before-run task, and made available to the debugger. */
  File executableToDebug = null;

  BlazeCidrRunConfigurationRunner(BlazeCommandRunConfiguration configuration) {
    this.configuration = configuration;
  }

  @Override
  public RunProfileState getRunProfileState(Executor executor, ExecutionEnvironment env) {
    return new CidrCommandLineState(env, new BlazeCidrLauncher(configuration, this, env));
  }

  @Override
  public boolean executeBeforeRunTask(ExecutionEnvironment env) {
    executableToDebug = null;
    if (!isDebugging(env)) {
      return true;
    }
    try {
      File executable = getExecutableToDebug(env);
      if (executable != null) {
        executableToDebug = executable;
        return true;
      }
    } catch (ExecutionException e) {
      ExecutionUtil.handleExecutionError(
          env.getProject(), env.getExecutor().getToolWindowId(), env.getRunProfile(), e);
    }
    return false;
  }

  private static boolean isDebugging(ExecutionEnvironment environment) {
    Executor executor = environment.getExecutor();
    return executor instanceof DefaultDebugExecutor;
  }

  private static Label getSingleTarget(BlazeCommandRunConfiguration config)
      throws ExecutionException {
    ImmutableList<? extends TargetExpression> targets = config.getTargets();
    if (targets.size() != 1 || !(targets.get(0) instanceof Label)) {
      throw new ExecutionException("Invalid configuration: doesn't have a single target label");
    }
    return (Label) targets.get(0);
  }

  private ImmutableList<String> getExtraDebugFlags(ExecutionEnvironment env) {
    if (Registry.is("bazel.clwb.debug.extraflags.disabled")) {
      return ImmutableList.of();
    }

    final var debuggerKind = RunConfigurationUtils.getDebuggerKind(configuration);
    if (debuggerKind == BlazeDebuggerKind.GDB_SERVER) {
      return BlazeGDBServerProvider.getFlagsForDebugging(configuration.getHandler().getState());
    }

    final var flagsBuilder = ImmutableList.<String>builder();

    if (debuggerKind == BlazeDebuggerKind.BUNDLED_LLDB && !Registry.is("bazel.trim.absolute.path.disabled")) {
      flagsBuilder.add("--copt=-fdebug-compilation-dir=" + WorkspaceRoot.fromProject(env.getProject()));
      flagsBuilder.add("--linkopt=-Wl,-oso_prefix,.");
    }

    flagsBuilder.add("--compilation_mode=dbg");
    flagsBuilder.add("--copt=-O0");
    flagsBuilder.add("--copt=-g");
    flagsBuilder.add("--strip=never");
    flagsBuilder.add("--dynamic_mode=off");
    flagsBuilder.addAll(BlazeGDBServerProvider.getOptionalFissionArguments());

    return flagsBuilder.build();
  }

  /**
   * Builds blaze C/C++ target in debug mode, and returns the output build artifact.
   *
   * @throws ExecutionException if no unique output artifact was found.
   */
  private File getExecutableToDebug(ExecutionEnvironment env) throws ExecutionException {
    SaveUtil.saveAllFiles();
    try (BuildResultHelper buildResultHelper =
        BuildResultHelperProvider.createForLocalBuild(env.getProject())) {
      ListenableFuture<BuildResult> buildOperation =
          BlazeBeforeRunCommandHelper.runBlazeCommand(
              BlazeCommandName.BUILD,
              configuration,
              buildResultHelper,
              ImmutableList.of(),
              getExtraDebugFlags(env),
              BlazeInvocationContext.runConfigContext(
                  ExecutorType.fromExecutor(env.getExecutor()), configuration.getType(), true),
              "Building debug binary");

      Label target = getSingleTarget(configuration);
      try {
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
      List<File> candidateFiles;
      try {
        candidateFiles =
            LocalFileArtifact.getLocalFiles(
                    buildResultHelper.getBuildArtifactsForTarget(target, file -> true))
                .stream()
                .filter(File::canExecute)
                .collect(Collectors.toList());
      } catch (GetArtifactsException e) {
        throw new ExecutionException(
            String.format(
                "Failed to get output artifacts when building %s: %s", target, e.getMessage()));
      }
      if (candidateFiles.isEmpty()) {
        throw new ExecutionException(
            String.format("No output artifacts found when building %s", target));
      }
      File file = findExecutable(target, candidateFiles);
      if (file == null) {
        throw new ExecutionException(
            String.format(
                "More than 1 executable was produced when building %s; don't know which to debug",
                target));
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
