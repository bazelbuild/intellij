/*
 * Copyright 2021 The Bazel Authors. All rights reserved.
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

import com.android.tools.idea.BaseAsCompat;
import com.android.tools.idea.run.ApkInfo;
import com.android.tools.idea.run.tasks.LaunchContext;
import com.android.tools.idea.run.tasks.LaunchResult;
import com.android.tools.idea.run.tasks.LaunchTask;
import com.google.common.base.Stopwatch;
import com.google.idea.blaze.android.run.LaunchMetrics;
import com.google.wireless.android.sdk.stats.LaunchTaskDetail;
import java.util.Collection;
import org.jetbrains.annotations.TestOnly;

/** A wrapper launch task that wraps the given deployment task and logs the deployment latency. */
public class DeploymentTimingReporterTask implements LaunchTask {
  private final LaunchTask deployTask;
  private final String launchId;

  public DeploymentTimingReporterTask(String launchId, LaunchTask deployTask) {
    this.launchId = launchId;
    this.deployTask = deployTask;
  }

  @Override
  public String getDescription() {
    return deployTask.getDescription();
  }

  @Override
  public int getDuration() {
    return deployTask.getDuration();
  }

  @Override
  public boolean shouldRun(LaunchContext launchContext) {
    return deployTask.shouldRun(launchContext);
  }

  @Override
  public LaunchResult run(LaunchContext launchContext) {
    Stopwatch s = Stopwatch.createStarted();
    LaunchResult launchResult = deployTask.run(launchContext);
    LaunchMetrics.logDeploymentTime(
        launchId, s.elapsed(), BaseAsCompat.wasSuccessfulLaunch(launchResult));
    return launchResult;
  }

  @Override
  public String getId() {
    return deployTask.getId();
  }

  @Override
  public Collection<ApkInfo> getApkInfos() {
    return deployTask.getApkInfos();
  }

  @Override
  public Collection<LaunchTaskDetail> getSubTaskDetails() {
    return deployTask.getSubTaskDetails();
  }

  @TestOnly
  public LaunchTask getWrappedTask() {
    return deployTask;
  }
}
