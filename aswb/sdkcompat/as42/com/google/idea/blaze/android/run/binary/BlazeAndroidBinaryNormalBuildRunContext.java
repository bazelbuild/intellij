/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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

import com.android.ddmlib.IDevice;
import com.android.tools.idea.gradle.util.DynamicAppUtils;
import com.android.tools.idea.run.ApkFileUnit;
import com.android.tools.idea.run.ApkInfo;
import com.android.tools.idea.run.ApkProvider;
import com.android.tools.idea.run.ApkProvisionException;
import com.android.tools.idea.run.LaunchOptions;
import com.android.tools.idea.run.editor.AndroidDebugger;
import com.android.tools.idea.run.editor.AndroidDebuggerState;
import com.android.tools.idea.run.tasks.DebugConnectorTask;
import com.android.tools.idea.run.tasks.DeployTasksCompat;
import com.android.tools.idea.run.tasks.LaunchTask;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.android.run.runner.BlazeApkBuildStep;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.jetbrains.android.facet.AndroidFacet;

/** Compat for #api4.0 */
public class BlazeAndroidBinaryNormalBuildRunContext
    extends BlazeAndroidBinaryNormalBuildRunContextBase {
  BlazeAndroidBinaryNormalBuildRunContext(
      Project project,
      AndroidFacet facet,
      RunConfiguration runConfiguration,
      ExecutionEnvironment env,
      BlazeAndroidBinaryRunConfigurationState configState,
      BlazeApkBuildStep buildStep) {
    super(project, facet, runConfiguration, env, configState, buildStep);
  }

  @Nullable
  @Override
  @SuppressWarnings("unchecked")
  public DebugConnectorTask getDebuggerTask(
      AndroidDebugger androidDebugger,
      AndroidDebuggerState androidDebuggerState,
      Set<String> packageIds)
      throws ExecutionException {
    return androidDebugger.getConnectDebuggerTask(
        env,
        null,
        packageIds,
        facet,
        androidDebuggerState,
        runConfiguration.getType().getId(),
        null);
  }

  @Nullable
  @Override
  public ImmutableList<LaunchTask> getDeployTasks(IDevice device, LaunchOptions launchOptions)
      throws ExecutionException {
    ImmutableMap<String, List<File>> filesToInstall =
        getFilesToInstall(device, launchOptions, apkProvider);
    return ImmutableList.of(
        DeployTasksCompat.getDeployTask(project, env, launchOptions, filesToInstall));
  }

  /**
   * Returns a map from applicationId to the list of files to install for that applicationId,
   * excluding any files for features that are disabled.
   */
  public static ImmutableMap<String, List<File>> getFilesToInstall(
      IDevice device, LaunchOptions launchOptions, ApkProvider apkProvider)
      throws ExecutionException {
    Collection<ApkInfo> apks;
    try {
      apks = apkProvider.getApks(device);
    } catch (ApkProvisionException e) {
      throw new ExecutionException(e);
    }
    ImmutableMap.Builder<String, List<File>> filesToInstall = ImmutableMap.builder();
    List<String> disabledFeatures = launchOptions.getDisabledDynamicFeatures();
    for (ApkInfo apkInfo : apks) {
      filesToInstall.put(apkInfo.getApplicationId(), getFilesToInstall(apkInfo, disabledFeatures));
    }
    return filesToInstall.build();
  }

  private static List<File> getFilesToInstall(ApkInfo apkInfo, List<String> disabledFeatures) {
    if (apkInfo.getFiles().size() > 1) {
      return apkInfo.getFiles().stream()
          .filter(feature -> DynamicAppUtils.isFeatureEnabled(disabledFeatures, feature))
          .map(ApkFileUnit::getApkFile)
          .collect(Collectors.toList());
    } else {
      return ImmutableList.of(apkInfo.getFile());
    }
  }
}
