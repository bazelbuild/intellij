/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.run.test;

import com.android.builder.model.Variant;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.run.ApkProvisionException;
import com.android.tools.idea.run.ApplicationIdProvider;
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.tasks.LaunchTask;
import com.android.tools.idea.run.testing.AndroidTestListener;
import com.android.tools.idea.run.util.LaunchStatus;
import com.google.idea.blaze.android.run.deployinfo.BlazeAndroidDeployInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import org.jetbrains.android.dom.manifest.Instrumentation;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.Nullable;

final class StockAndroidTestLaunchTask implements LaunchTask {
  private static final Logger LOG = Logger.getInstance(StockAndroidTestLaunchTask.class);

  private final BlazeAndroidTestRunConfigurationState configState;
  @Nullable private final String instrumentationTestRunner;
  private final String testApplicationId;
  private final boolean waitForDebugger;

  private StockAndroidTestLaunchTask(
      BlazeAndroidTestRunConfigurationState configState,
      @Nullable String runner,
      String testPackage,
      boolean waitForDebugger) {
    this.configState = configState;
    this.instrumentationTestRunner = runner;
    this.waitForDebugger = waitForDebugger;
    this.testApplicationId = testPackage;
  }

  public static LaunchTask getStockTestLaunchTask(
      BlazeAndroidTestRunConfigurationState configState,
      ApplicationIdProvider applicationIdProvider,
      boolean waitForDebugger,
      BlazeAndroidDeployInfo deployInfo,
      AndroidFacet facet,
      LaunchStatus launchStatus) {
    String runner =
        StringUtil.isEmpty(configState.getInstrumentationRunnerClass())
            ? findInstrumentationRunner(deployInfo, facet)
            : configState.getInstrumentationRunnerClass();
    String testPackage;
    try {
      testPackage = applicationIdProvider.getTestPackageName();
      if (testPackage == null) {
        launchStatus.terminateLaunch("Unable to determine test package name");
        return null;
      }
    } catch (ApkProvisionException e) {
      launchStatus.terminateLaunch("Unable to determine test package name");
      return null;
    }

    return new StockAndroidTestLaunchTask(configState, runner, testPackage, waitForDebugger);
  }

  @Nullable
  private static String findInstrumentationRunner(
      BlazeAndroidDeployInfo deployInfo, AndroidFacet facet) {
    String runner = getRunnerFromManifest(deployInfo);

    // TODO: Resolve direct AndroidGradleModel dep (b/22596984)
    AndroidGradleModel androidModel = AndroidGradleModel.get(facet);
    if (runner == null && androidModel != null) {
      Variant selectedVariant = androidModel.getSelectedVariant();
      String testRunner = selectedVariant.getMergedFlavor().getTestInstrumentationRunner();
      if (testRunner != null) {
        runner = testRunner;
      }
    }

    // Fall back to the default runner.
    if (runner == null) {
      runner = InstrumentationRunnerProvider.getDefaultInstrumentationRunnerClass();
    }

    return runner;
  }

  @Nullable
  private static String getRunnerFromManifest(final BlazeAndroidDeployInfo deployInfo) {
    if (!ApplicationManager.getApplication().isReadAccessAllowed()) {
      return ApplicationManager.getApplication()
          .runReadAction((Computable<String>) () -> getRunnerFromManifest(deployInfo));
    }

    Manifest manifest = deployInfo.getMergedManifest();
    if (manifest != null) {
      for (Instrumentation instrumentation : manifest.getInstrumentations()) {
        if (instrumentation != null) {
          PsiClass instrumentationClass = instrumentation.getInstrumentationClass().getValue();
          if (instrumentationClass != null) {
            return instrumentationClass.getQualifiedName();
          }
        }
      }
    }
    return null;
  }

  @Override
  public String getDescription() {
    return "Launching instrumentation runner";
  }

  @Override
  public int getDuration() {
    return 2;
  }

  @Override
  public boolean perform(
      IDevice device, final LaunchStatus launchStatus, final ConsolePrinter printer) {
    printer.stdout("Running tests\n");

    final RemoteAndroidTestRunner runner =
        new RemoteAndroidTestRunner(testApplicationId, instrumentationTestRunner, device);
    switch (configState.getTestingType()) {
      case BlazeAndroidTestRunConfigurationState.TEST_ALL_IN_PACKAGE:
        runner.setTestPackageName(configState.getPackageName());
        break;
      case BlazeAndroidTestRunConfigurationState.TEST_CLASS:
        runner.setClassName(configState.getClassName());
        break;
      case BlazeAndroidTestRunConfigurationState.TEST_METHOD:
        runner.setMethodName(configState.getClassName(), configState.getMethodName());
        break;
    }
    runner.setDebug(waitForDebugger);
    runner.setRunOptions(configState.getExtraOptions());

    printer.stdout("$ adb shell " + runner.getAmInstrumentCommand());

    // run in a separate thread as this will block until the tests complete
    ApplicationManager.getApplication()
        .executeOnPooledThread(
            new Runnable() {
              @Override
              public void run() {
                try {
                  runner.run(new AndroidTestListener(launchStatus, printer));
                } catch (Exception e) {
                  LOG.info(e);
                  printer.stderr("Error: Unexpected exception while running tests: " + e);
                }
              }
            });

    return true;
  }
}
