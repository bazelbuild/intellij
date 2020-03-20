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
package com.google.idea.blaze.android.run.runner;

import com.android.tools.idea.run.AndroidSessionInfo;
import com.android.tools.idea.run.DeviceFutures;
import com.android.tools.idea.run.editor.DeployTarget;
import com.android.tools.idea.run.editor.DeployTargetState;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import javax.annotation.Nullable;
import org.jetbrains.android.facet.AndroidFacet;

/** Selects a device. */
public interface BlazeAndroidDeviceSelector {

  /** A device session */
  class DeviceSession {
    @Nullable public final DeployTarget deployTarget;
    @Nullable public final DeviceFutures deviceFutures;
    @Nullable public final AndroidSessionInfo sessionInfo;

    public DeviceSession(
        @Nullable DeployTarget deployTarget,
        @Nullable DeviceFutures deviceFutures,
        @Nullable AndroidSessionInfo sessionInfo) {
      this.deployTarget = deployTarget;
      this.deviceFutures = deviceFutures;
      this.sessionInfo = sessionInfo;
    }
  }

  DeviceSession getDevice(
      Project project,
      AndroidFacet facet,
      BlazeAndroidRunConfigurationDeployTargetManager deployTargetManager,
      Executor executor,
      ExecutionEnvironment env,
      AndroidSessionInfo info,
      boolean debug,
      int runConfigId)
      throws ExecutionException;

  /** Standard device selector */
  class NormalDeviceSelector implements BlazeAndroidDeviceSelector {

    private static final DialogWrapper.DoNotAskOption ourKillLaunchOption =
        new KillLaunchDialogOption();
    private static final Logger LOG = Logger.getInstance(NormalDeviceSelector.class);

    static class KillLaunchDialogOption implements DialogWrapper.DoNotAskOption {
      private boolean show;

      @Override
      public boolean isToBeShown() {
        return !show;
      }

      @Override
      public void setToBeShown(boolean toBeShown, int exitCode) {
        show = !toBeShown;
      }

      @Override
      public boolean canBeHidden() {
        return true;
      }

      @Override
      public boolean shouldSaveOptionsOnCancel() {
        return true;
      }

      @Override
      public String getDoNotShowMessage() {
        return "Do not ask again";
      }
    }

    @Override
    public DeviceSession getDevice(
        Project project,
        AndroidFacet facet,
        BlazeAndroidRunConfigurationDeployTargetManager deployTargetManager,
        Executor executor,
        ExecutionEnvironment env,
        AndroidSessionInfo info,
        boolean debug,
        int runConfigId)
        throws ExecutionException {
      // If there is an existing session, then terminate those sessions
      if (info != null) {
        boolean continueLaunch = promptAndKillSession(executor, project, info);
        if (!continueLaunch) {
          return null;
        }
      }

      DeployTarget deployTarget =
          deployTargetManager.getDeployTarget(executor, env, facet, runConfigId);
      if (deployTarget == null) {
        return null;
      }

      DeviceFutures deviceFutures = null;
      DeployTargetState deployTargetState = deployTargetManager.getCurrentDeployTargetState();
      if (!deployTarget.hasCustomRunProfileState(executor)) {
        deviceFutures =
            deployTarget.getDevices(
                deployTargetState, facet, deployTargetManager.getDeviceCount(), debug, runConfigId);
      }
      return new DeviceSession(deployTarget, deviceFutures, info);
    }

    private boolean promptAndKillSession(
        Executor executor, Project project, AndroidSessionInfo info) {
      String previousExecutor = info.getExecutorId();
      String currentExecutor = executor.getId();

      if (ourKillLaunchOption.isToBeShown()) {
        String msg;
        String noText;
        if (previousExecutor.equals(currentExecutor)) {
          msg =
              String.format(
                  "Restart App?\nThe app is already running. "
                      + "Would you like to kill it and restart the session?");
          noText = "Cancel";
        } else {
          msg =
              String.format(
                  "To switch from %1$s to %2$s, the app has to restart. Continue?",
                  previousExecutor, currentExecutor);
          noText = "Cancel " + currentExecutor;
        }

        String targetName = info.getExecutionTarget().getDisplayName();
        String title = "Launching " + targetName;
        String yesText = "Restart " + targetName;
        if (Messages.NO
            == Messages.showYesNoDialog(
                project,
                msg,
                title,
                yesText,
                noText,
                AllIcons.General.QuestionDialog,
                ourKillLaunchOption)) {
          return false;
        }
      }

      LOG.info("Disconnecting existing session of the same launch configuration");
      info.getProcessHandler().detachProcess();
      return true;
    }
  }
}
