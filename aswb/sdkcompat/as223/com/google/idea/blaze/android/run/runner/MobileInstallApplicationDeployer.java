/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.google.idea.blaze.android.run.runner;

import com.android.ddmlib.IDevice;
import com.android.tools.deployer.ApkParser;
import com.android.tools.deployer.Deployer;
import com.android.tools.deployer.DeployerException;
import com.android.tools.deployer.model.Apk;
import com.android.tools.deployer.model.App;
import com.android.tools.idea.execution.common.ApplicationDeployer;
import com.android.tools.idea.execution.common.DeployOptions;
import com.android.tools.idea.run.ApkFileUnit;
import com.android.tools.idea.run.ApkInfo;
import com.android.tools.idea.run.configuration.execution.AdbCommandCaptureLoggerWithConsole;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

/** Deploys mobile install application. */
public class MobileInstallApplicationDeployer implements ApplicationDeployer {
  private static final Logger LOG = Logger.getInstance(MobileInstallApplicationDeployer.class);
  private final ConsoleView myConsole;

  public MobileInstallApplicationDeployer(ConsoleView console) {
    myConsole = console;
  }

  @NotNull
  @Override
  public Deployer.Result fullDeploy(
      @NotNull IDevice device,
      @NotNull ApkInfo apkInfo,
      @NotNull DeployOptions deployOptions,
      ProgressIndicator indicator)
      throws DeployerException {
    final List<String> apkPaths =
        apkInfo.getFiles().stream()
            .map(ApkFileUnit::getApkPath)
            .map(Path::toString)
            .collect(Collectors.toList());
    final List<Apk> apks = new ApkParser().parsePaths(apkPaths);
    App app =
        new App(
            apkInfo.getApplicationId(),
            apks,
            device,
            new AdbCommandCaptureLoggerWithConsole(LOG, myConsole));
    return new Deployer.Result(false, false, false, app);
  }

  @NotNull
  @Override
  public Deployer.Result applyChangesDeploy(
      @NotNull IDevice device,
      @NotNull ApkInfo app,
      @NotNull DeployOptions deployOptions,
      ProgressIndicator indicator)
      throws DeployerException {
    throw new RuntimeException("Apply changes is not supported for mobile-install");
  }

  @NotNull
  @Override
  public Deployer.Result applyCodeChangesDeploy(
      @NotNull IDevice device,
      @NotNull ApkInfo app,
      @NotNull DeployOptions deployOptions,
      ProgressIndicator indicator)
      throws DeployerException {
    throw new RuntimeException("Apply code changes is not supported for mobile-install");
  }
}
