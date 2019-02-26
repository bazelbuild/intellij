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
package com.google.idea.blaze.java.run.coverage;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import com.google.idea.blaze.base.logging.EventLoggingService;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.coverage.CoverageUtils;
import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.coverage.CoverageHelper;
import com.intellij.coverage.CoverageRunnerData;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.KillableProcess;
import com.intellij.execution.configurations.ConfigurationInfoProvider;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.configurations.coverage.CoverageEnabledConfiguration;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.DefaultProgramRunner;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.function.IntConsumer;
import javax.annotation.Nullable;

/** Controls 'run with coverage' execution for blaze run configurations. */
public class BlazeCoverageProgramRunner extends DefaultProgramRunner {

  private static final String ID = "BlazeCoverageProgramRunner";
  private static final Logger logger = Logger.getInstance(BlazeCoverageRunner.class);

  @Override
  public String getRunnerId() {
    return ID;
  }

  @Override
  public boolean canRun(String executorId, RunProfile profile) {
    return CoverageUtils.coverageEnabled(executorId, profile);
  }

  @Override
  public RunnerSettings createConfigurationData(final ConfigurationInfoProvider settingsProvider) {
    return new CoverageRunnerData();
  }

  @Nullable
  @Override
  protected RunContentDescriptor doExecute(RunProfileState profile, ExecutionEnvironment env)
      throws ExecutionException {
    WorkspaceRoot root = WorkspaceRoot.fromProjectSafe(env.getProject());
    if (root == null) {
      return null;
    }
    RunContentDescriptor result = super.doExecute(profile, env);
    if (result == null) {
      return null;
    }
    EventLoggingService.getInstance().logEvent(getClass(), "run-with-coverage");
    // remove any old copy of the coverage data

    // retrieve coverage data and copy locally
    BlazeCommandRunConfiguration blazeConfig = (BlazeCommandRunConfiguration) env.getRunProfile();
    BlazeCoverageEnabledConfiguration config =
        (BlazeCoverageEnabledConfiguration) CoverageEnabledConfiguration.getOrCreate(blazeConfig);

    String coverageFilePath = config.getCoverageFilePath();
    File blazeOutputFile =
        CoverageUtils.getOutputFile(Blaze.getBuildSystem(env.getProject()), root);

    ProcessHandler handler = result.getProcessHandler();
    if (handler != null) {
      ProcessHandler wrappedHandler =
          new ProcessHandlerWrapper(
              handler, exitCode -> copyCoverageOutput(blazeOutputFile, coverageFilePath, exitCode));
      CoverageHelper.attachToProcess(blazeConfig, wrappedHandler, env.getRunnerSettings());
    }
    return result;
  }

  private static void copyCoverageOutput(File blazeOutputFile, String localPath, int exitCode) {
    if (exitCode != 0) {
      new File(localPath).delete();
      return;
    }
    try {
      Files.copy(blazeOutputFile.toPath(), Paths.get(localPath), REPLACE_EXISTING);
    } catch (IOException e) {
      String msg = "Error copying output coverage file";
      logger.warn(msg, e);
    }
  }

  /**
   * Wraps up a process handler in another process handler, which on exit runs a provided function
   * prior to notifying any listeners.
   */
  private static class ProcessHandlerWrapper extends ProcessHandler implements KillableProcess {

    final ProcessHandler base;

    ProcessHandlerWrapper(ProcessHandler base, IntConsumer runOnExit) {
      this.base = base;
      base.addProcessListener(
          new ProcessAdapter() {
            @Override
            public void startNotified(ProcessEvent event) {
              ProcessHandlerWrapper.super.startNotify();
            }

            @Override
            public void processTerminated(ProcessEvent event) {
              int exitCode = event.getExitCode();
              runOnExit.accept(exitCode);
              ProcessHandlerWrapper.this.notifyProcessTerminated(exitCode);
            }
          });
      if (base.isStartNotified()) {
        super.startNotify();
      }
    }

    @Override
    public void startNotify() {
      base.startNotify();
    }

    @Override
    public boolean canKillProcess() {
      return base instanceof KillableProcess && ((KillableProcess) base).canKillProcess();
    }

    @Override
    public void killProcess() {
      if (base instanceof KillableProcess) {
        ((KillableProcess) base).killProcess();
      }
    }

    @Override
    protected void destroyProcessImpl() {
      base.destroyProcess();
    }

    @Override
    protected void detachProcessImpl() {
      base.detachProcess();
    }

    @Override
    public boolean detachIsDefault() {
      return base.detachIsDefault();
    }

    @Nullable
    @Override
    public OutputStream getProcessInput() {
      return base.getProcessInput();
    }
  }
}
