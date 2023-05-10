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
import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import java.io.OutputStream;
import java.util.function.Function;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;

/** Connects the blaze debugger during execution. */
class ConnectBlazeTestDebuggerTask<S extends AndroidDebuggerState> implements ConnectDebuggerTask {
  private static final Logger LOG = Logger.getInstance(ConnectBlazeTestDebuggerTask.class);

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
  public @NotNull Promise<XDebugSessionImpl> perform(
      @NotNull IDevice device,
      @NotNull String applicationId,
      @NotNull ExecutionEnvironment environment,
      @NotNull ProcessHandler oldProcessHandler) {
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
    Promise<XDebugSessionImpl> debugSessionPromise =
        DebugSessionStarter.INSTANCE
            .attachReattachingDebuggerToStartedProcess(
                device,
                applicationId,
                masterProcessHandler,
                environment,
                myAndroidDebugger,
                myAndroidDebuggerState,
                /* destroyRunningProcess= */ x -> Unit.INSTANCE,
                null,
                Long.MAX_VALUE)
            .onSuccess(
                session -> {
                  oldProcessHandler.detachProcess();
                  session.showSessionTab();
                })
            .onError(
                it -> {
                  if (it instanceof ExecutionException) {
                    ExecutionUtil.handleExecutionError(
                        environment.getProject(),
                        ToolWindowId.DEBUG,
                        it,
                        "Error running " + environment.getRunProfile().getName(),
                        it.getMessage(),
                        Function.identity(),
                        null);
                  } else {
                    Logger.getInstance(this.getClass()).error(it);
                  }
                });

    runContext.addLaunchTaskCompleteListener(
        () -> {
          masterProcessHandler.notifyTextAvailable(
              "Test run completed.\n", ProcessOutputTypes.STDOUT);
          masterProcessHandler.detachProcess();
        });
    return debugSessionPromise;
  }
}
