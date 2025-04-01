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
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.util.SystemProperties;
import com.intellij.xdebugger.XDebugSession;
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
        new TrivialRunParameters(
            debuggerDriverConfiguration, new TrivialInstaller(new GeneralCommandLine())),
        xDebugSession,
        textConsoleBuilder);
    this.remoteDebugParameters = remoteDebugParameters;
    this.targetProcess = targetProcess;
  }

  private void writeLineToConsole(String s) {
    myConsole.print(s + System.lineSeparator(), ConsoleViewContentType.SYSTEM_OUTPUT);
  }

  @Override
  public ConsoleView createConsole() {
    writeLineToConsole("Running debug binary");
    writeLineToConsole("Command: " + targetProcess);
    writeLineToConsole("");
    myConsole.attachToProcess(targetProcess);
    return myConsole;
  }

  @Override
  public boolean isDetachDefault() {
    return false;
  }

  @Override
  protected Inferior doLoadTarget(DebuggerDriver debuggerDriver) throws ExecutionException {
    if (!(debuggerDriver instanceof GDBDriver)) {
      throw new ExecutionException("Invalid DebuggerDriver - wanted GDBDriver");
    }
    GDBDriver gdbDriver = (GDBDriver) debuggerDriver;
    return gdbDriver.loadForRemote(
        remoteDebugParameters.getRemoteCommand(),
        new File(remoteDebugParameters.getSymbolFile()),
        new File(remoteDebugParameters.getSysroot()),
        remoteDebugParameters.driverPathMapping());
  }
}
