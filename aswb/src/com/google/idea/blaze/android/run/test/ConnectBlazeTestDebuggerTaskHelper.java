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
package com.google.idea.blaze.android.run.test;

import com.android.ddmlib.Client;
import com.android.tools.idea.run.AndroidDebugState;
import com.android.tools.idea.run.AndroidProcessText;
import com.android.tools.idea.run.AndroidRunConfiguration;
import com.android.tools.idea.run.AndroidSessionInfo;
import com.android.tools.idea.run.AndroidSessionInfoCompat;
import com.android.tools.idea.run.LaunchInfo;
import com.android.tools.idea.run.ProcessHandlerConsolePrinter;
import com.android.tools.idea.run.tasks.ConnectJavaDebuggerTask;
import com.android.tools.idea.run.util.ProcessHandlerLaunchStatus;
import com.intellij.debugger.engine.RemoteDebugProcessHandler;
import com.intellij.debugger.ui.DebuggerPanelsManager;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.util.Locale;
import javax.annotation.Nullable;

/** Connects the blaze debugger during execution. */
class ConnectBlazeTestDebuggerTaskHelper {
  private ConnectBlazeTestDebuggerTaskHelper() {}

  /**
   * Nearly a clone of {@link ConnectJavaDebuggerTask#launchDebugger}. There are a few changes to
   * account for null variables that could occur in our implementation.
   */
  @Nullable
  public static ProcessHandler launchDebugger(
      Project project,
      LaunchInfo currentLaunchInfo,
      Client client,
      ProcessHandlerLaunchStatus launchStatus,
      ProcessHandlerConsolePrinter printer) {
    String debugPort = Integer.toString(client.getDebuggerListenPort());
    int pid = client.getClientData().getPid();
    Logger.getInstance(ConnectJavaDebuggerTask.class)
        .info(
            String.format(
                Locale.US,
                "Attempting to connect debugger to port %1$s [client %2$d]",
                debugPort,
                pid));

    // create a new process handler
    RemoteConnection connection = new RemoteConnection(true, "localhost", debugPort, false);
    RemoteDebugProcessHandler debugProcessHandler = new RemoteDebugProcessHandler(project);

    // switch the launch status and console printers to point to the new process handler
    // this is required, esp. for AndroidTestListener which holds a
    // reference to the launch status and printers, and those should
    // be updated to point to the new process handlers,
    // otherwise test results will not be forwarded appropriately
    ProcessHandler oldProcessHandler = launchStatus.getProcessHandler();
    launchStatus.setProcessHandler(debugProcessHandler);
    printer.setProcessHandler(debugProcessHandler);

    // Detach old process handler after the launch status
    // has been updated to point to the new process handler.
    oldProcessHandler.detachProcess();

    AndroidDebugState debugState =
        new AndroidDebugState(
            project, debugProcessHandler, connection, currentLaunchInfo.consoleProvider);

    RunContentDescriptor oldDescriptor;
    AndroidSessionInfo oldSession = oldProcessHandler.getUserData(AndroidSessionInfo.KEY);
    if (oldSession != null) {
      oldDescriptor = AndroidSessionInfoCompat.getDescriptor(oldSession);
    } else {
      // This is the first time we are attaching the debugger; get it from the environment instead.
      oldDescriptor = currentLaunchInfo.env.getContentToReuse();
    }

    RunContentDescriptor debugDescriptor;
    try {
      // @formatter:off
      ExecutionEnvironment debugEnv =
          new ExecutionEnvironmentBuilder(currentLaunchInfo.env)
              .executor(currentLaunchInfo.executor)
              .runner(currentLaunchInfo.runner)
              .contentToReuse(oldDescriptor)
              .build();
      debugDescriptor =
          DebuggerPanelsManager.getInstance(project)
              .attachVirtualMachine(debugEnv, debugState, connection, false);
      // @formatter:on
    } catch (ExecutionException e) {
      printer.stderr("ExecutionException: " + e.getMessage() + '.');
      return null;
    }

    // Based on the above try block we shouldn't get here unless we have assigned to debugDescriptor
    assert debugDescriptor != null;

    // re-run the collected text from the old process handler to the new
    // TODO: is there a race between messages received once the debugger has been connected,
    // and these messages that are printed out?
    final AndroidProcessText oldText = AndroidProcessText.get(oldProcessHandler);
    if (oldText != null) {
      oldText.printTo(debugProcessHandler);
    }

    RunProfile runProfile = currentLaunchInfo.env.getRunProfile();
    RunConfiguration runConfiguration =
        runProfile instanceof AndroidRunConfiguration ? (AndroidRunConfiguration) runProfile : null;
    AndroidSessionInfo sessionInfo =
        AndroidSessionInfoCompat.create(
            debugProcessHandler,
            debugDescriptor,
            runConfiguration,
            currentLaunchInfo.env.getExecutor().getId(),
            currentLaunchInfo.env.getExecutor().getActionName(),
            currentLaunchInfo.env.getExecutionTarget());

    debugProcessHandler.putUserData(AndroidSessionInfo.KEY, sessionInfo);
    AndroidSessionInfoCompat.putAndroidDebugClient(debugProcessHandler, client);
    debugProcessHandler.putUserData(
        AndroidSessionInfo.ANDROID_DEVICE_API_LEVEL, client.getDevice().getVersion());

    return debugProcessHandler;
  }
}
