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
package com.google.idea.blaze.android.run.binary.mobileinstall;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.run.ApkProvisionException;
import com.android.tools.idea.run.DeviceFutures;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.devtools.build.lib.rules.android.deployinfo.AndroidDeployInfoOuterClass.AndroidDeployInfo;
import com.google.idea.blaze.android.run.binary.mobileinstall.AdbTunnelConfigurator.AdbTunnelConfiguratorProvider;
import com.google.idea.blaze.android.run.deployinfo.BlazeAndroidDeployInfo;
import com.google.idea.blaze.android.run.deployinfo.BlazeApkDeployInfoProtoHelper;
import com.google.idea.blaze.android.run.deployinfo.BlazeApkDeployInfoProtoHelper.GetDeployInfoException;
import com.google.idea.blaze.android.run.runner.BlazeAndroidDeviceSelector;
import com.google.idea.blaze.android.run.runner.BlazeApkBuildStep;
import com.google.idea.blaze.android.run.runner.ExecRootUtil;
import com.google.idea.blaze.base.async.process.ExternalTask;
import com.google.idea.blaze.base.async.process.LineProcessingOutputStream;
import com.google.idea.blaze.base.async.process.PrintOutputLineProcessor;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper.GetArtifactsException;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelperProvider;
import com.google.idea.blaze.base.console.BlazeConsoleLineProcessorProvider;
import com.google.idea.blaze.base.filecache.FileCaches;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.output.StatusOutput;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BuildSystem;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.util.SaveUtil;
import com.intellij.execution.ExecutionException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.net.InetSocketAddress;
import java.util.NoSuchElementException;
import java.util.concurrent.CancellationException;
import javax.annotation.Nullable;

/** Builds and installs the APK using mobile-install. */
public class BlazeApkBuildStepMobileInstall implements BlazeApkBuildStep {
  private static final Logger log = Logger.getInstance(BlazeApkBuildStepMobileInstall.class);
  private final Project project;
  private final Label label;
  private final ImmutableList<String> blazeFlags;
  private final ImmutableList<String> exeFlags;
  private final BlazeApkDeployInfoProtoHelper deployInfoHelper;
  private BlazeAndroidDeployInfo deployInfo = null;

  /**
   * Returns the correct DeployInfo file suffix for mobile-install classic (bazel). This should be
   * removed once mobile-install v2 is open sourced, at which point the internal and external
   * versions will both use _mi.deployinfo.pb
   */
  public static String getDeployInfoSuffix(BuildSystem buildSystem) {
    return buildSystem == BuildSystem.Bazel ? "_incremental.deployinfo.pb" : "_mi.deployinfo.pb";
  }

  @VisibleForTesting
  public BlazeApkBuildStepMobileInstall(
      Project project,
      Label label,
      ImmutableList<String> blazeFlags,
      ImmutableList<String> exeFlags,
      BlazeApkDeployInfoProtoHelper deployInfoHelper) {
    this.project = project;
    this.label = label;
    this.blazeFlags = blazeFlags;
    this.exeFlags = exeFlags;
    this.deployInfoHelper = deployInfoHelper;
  }

  public BlazeApkBuildStepMobileInstall(
      Project project,
      Label label,
      ImmutableList<String> blazeFlags,
      ImmutableList<String> exeFlags) {
    this(project, label, blazeFlags, exeFlags, new BlazeApkDeployInfoProtoHelper());
  }

