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

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.Client;
import com.android.ddmlib.ClientData;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.run.ApkProvisionException;
import com.android.tools.idea.run.ApplicationIdProvider;
import com.android.tools.idea.run.LaunchInfo;
import com.android.tools.idea.run.ProcessHandlerConsolePrinter;
import com.android.tools.idea.run.tasks.ConnectDebuggerTaskBase;
import com.android.tools.idea.run.tasks.ConnectJavaDebuggerTask;
import com.android.tools.idea.run.util.ProcessHandlerLaunchStatus;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Connects the blaze debugger during execution. */
class ConnectBlazeTestDebuggerTask extends ConnectDebuggerTaskBase {
  private static final Logger LOG = Logger.getInstance(ConnectBlazeTestDebuggerTask.class);

  private final Project project;
  private final ApplicationIdProvider applicationIdProvider;
  private final BlazeAndroidTestRunContext runContext;

  public ConnectBlazeTestDebuggerTask(
      Project project,
      ApplicationIdProvider applicationIdProvider,
      BlazeAndroidTestRunContext runContext) {
    super(applicationIdProvider, project, true);
    this.project = project;
    this.applicationIdProvider = applicationIdProvider;
    this.runContext = runContext;
  }

  @Nullable
  @Override
  public void perform(
      @NotNull LaunchInfo launchInfo,
      @NotNull IDevice device,
      @NotNull ProcessHandlerLaunchStatus state,
      @NotNull ProcessHandlerConsolePrinter printer) {
    try {
      String packageName = applicationIdProvider.getPackageName();
      setUpForReattachingDebugger(packageName, launchInfo, state, printer);
    } catch (ApkProvisionException e) {
      LOG.error(e);
    }
  }

  /**
   * Wires up listeners to automatically reconnect the debugger for each test method. When you
   * `blaze test` an android_test in debug mode, it kills the instrumentation process between each
   * test method, disconnecting the debugger. We listen for the start of a new method waiting for a
   * debugger, and reconnect. TODO: Support stopping Blaze from the UI. This is hard because we have
   * no way to distinguish process handler termination/debug session ending initiated by the user.
   */
  private void setUpForReattachingDebugger(
      String targetPackage,
      LaunchInfo launchInfo,
      ProcessHandlerLaunchStatus launchStatus,
      ProcessHandlerConsolePrinter printer) {
    final AndroidDebugBridge.IClientChangeListener reattachingListener =
        new AndroidDebugBridge.IClientChangeListener() {
          // The target application can either
          // 1. Match our target name, and become available for debugging.
          // 2. Be available for debugging, and suddenly have its name changed to match.
          static final int CHANGE_MASK = Client.CHANGE_DEBUGGER_STATUS | Client.CHANGE_NAME;

          @Override
          public void clientChanged(@NotNull Client client, int changeMask) {
            ClientData data = client.getClientData();
            String clientDescription = data.getClientDescription();
            if (clientDescription != null
                && clientDescription.equals(targetPackage)
                && (changeMask & CHANGE_MASK) != 0
                && data.getDebuggerConnectionStatus().equals(ClientData.DebuggerStatus.WAITING)) {
              reattachDebugger(launchInfo, client, launchStatus, printer);
            }
          }
        };

    AndroidDebugBridge.addClientChangeListener(reattachingListener);
    runContext.addLaunchTaskCompleteListener(
        () -> {
          AndroidDebugBridge.removeClientChangeListener(reattachingListener);
          launchStatus.terminateLaunch("Test run completed.\n", true);
        });
  }

  private void reattachDebugger(
      LaunchInfo launchInfo,
      final Client client,
      ProcessHandlerLaunchStatus launchStatus,
      ProcessHandlerConsolePrinter printer) {
    ApplicationManager.getApplication()
        .invokeLater(() -> launchDebugger(launchInfo, client, launchStatus, printer));
  }

  /**
   * Nearly a clone of {@link ConnectJavaDebuggerTask#launchDebugger}. There are a few changes to
   * account for null variables that could occur in our implementation.
   */
  @Override
  public void launchDebugger(
      @NotNull LaunchInfo currentLaunchInfo,
      @NotNull Client client,
      @NotNull ProcessHandlerLaunchStatus launchStatus,
      @NotNull ProcessHandlerConsolePrinter printer) {
    ProcessHandler unused =
        ConnectBlazeTestDebuggerTaskHelper.launchDebugger(
            project, currentLaunchInfo, client, launchStatus, printer);
  }
}
