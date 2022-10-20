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

import static com.android.tools.idea.run.debug.StartJavaReattachingDebuggerKt.startJavaReattachingDebugger;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.Client;
import com.android.ddmlib.ClientData;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.run.ApkProvisionException;
import com.android.tools.idea.run.ApplicationIdProvider;
import com.android.tools.idea.run.LaunchInfo;
import com.android.tools.idea.run.ProcessHandlerConsolePrinter;
import com.android.tools.idea.run.tasks.ConnectDebuggerTask;
import com.android.tools.idea.run.tasks.LaunchTaskDurations;
import com.android.tools.idea.run.util.ProcessHandlerLaunchStatus;
import com.google.common.base.VerifyException;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Connects the blaze debugger during execution. */
class ConnectBlazeTestDebuggerTask implements ConnectDebuggerTask {
  private static final Logger LOG = Logger.getInstance(ConnectBlazeTestDebuggerTask.class);

  private final Project project;
  private final BlazeAndroidTestRunContext runContext;
  private final ApplicationIdProvider applicationIdProvider;

  public ConnectBlazeTestDebuggerTask(
      Project project,
      ApplicationIdProvider applicationIdProvider,
      BlazeAndroidTestRunContext runContext) {
    this.project = project;
    this.applicationIdProvider = applicationIdProvider;
    this.runContext = runContext;
  }

  @Override
  @NotNull
  public String getDescription() {
    return "ConnectBlazeTestDebuggerTask";
  }

  @Override
  public int getDuration() {
    return LaunchTaskDurations.CONNECT_DEBUGGER;
  }

  @Override
  public int getTimeoutSeconds() {
    return 0;
  }

  @Override
  public void setTimeoutSeconds(int timeoutSeconds) {
    throw new VerifyException("Unexpected code path");
  }

  @Override
  public void perform(
      @NotNull LaunchInfo launchInfo,
      @NotNull IDevice device,
      @NotNull ProcessHandlerLaunchStatus state,
      @NotNull ProcessHandlerConsolePrinter printer) {
    try {
      String packageName = applicationIdProvider.getPackageName();
      setUpForReattachingDebugger(device, packageName, launchInfo, state);
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
      @NotNull IDevice device,
      String packageName,
      LaunchInfo launchInfo,
      ProcessHandlerLaunchStatus launchStatus) {
    final ProcessHandler originalProcessHandler = launchStatus.getProcessHandler();
    final ProcessHandler masterProcessHandler =
        new ProcessHandler() {

          @Override
          protected void destroyProcessImpl() {
            notifyProcessTerminated(0);
          }

          @Override
          protected void detachProcessImpl() {
            notifyProcessDetached();
          }

          @Override
          public boolean detachIsDefault() {
            return false;
          }

          @Override
          @Nullable
          public OutputStream getProcessInput() {
            return null;
          }
        };
    final AndroidDebugBridge.IClientChangeListener reattachingListener =
        new AndroidDebugBridge.IClientChangeListener() {
          @Override
          public synchronized void clientChanged(@NotNull Client client, int changeMask) {
            ClientData data = client.getClientData();
            String clientDescription = data.getClientDescription();
            if (clientDescription != null && packageName.contains(clientDescription)) {
              AndroidDebugBridge.removeClientChangeListener(this);
              ApplicationManager.getApplication()
                  .executeOnPooledThread(
                      () ->
                          startJavaReattachingDebugger(
                                  project,
                                  device,
                                  masterProcessHandler,
                                  new HashSet<>(Collections.singleton(packageName)),
                                  launchInfo.env,
                                  null)
                              .onSuccess(
                                  it -> {
                                    if (!originalProcessHandler.isProcessTerminated()) {
                                      originalProcessHandler.detachProcess();
                                    }
                                    ApplicationManager.getApplication()
                                        .invokeLater(() -> it.showSessionTab());
                                  }));
            }
          }
        };

    AndroidDebugBridge.addClientChangeListener(reattachingListener);

    runContext.addLaunchTaskCompleteListener(
        () -> {
          masterProcessHandler.notifyTextAvailable(
              "Test run completed.\n", ProcessOutputTypes.STDOUT);
          masterProcessHandler.detachProcess();
          AndroidDebugBridge.removeClientChangeListener(reattachingListener);
        });
  }
}
