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

import com.android.ddmlib.IDevice;
import com.android.tools.idea.execution.common.debug.AndroidDebugger;
import com.android.tools.idea.execution.common.debug.AndroidDebuggerState;
import com.android.tools.idea.execution.common.debug.DebugSessionStarter;
import com.android.tools.idea.run.tasks.ConnectDebuggerTask;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import java.io.OutputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Connects the blaze debugger during execution. */
class ConnectBlazeTestDebuggerTask<S extends AndroidDebuggerState> implements ConnectDebuggerTask {

  private final BlazeAndroidTestRunContext runContext;
  private final AndroidDebugger<S> myAndroidDebugger;
  private final S myAndroidDebuggerState;

  public ConnectBlazeTestDebuggerTask(
      BlazeAndroidTestRunContext runContext,
      AndroidDebugger<S> androidDebugger,
      S androidDebuggerState) {
    this.runContext = runContext;
    myAndroidDebugger = androidDebugger;
    myAndroidDebuggerState = androidDebuggerState;
  }

  /**
   * Wires up listeners to automatically reconnect the debugger for each test method. When you
   * `blaze test` an android_test in debug mode, it kills the instrumentation process between each
   * test method, disconnecting the debugger. We listen for the start of a new method waiting for a
   * debugger, and reconnect. TODO: Support stopping Blaze from the UI. This is hard because we have
   * no way to distinguish process handler termination/debug session ending initiated by the user.
   *
   * @return Promise with debug session or error
   */
  @Override
  public @NotNull XDebugSessionImpl perform(
      @NotNull IDevice device,
      @NotNull String applicationId,
      @NotNull ExecutionEnvironment environment,
      @NotNull ProgressIndicator progressIndicator,
      ConsoleView console) {
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
          public @Nullable OutputStream getProcessInput() {
            return null;
          }
        };
    runContext.addLaunchTaskCompleteListener(
        () -> {
          masterProcessHandler.notifyTextAvailable(
              "Test run completed.\n", ProcessOutputTypes.STDOUT);
          masterProcessHandler.detachProcess();
        });
    return DebugSessionStarter.INSTANCE.attachReattachingDebuggerToStartedProcess(
        device,
        applicationId,
        masterProcessHandler,
        environment,
        myAndroidDebugger,
        myAndroidDebuggerState,
        progressIndicator,
        console,
        Long.MAX_VALUE);
  }
}
