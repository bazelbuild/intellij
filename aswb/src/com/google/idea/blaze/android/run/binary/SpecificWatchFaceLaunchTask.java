/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.run.binary;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.tasks.LaunchResult;
import com.android.tools.idea.run.tasks.LaunchTask;
import com.android.tools.idea.run.tasks.LaunchTaskDurations;
import com.android.tools.idea.run.tasks.ShellCommandLauncher;
import com.android.tools.idea.run.util.LaunchStatus;
import com.intellij.execution.Executor;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;

/** LaunchTask class for a specific watch face (WearOS specific) */
public class SpecificWatchFaceLaunchTask implements LaunchTask {
  private static final String WEAR_PLATFORM_MAINTENANCE_RELEASE = "ro.cw_build.platform_mr";
  private static final String ID = "SPECIFIC_WATCH_FACE";

  @NotNull private final String myApplicationId;
  @NotNull private final String myWatchFace;
  private final boolean myIsDebug;

  public SpecificWatchFaceLaunchTask(
      @NotNull String applicationId, @NotNull String watchFace, boolean isDebug) {
    myApplicationId = applicationId;
    myWatchFace = watchFace;
    myIsDebug = isDebug;
  }

  @NotNull
  @Override
  public String getId() {
    return ID;
  }

  @NotNull
  @Override
  public String getDescription() {
    return "Launching watch face";
  }

  @Override
  public int getDuration() {
    return LaunchTaskDurations.LAUNCH_ACTIVITY;
  }

  @Override
  public LaunchResult run(
      @NotNull Executor executor,
      @NotNull IDevice device,
      @NotNull LaunchStatus launchStatus,
      @NotNull ConsolePrinter printer) {

    boolean hasWearDebugService = getHasWearDebugService(device);

    if (!hasWearDebugService && myIsDebug) {
      String command = getLegacyDebugWatchFaceCommands();
      executeShellCommand(command, device, launchStatus, printer, 15, TimeUnit.SECONDS);
    }

    String command = getStartWatchFaceCommand(hasWearDebugService);

    // The timeout is quite large to accommodate ARM emulators.
    boolean successful =
        executeShellCommand(command, device, launchStatus, printer, 15, TimeUnit.SECONDS);

    return successful
        ? LaunchResult.success()
        : LaunchResult.error("Unknown error.", getDescription());
  }

  private boolean getHasWearDebugService(IDevice device) {
    if (device.getVersion().getApiLevel() > 28) {
      return true;
    }
    if (device.getVersion().getApiLevel() == 28) {
      String platformMr = device.getProperty(WEAR_PLATFORM_MAINTENANCE_RELEASE);
      if (platformMr != null && Integer.parseInt(platformMr) >= 2) {
        return true;
      }
    }
    return false;
  }

  /**
   * Executes the given command, collecting the entire shell output into a single String before
   * passing it to the given {@link ConsolePrinter}.
   */
  protected boolean executeShellCommand(
      @NotNull String command,
      @NotNull IDevice device,
      @NotNull LaunchStatus launchStatus,
      @NotNull ConsolePrinter printer,
      long timeout,
      @NotNull TimeUnit timeoutUnit) {
    return ShellCommandLauncher.execute(
        command, device, launchStatus, printer, timeout, timeoutUnit);
  }

  @NotNull
  private String getLegacyDebugWatchFaceCommands() {
    return "am set-debug-app -w \"" + myApplicationId + "\"";
  }

  @NotNull
  private String getStartWatchFaceCommand(boolean hasWearDebugService) {
    String watchFacePath = getLauncherWatchFacePath(myApplicationId, myWatchFace);
    if (hasWearDebugService) {
      return "cmd wear.debug set-watchface"
          + (myIsDebug ? " -D" : "")
          + " \""
          + watchFacePath
          + "\"";
    } else {
      return "am broadcast -a com.google.android.wearable.watchface.action.SET_WATCH_FACE_SHELL"
          + " --ecn watch_face_component \""
          + watchFacePath
          + "\"";
    }
  }

  @NotNull
  private static String getLauncherWatchFacePath(
      @NotNull String packageName, @NotNull String watchFaceName) {
    return packageName + "/" + watchFaceName.replace("$", "\\$");
  }
}