  @Override
  public void build(BlazeContext context, BlazeAndroidDeviceSelector.DeviceSession deviceSession) {
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();

    if (projectData == null) {
      IssueOutput.error("Missing project data. Please sync and try again.").submit(context);
      return;
    }

    DeviceFutures deviceFutures = deviceSession.deviceFutures;
    if (deviceFutures == null) {
      IssueOutput.error("Error fetching devices!").submit(context);
      return;
    }

    context.output(new StatusOutput("Waiting for target device..."));
    IDevice device = resolveDevice(context, deviceFutures);
    if (device == null) {
      return;
    }

    BlazeCommand.Builder command =
        BlazeCommand.builder(
            Blaze.getBuildSystemProvider(project).getBinaryPath(project),
            BlazeCommandName.MOBILE_INSTALL);

    // Redundant, but we need this to get around bug in bazel.
    // https://github.com/bazelbuild/bazel/issues/4922
    command.addBlazeFlags(
        BlazeFlags.ADB_ARG + "-s ", BlazeFlags.ADB_ARG + device.getSerialNumber());
    MobileInstallAdbLocationProvider.getAdbLocationForMobileInstall(project)
        .ifPresent((location) -> command.addBlazeFlags(BlazeFlags.ADB, location));

    WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProject(project);
    final String deployInfoSuffix = getDeployInfoSuffix(Blaze.getBuildSystem(project));

    try (BuildResultHelper buildResultHelper = BuildResultHelperProvider.create(project);
        AdbTunnelConfigurator tunnelConfig = getTunnelConfigurator(context)) {
      tunnelConfig.setupConnection(context);
      String deviceFlag = device.getSerialNumber();
      if (tunnelConfig.isActive()) {
        deviceFlag += ":tcp:" + tunnelConfig.getAdbServerPort();
      } else {
        InetSocketAddress adbAddr = AndroidDebugBridge.getSocketAddress();
        if (adbAddr == null) {
          IssueOutput.warn(
                  "Can't get ADB server port, please ensure ADB server is running. Will fallback to"
                      + " the default adb server.")
              .submit(context);
        } else {
          deviceFlag += ":tcp:" + adbAddr.getPort();
        }
      }
      command.addBlazeFlags(BlazeFlags.DEVICE, deviceFlag);

      command
          .addTargets(label)
          .addBlazeFlags(blazeFlags)
          .addBlazeFlags(buildResultHelper.getBuildFlags())
          .addExeFlags(exeFlags)
          // MI launches apps by default. Defer app launch to BlazeAndroidLaunchTasksProvider.
          .addExeFlags("--nolaunch_app");

      SaveUtil.saveAllFiles();
      context.output(new StatusOutput("Invoking mobile-install..."));
      int retVal =
          ExternalTask.builder(workspaceRoot)
              .addBlazeCommand(command.build())
              .context(context)
              .stdout(LineProcessingOutputStream.of(new PrintOutputLineProcessor(context)))
              .stderr(
                  LineProcessingOutputStream.of(
                      BlazeConsoleLineProcessorProvider.getAllStderrLineProcessors(context)))
              .build()
              .run();
      ListenableFuture<Void> unusedFuture = FileCaches.refresh(project, context);

      if (retVal != 0) {
        IssueOutput.error("Blaze build failed. See Blaze Console for details.").submit(context);
        return;
      }

      context.output(new StatusOutput("Reading deployment information..."));
      String executionRoot =
          ExecRootUtil.getExecutionRoot(buildResultHelper, project, blazeFlags, context);
      if (executionRoot == null) {
        IssueOutput.error("Could not locate execroot!").submit(context);
        return;
      }

      AndroidDeployInfo deployInfoProto =
          deployInfoHelper.readDeployInfoProtoForTarget(
              label, buildResultHelper, fileName -> fileName.endsWith(deployInfoSuffix));
      deployInfo =
          deployInfoHelper.extractDeployInfoAndInvalidateManifests(
              project, new File(executionRoot), deployInfoProto);
    } catch (GetArtifactsException e) {
      IssueOutput.error("Could not read BEP output: " + e.getMessage()).submit(context);
    } catch (GetDeployInfoException e) {
      IssueOutput.error("Could not read apk deploy info from build: " + e.getMessage())
          .submit(context);
    }
  }

  @Override
  public BlazeAndroidDeployInfo getDeployInfo() throws ApkProvisionException {
    if (deployInfo != null) {
      return deployInfo;
    }
    throw new ApkProvisionException("Failed to read APK deploy info");
  }

  private static AdbTunnelConfigurator getTunnelConfigurator(BlazeContext context) {
    try {
      AdbTunnelConfigurator configurator =
          Iterables.getOnlyElement(AdbTunnelConfiguratorProvider.EP_NAME.getExtensionList())
              .createConfigurator(context);
      if (configurator != null) {
        return configurator;
      }
    } catch (NoSuchElementException ex) {
      // Fail quietly when there's no configurable registered.
    } catch (IllegalArgumentException ex) {
      log.warn("More than one provider registered; there should only be one!");
    }
    return new AdbTunnelConfigurator() {
      @Override
      public void setupConnection(BlazeContext context) {}

      @Override
      public void tearDownConnection() {}

      @Override
      public int getAdbServerPort() {
        throw new IllegalStateException("Stub configurator is inactive.");
      }

      @Override
      public boolean isActive() {
        return false;
      }
    };
  }

  @Nullable
  private static IDevice resolveDevice(BlazeContext context, DeviceFutures deviceFutures) {
    if (deviceFutures.get().size() != 1) {
      IssueOutput.error("Only one device can be used with mobile-install.").submit(context);
      return null;
    }
    try {
      return Futures.getChecked(
          Iterables.getOnlyElement(deviceFutures.get()), ExecutionException.class);
    } catch (ExecutionException | UncheckedExecutionException e) {
      IssueOutput.error("Could not get device: " + e.getMessage()).submit(context);
      return null;
    } catch (CancellationException e) {
      // The user cancelled the device launch.
      context.setCancelled();
      return null;
    }
  }
}
