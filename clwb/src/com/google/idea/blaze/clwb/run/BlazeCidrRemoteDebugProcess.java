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
package com.google.idea.blaze.clwb.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessListener;
import com.intellij.openapi.util.Key;
import com.intellij.xdebugger.XDebugSession;
import org.jetbrains.annotations.NotNull;
import com.jetbrains.cidr.execution.TrivialInstaller;
import com.jetbrains.cidr.execution.TrivialRunParameters;
import com.jetbrains.cidr.execution.debugger.CidrDebugProcess;
import com.jetbrains.cidr.execution.debugger.backend.DebuggerDriver;
import com.jetbrains.cidr.execution.debugger.backend.DebuggerDriver.Inferior;
import com.jetbrains.cidr.execution.debugger.backend.DebuggerDriverConfiguration;
import com.jetbrains.cidr.execution.debugger.backend.gdb.GDBDriver;
import com.jetbrains.cidr.execution.debugger.remote.CidrRemoteDebugParameters;
import java.io.File;

/**
 * CLion-specific class representing a remote target process and the debugger process.
 *
 * <p>This should be extending CidrRemoteGDBDebugProcess, but that is 'final'
 */
public class BlazeCidrRemoteDebugProcess extends CidrDebugProcess {

  private final ProcessHandler targetProcess;
  private final CidrRemoteDebugParameters remoteDebugParameters;

  BlazeCidrRemoteDebugProcess(
      ProcessHandler targetProcess,
      DebuggerDriverConfiguration debuggerDriverConfiguration,
      CidrRemoteDebugParameters remoteDebugParameters,
      XDebugSession xDebugSession,
      TextConsoleBuilder textConsoleBuilder)
      throws ExecutionException {
    super(
        new TrivialRunParameters(debuggerDriverConfiguration, new TrivialInstaller(new GeneralCommandLine())),
        xDebugSession,
        textConsoleBuilder
    );

    this.remoteDebugParameters = remoteDebugParameters;
    this.targetProcess = targetProcess;

    installServerProcess();
  }

  // mirrors GdbWithGdbServerProcess#installServerProcess
  private void installServerProcess() {
    targetProcess.addProcessListener(new ProcessListener() {
      @Override
      public void processTerminated(@NotNull ProcessEvent event) {
        getSession().stop();
      }

      @Override
      public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
        getProcessHandler().notifyTextAvailable(event.getText(), outputType);
      }
    });

    getProcessHandler().addProcessListener(new ProcessListener() {
      @Override
      public void startNotified(@NotNull ProcessEvent event) {
        printlnToConsole("Running debug binary");
        printlnToConsole("Command: " + targetProcess);
        printlnToConsole("");

        targetProcess.startNotify();
      }

      @Override
      public void processTerminated(@NotNull ProcessEvent event) {
        if (!targetProcess.isStartNotified()) {
          targetProcess.startNotify();
        }

        if (!targetProcess.isProcessTerminated()) {
          targetProcess.destroyProcess();
        }

        event.getProcessHandler().removeProcessListener(this);
      }
    });
  }

  @Override
  public boolean isDetachDefault() {
    return false;
  }

  @Override
  protected Inferior doLoadTarget(DebuggerDriver debuggerDriver) throws ExecutionException {
    if (targetProcess.isProcessTerminated()) {
      throw new ExecutionException(
          "Target process terminated before debugger could attach (exit code "
              + targetProcess.getExitCode() + "). "
              + "Please check the console output for errors.");
    }

    if (!(debuggerDriver instanceof GDBDriver gdbDriver)) {
      throw new ExecutionException("Invalid DebuggerDriver - wanted GDBDriver");
    }

    return gdbDriver.loadForRemote(
        remoteDebugParameters.getRemoteCommand(),
        new File(remoteDebugParameters.getSymbolFile()),
        new File(remoteDebugParameters.getSysroot()),
        remoteDebugParameters.driverPathMapping()
    );
  }

  public ProcessHandler getTargetProcess() {
    return targetProcess;
  }
}
