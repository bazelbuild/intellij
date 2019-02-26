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
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.tools.idea.run.ApkProvisionException;
import com.android.tools.idea.run.ApplicationIdProvider;
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.tasks.LaunchTask;
import com.android.tools.idea.run.util.LaunchStatus;
import com.android.tools.idea.testartifacts.instrumented.AndroidTestListener;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.android.run.deployinfo.BlazeAndroidDeployInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.annotations.NotNull;

final class StockAndroidTestLaunchTask implements LaunchTask {
  private static final String ID = "STOCK_ANDROID_TEST";

  private static final Logger LOG = Logger.getInstance(StockAndroidTestLaunchTask.class);

  private final BlazeAndroidTestRunConfigurationState configState;
  private final String instrumentationTestRunner;
  private final String testApplicationId;
  private final boolean waitForDebugger;

  private StockAndroidTestLaunchTask(
      BlazeAndroidTestRunConfigurationState configState,
      String runner,
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
      LaunchStatus launchStatus) {
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

    List<String> availableRunners = getRunnersFromManifest(deployInfo);
    if (availableRunners.isEmpty()) {
      launchStatus.terminateLaunch(
          String.format(
              "No instrumentation test runner is defined in the manifest.\n"
                  + "At least one instrumentation tag must be defined for the\n"
                  + "\"%1$s\" package in the AndroidManifest.xml, e.g.:\n"
                  + "\n"
                  + "<manifest\n"
                  + "    package=\"%1$s\"\n"
                  + "    xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                  + "\n"
                  + "    <instrumentation\n"
                  + "        android:name=\"androidx.test.runner.AndroidJUnitRunner\"\n"
                  + "        android:targetPackage=\"%1$s\">\n"
                  + "    </instrumentation>\n"
                  + "\n"
                  + "</manifest>",
              testPackage));
      // Note: Gradle users will never see the above message, so don't mention Gradle here.
      // Even if no runners are defined in build.gradle, Gradle will add a default to the manifest.
      return null;
    }
    String runner = configState.getInstrumentationRunnerClass();
    if (!StringUtil.isEmpty(runner)) {
      if (!availableRunners.contains(runner)) {
        launchStatus.terminateLaunch(
            String.format(
                "Instrumentation test runner \"%2$s\"\n"
                    + "is not defined for the \"%1$s\" package in the manifest.\n"
                    + "Clear the 'Specific instrumentation runner' field in your configuration\n"
                    + "to default to \"%3$s\",\n"
                    + "or add the runner to your AndroidManifest.xml:\n"
                    + "\n"
                    + "<manifest\n"
                    + "    package=\"%1$s\"\n"
                    + "    xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                    + "\n"
                    + "    <instrumentation\n"
                    + "        android:name=\"%2$s\"\n"
                    + "        android:targetPackage=\"%1$s\">\n"
                    + "    </instrumentation>\n"
                    + "\n"
                    + "</manifest>",
                testPackage, runner, availableRunners.get(0)));
        return null;
      }
    } else {
      // Default to the first available runner.
      runner = availableRunners.get(0);
    }

    return new StockAndroidTestLaunchTask(configState, runner, testPackage, waitForDebugger);
  }

  private static ImmutableList<String> getRunnersFromManifest(
      final BlazeAndroidDeployInfo deployInfo) {
    if (!ApplicationManager.getApplication().isReadAccessAllowed()) {
      return ApplicationManager.getApplication()
          .runReadAction(
              (Computable<ImmutableList<String>>) () -> getRunnersFromManifest(deployInfo));
    }

    Manifest manifest = deployInfo.getMergedManifest();
    if (manifest != null) {
      return ImmutableList.copyOf(
          manifest
              .getInstrumentations()
              .stream()
              .map(instrumentation -> instrumentation.getInstrumentationClass().getStringValue())
              .filter(Objects::nonNull)
              .collect(Collectors.toList()));
    }
    return ImmutableList.of();
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
  @SuppressWarnings("FutureReturnValueIgnored")
  public boolean perform(
      IDevice device, final LaunchStatus launchStatus, final ConsolePrinter printer) {
    printer.stdout("Running tests\n");

    final RemoteAndroidTestRunner runner =
        new RemoteAndroidTestRunner(testApplicationId, instrumentationTestRunner, device);
    switch (configState.getTestingType()) {
      case BlazeAndroidTestRunConfigurationState.TEST_ALL_IN_MODULE:
        break;
      case BlazeAndroidTestRunConfigurationState.TEST_ALL_IN_PACKAGE:
        runner.setTestPackageName(configState.getPackageName());
        break;
      case BlazeAndroidTestRunConfigurationState.TEST_CLASS:
        runner.setClassName(configState.getClassName());
        break;
      case BlazeAndroidTestRunConfigurationState.TEST_METHOD:
        runner.setMethodName(configState.getClassName(), configState.getMethodName());
        break;
      default:
        LOG.error(String.format("Unrecognized testing type: %d", configState.getTestingType()));
        return false;
    }
    runner.setDebug(waitForDebugger);
    runner.setRunOptions(configState.getExtraOptions());

    printer.stdout("$ adb shell " + runner.getAmInstrumentCommand());

    // run in a separate thread as this will block until the tests complete
    ApplicationManager.getApplication()
        .executeOnPooledThread(
            () -> {
              try {
                runner.run(new AndroidTestListener(launchStatus, printer));
              } catch (Exception e) {
                LOG.info(e);
                printer.stderr("Error: Unexpected exception while running tests: " + e);
              }
            });

    return true;
  }

  @NotNull
  @Override
  public String getId() {
    return ID;
  }
}
