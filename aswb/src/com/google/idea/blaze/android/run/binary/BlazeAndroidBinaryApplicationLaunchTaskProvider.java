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
package com.google.idea.blaze.android.run.binary;

import com.android.tools.idea.run.ApkProvisionException;
import com.android.tools.idea.run.ApplicationIdProvider;
import com.android.tools.idea.run.activity.StartActivityFlagsProvider;
import com.android.tools.idea.run.tasks.AndroidDeepLinkLaunchTask;
import com.android.tools.idea.run.tasks.DefaultActivityLaunchTask;
import com.android.tools.idea.run.tasks.LaunchTask;
import com.android.tools.idea.run.tasks.SpecificActivityLaunchTask;
import com.android.tools.idea.run.util.ProcessHandlerLaunchStatus;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.io.File;

/** Provides the launch task for android_binary */
public class BlazeAndroidBinaryApplicationLaunchTaskProvider {
  private static final Logger LOG =
      Logger.getInstance(BlazeAndroidBinaryApplicationLaunchTaskProvider.class);

  public static LaunchTask getApplicationLaunchTask(
      Project project,
      ApplicationIdProvider applicationIdProvider,
      File mergedManifestFile,
      BlazeAndroidBinaryRunConfigurationState configState,
      StartActivityFlagsProvider startActivityFlagsProvider,
      ProcessHandlerLaunchStatus processHandlerLaunchStatus) {
    try {
      String applicationId = applicationIdProvider.getPackageName();

      final LaunchTask launchTask;

      switch (configState.getMode()) {
        case BlazeAndroidBinaryRunConfigurationState.DO_NOTHING:
          launchTask = null;
          break;
        case BlazeAndroidBinaryRunConfigurationState.LAUNCH_DEFAULT_ACTIVITY:
          BlazeDefaultActivityLocator activityLocator =
              new BlazeDefaultActivityLocator(project, mergedManifestFile);
          launchTask =
              new DefaultActivityLaunchTask(
                  applicationId, activityLocator, startActivityFlagsProvider);
          break;
        case BlazeAndroidBinaryRunConfigurationState.LAUNCH_SPECIFIC_ACTIVITY:
          launchTask =
              new SpecificActivityLaunchTask(
                  applicationId, configState.getActivityClass(), startActivityFlagsProvider);
          break;
        case BlazeAndroidBinaryRunConfigurationState.LAUNCH_DEEP_LINK:
          launchTask =
              new AndroidDeepLinkLaunchTask(configState.getDeepLink(), startActivityFlagsProvider);
          break;
        default:
          launchTask = null;
          break;
      }
      return launchTask;
    } catch (ApkProvisionException e) {
      LOG.error(e);
      processHandlerLaunchStatus.terminateLaunch("Unable to identify application id");
      return null;
    }
  }
}
