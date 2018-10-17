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
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.tasks.LaunchTask;
import com.android.tools.idea.run.tasks.LaunchTaskDurations;
import com.android.tools.idea.run.util.LaunchStatus;
import com.android.tools.idea.run.util.ProcessHandlerLaunchStatus;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.google.idea.blaze.base.async.process.ExternalTask;
import com.google.idea.blaze.base.async.process.LineProcessingOutputStream;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.filecache.FileCaches;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.ScopedFunction;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.util.SaveUtil;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.ide.PooledThreadExecutor;

/**
 * An Android application launcher that invokes `blaze test` on an android_test target, and sets up
 * process handling and debugging for the test run.
 */
class BlazeAndroidTestLaunchTask implements LaunchTask {
  private static final String ID = "BLAZE_ANDROID_TEST";

  // Uses a local device/emulator attached to adb to run an android_test.
  public static final String TEST_LOCAL_DEVICE =
      BlazeFlags.TEST_ARG + "--device_broker_type=LOCAL_ADB_SERVER";
  // Uses a local device/emulator attached to adb to run an android_test.
  public static final String TEST_DEBUG = BlazeFlags.TEST_ARG + "--enable_debug";
  // Specifies the serial number for a local test device.
  private static final String TEST_DEVICE_SERIAL = "--device_serial_number=";
  private static final Logger LOG = Logger.getInstance(BlazeAndroidTestLaunchTask.class);

  private final Project project;
  private final Label target;
  private final List<String> buildFlags;
  private final BlazeAndroidTestFilter testFilter;

  private ListenableFuture<Boolean> blazeResult;

  private final BlazeAndroidTestRunContext runContext;

  private final boolean debug;

  public BlazeAndroidTestLaunchTask(
      Project project,
      Label target,
      List<String> buildFlags,
      BlazeAndroidTestFilter testFilter,
      BlazeAndroidTestRunContext runContext,
      boolean debug) {
    this.project = project;
    this.target = target;
    this.buildFlags = buildFlags;
    this.testFilter = testFilter;
    this.runContext = runContext;
    this.debug = debug;
  }

  @NotNull
  @Override
  public String getDescription() {
    return String.format("Running %s tests", Blaze.buildSystemName(project));
  }

  @Override
  public int getDuration() {
    return LaunchTaskDurations.LAUNCH_ACTIVITY;
  }

  @Override
  public boolean perform(
      @NotNull IDevice device,
      @NotNull LaunchStatus launchStatus,
      @NotNull ConsolePrinter printer) {
    BlazeExecutor executor = BlazeExecutor.getInstance();

    ProcessHandlerLaunchStatus processHandlerLaunchStatus =
        (ProcessHandlerLaunchStatus) launchStatus;
    final ProcessHandler processHandler = processHandlerLaunchStatus.getProcessHandler();

    blazeResult =
        executor.submit(
            new Callable<Boolean>() {
              @Override
              public Boolean call() throws Exception {
                return Scope.root(
                    new ScopedFunction<Boolean>() {
                      @Override
                      public Boolean execute(@NotNull BlazeContext context) {
                        ProjectViewSet projectViewSet =
                            ProjectViewManager.getInstance(project).getProjectViewSet();
                        if (projectViewSet == null) {
                          IssueOutput.error("Could not load project view. Please resync project.")
                              .submit(context);
                          return false;
                        }

                        BlazeCommand.Builder commandBuilder =
                            BlazeCommand.builder(
                                    Blaze.getBuildSystemProvider(project).getBinaryPath(project),
                                    BlazeCommandName.TEST)
                                .addTargets(target);
                        // Build flags must match BlazeBeforeRunTask.
                        commandBuilder.addBlazeFlags(buildFlags);
                        // Run the test on the selected local device/emulator.
                        commandBuilder
                            .addBlazeFlags(TEST_LOCAL_DEVICE, BlazeFlags.TEST_OUTPUT_STREAMED)
                            .addBlazeFlags(testDeviceSerialFlags(device.getSerialNumber()))
                            .addBlazeFlags(testFilter.getBlazeFlags());
                        if (debug) {
                          commandBuilder.addBlazeFlags(
                              TEST_DEBUG, BlazeFlags.NO_CACHE_TEST_RESULTS);
                        }
                        BlazeCommand command = commandBuilder.build();

                        printer.stdout(
                            String.format("Starting %s test...\n", Blaze.buildSystemName(project)));
                        printer.stdout(command + "\n");
                        LineProcessingOutputStream.LineProcessor stdoutLineProcessor =
                            line -> {
                              printer.stdout(line);
                              return true;
                            };
                        LineProcessingOutputStream.LineProcessor stderrLineProcessor =
                            line -> {
                              printer.stderr(line);
                              return true;
                            };
                        SaveUtil.saveAllFiles();
                        int retVal =
                            ExternalTask.builder(WorkspaceRoot.fromProject(project))
                                .addBlazeCommand(command)
                                .context(context)
                                .stdout(LineProcessingOutputStream.of(stdoutLineProcessor))
                                .stderr(LineProcessingOutputStream.of(stderrLineProcessor))
                                .build()
                                .run();
                        FileCaches.refresh(project);

                        if (retVal != 0) {
                          context.setHasError();
                        }

                        return !context.hasErrors();
                      }
                    });
              }
            });

    blazeResult.addListener(runContext::onLaunchTaskComplete, PooledThreadExecutor.INSTANCE);

    // The debug case is set up in ConnectBlazeTestDebuggerTask
    if (!debug) {
      waitAndSetUpForKillingBlazeOnStop(processHandler, launchStatus);
    }
    return true;
  }

  @NotNull
  @Override
  public String getId() {
    return ID;
  }

  /**
   * Hooks up the Blaze process to be killed if the user hits the 'Stop' button, then waits for the
   * Blaze process to stop. In non-debug mode, we wait for test execution to finish before returning
   * from launch() (this matches the behavior of the stock ddmlib runner).
   */
  private void waitAndSetUpForKillingBlazeOnStop(
      @NotNull final ProcessHandler processHandler, @NotNull LaunchStatus launchStatus) {
    processHandler.addProcessListener(
        new ProcessAdapter() {
          @Override
          public void processWillTerminate(ProcessEvent event, boolean willBeDestroyed) {
            blazeResult.cancel(true /* mayInterruptIfRunning */);
            launchStatus.terminateLaunch("Test run stopped.\n");
          }
        });

    try {
      blazeResult.get();
      launchStatus.terminateLaunch("Tests ran to completion.\n");
    } catch (CancellationException e) {
      // The user has canceled the test.
      launchStatus.terminateLaunch("Test run stopped.\n");
    } catch (InterruptedException e) {
      // We've been interrupted - cancel the underlying Blaze process.
      blazeResult.cancel(true /* mayInterruptIfRunning */);
      launchStatus.terminateLaunch("Test run stopped.\n");
    } catch (ExecutionException e) {
      LOG.error(e);
      launchStatus.terminateLaunch(
          "Test run stopped due to internal exception. Please file a bug report.\n");
    }
  }

  @NotNull
  private static String testDeviceSerialFlags(@NotNull String serial) {
    return BlazeFlags.TEST_ARG + TEST_DEVICE_SERIAL + serial;
  }
}
