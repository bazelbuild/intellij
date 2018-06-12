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
package com.google.idea.blaze.java.run;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Stopwatch;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.logging.EventLoggingService;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandGenericRunConfigurationRunner.BlazeCommandRunProfileState;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationRunner;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.util.SaveUtil;
import com.google.idea.blaze.java.fastbuild.FastBuildCompileException;
import com.google.idea.blaze.java.fastbuild.FastBuildException;
import com.google.idea.blaze.java.fastbuild.FastBuildInfo;
import com.google.idea.blaze.java.fastbuild.FastBuildService;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.RunCanceledByUserException;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.KeyWithDefaultValue;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

final class FastBuildConfigurationRunner implements BlazeCommandRunConfigurationRunner {

  private static final Logger logger = Logger.getInstance(FastBuildConfigurationRunner.class);

  static final Key<AtomicReference<FastBuildInfo>> BUILD_INFO_KEY =
      KeyWithDefaultValue.create("blaze.java.fastRun.buildInfo", new AtomicReference<>());
  static final Key<AtomicReference<FastBuildLoggingData>> LOGGING_DATA_KEY =
      KeyWithDefaultValue.create("blaze.java.fastRun.loggingData", new AtomicReference<>());

  /** Returns false if this isn't a 'blaze test' invocation. */
  private static boolean useFastBuild(ExecutionEnvironment env) {
    BlazeCommandRunConfiguration configuration = (BlazeCommandRunConfiguration) env.getRunProfile();
    BlazeCommandName command =
        ((BlazeCommandRunConfigurationCommonState) configuration.getHandler().getState())
            .getCommandState()
            .getCommand();
    return BlazeCommandName.TEST.equals(command);
  }
  @Override
  public RunProfileState getRunProfileState(Executor executor, ExecutionEnvironment env) {
    if (!useFastBuild(env)) {
      return new BlazeCommandRunProfileState(env);
    }
    return new FastBuildRunProfileState(env);
  }

  @Override
  public boolean executeBeforeRunTask(ExecutionEnvironment env) {
    if (!useFastBuild(env)) {
      return true;
    }
    Project project = env.getProject();
    BlazeCommandRunConfiguration configuration = (BlazeCommandRunConfiguration) env.getRunProfile();
    BlazeCommandRunConfigurationCommonState handlerState =
        (BlazeCommandRunConfigurationCommonState) configuration.getHandler().getState();

    checkState(configuration.getTarget() != null);
    Label label = (Label) configuration.getTarget();

    String binaryPath =
        handlerState.getBlazeBinaryState().getBlazeBinary() != null
            ? handlerState.getBlazeBinaryState().getBlazeBinary()
            : Blaze.getBuildSystemProvider(project).getBinaryPath();

    SaveUtil.saveAllFiles();
    Future<FastBuildInfo> buildFuture = null;
    FastBuildLoggingData loggingData = new FastBuildLoggingData();
    try {
      buildFuture =
          FastBuildService.getInstance(project)
              .createBuild(label, binaryPath, handlerState.getBlazeFlagsState().getExpandedFlags());
      FastBuildInfo fastBuildInfo = buildFuture.get();
      env.getUserData(BUILD_INFO_KEY).set(fastBuildInfo);
      loggingData.data.putAll(fastBuildInfo.loggingData());
      env.getUserData(LOGGING_DATA_KEY).set(loggingData);
      return true;
    } catch (InterruptedException e) {
      buildFuture.cancel(/* mayInterruptIfRunning */ true);
      Thread.currentThread().interrupt();
    } catch (CancellationException e) {
      ExecutionUtil.handleExecutionError(
          env.getProject(),
          env.getExecutor().getToolWindowId(),
          env.getRunProfile(),
          new RunCanceledByUserException());
    } catch (FastBuildException e) {
      logger.warn(e);
      ExecutionUtil.handleExecutionError(env, new ExecutionException(e));
    } catch (java.util.concurrent.ExecutionException e) {
      logger.warn(e);
      if (e.getCause() instanceof FastBuildCompileException) {
        loggingData.data.putAll(((FastBuildCompileException) e.getCause()).getLoggingData());
      }
      ExecutionUtil.handleExecutionError(env, new ExecutionException(e.getCause()));
    }
    loggingData.writeLog();
    return false;
  }

  static class FastBuildLoggingData {
    private final Stopwatch timer = Stopwatch.createStarted();
    // Use a LinkedHashMap so that we preserve the order of the entries.
    private final Map<String, String> data = new LinkedHashMap<>();

    void put(String key, String value) {
      data.put(key, value);
    }

    void writeLog() {
      EventLoggingService.getInstance()
          .ifPresent(
              s ->
                  s.logEvent(
                      FastBuildService.class,
                      "fast_build",
                      data,
                      timer.elapsed(TimeUnit.NANOSECONDS)));
    }
  }
}
