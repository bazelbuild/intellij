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
package com.google.idea.blaze.android.run.binary.mobileinstall;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.run.DeviceFutures;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.idea.blaze.android.run.BlazeAndroidRunConfigurationCommonState;
import com.google.idea.blaze.android.run.deployinfo.BlazeAndroidDeployInfo;
import com.google.idea.blaze.android.run.deployinfo.BlazeApkDeployInfoProtoHelper;
import com.google.idea.blaze.android.run.runner.BlazeAndroidDeviceSelector;
import com.google.idea.blaze.android.run.runner.BlazeApkBuildStep;
import com.google.idea.blaze.android.sync.model.AndroidSdkPlatform;
import com.google.idea.blaze.android.sync.model.BlazeAndroidSyncData;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.google.idea.blaze.base.async.process.ExternalTask;
import com.google.idea.blaze.base.async.process.LineProcessingOutputStream;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.experiments.BoolExperiment;
import com.google.idea.blaze.base.issueparser.IssueOutputLineProcessor;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.ScopedTask;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.output.PrintOutput;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.util.SaveUtil;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.vfs.LocalFileSystem;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.io.File;
import java.nio.file.Paths;
import java.util.concurrent.CancellationException;

/**
 * Builds and installs the APK using mobile-install.
 */
public class BlazeApkBuildStepMobileInstall implements BlazeApkBuildStep {
  private static final BoolExperiment USE_SDK_ADB = new BoolExperiment("use.sdk.adb", true);

  private final Project project;
  private final ExecutionEnvironment env;
  private final BlazeAndroidRunConfigurationCommonState commonState;
  private final ImmutableList<String> buildFlags;
  private final boolean useSplitApksIfPossible;
  private final SettableFuture<BlazeAndroidDeployInfo> deployInfoFuture = SettableFuture.create();

  public BlazeApkBuildStepMobileInstall(Project project,
                                        ExecutionEnvironment env,
                                        BlazeAndroidRunConfigurationCommonState commonState,
                                        ImmutableList<String> buildFlags,
                                        boolean useSplitApksIfPossible) {
    this.project = project;
    this.env = env;
    this.commonState = commonState;
    this.buildFlags = buildFlags;
    this.useSplitApksIfPossible = useSplitApksIfPossible;
  }

  @Override
  public boolean build(BlazeContext context, BlazeAndroidDeviceSelector.DeviceSession deviceSession) {
    final ScopedTask buildTask = new ScopedTask(context) {
      @Override
      protected void execute(@NotNull BlazeContext context) {
        boolean incrementalInstall = env.getExecutor() instanceof IncrementalInstallExecutor;

        DeviceFutures deviceFutures = deviceSession.deviceFutures;
        assert deviceFutures != null;
        IDevice device = resolveDevice(context, deviceFutures);
        if (device == null) {
          return;
        }
        BlazeCommand.Builder command = BlazeCommand.builder(Blaze.getBuildSystem(project), BlazeCommandName.MOBILE_INSTALL);
        command.addBlazeFlags(BlazeFlags.adbSerialFlags(device.getSerialNumber()));

        if (USE_SDK_ADB.getValue()) {
          File adb = getSdkAdb(project);
          if (adb != null) {
            command.addBlazeFlags(ImmutableList.of("--adb", adb.toString()));
          }
        }

        // split-apks only supported for API level 23 and above
        if (useSplitApksIfPossible && device.getVersion().getApiLevel() >= 23) {
          command.addBlazeFlags(BlazeFlags.SPLIT_APKS);
        }
        else if (incrementalInstall) {
          command.addBlazeFlags(BlazeFlags.INCREMENTAL);
        }
        WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProject(project);

        command
          .addTargets(commonState.getTarget())
          .addBlazeFlags(buildFlags)
          .addBlazeFlags(BlazeFlags.EXPERIMENTAL_SHOW_ARTIFACTS)
        ;

        BlazeApkDeployInfoProtoHelper deployInfoHelper = new BlazeApkDeployInfoProtoHelper(project, buildFlags);

        SaveUtil.saveAllFiles();
        int retVal = ExternalTask.builder(workspaceRoot, command.build())
          .context(context)
          .stderr(LineProcessingOutputStream.of(
            deployInfoHelper.getLineProcessor(),
            new IssueOutputLineProcessor(project, context, workspaceRoot)))
          .build()
          .run();
        LocalFileSystem.getInstance().refresh(true);

        if (retVal != 0) {
          context.setHasError();
          return;
        }

        BlazeAndroidDeployInfo deployInfo = deployInfoHelper.readDeployInfo(context);
        if (deployInfo == null) {
          IssueOutput.error("Could not read apk deploy info from build").submit(context);
          return;
        }
        deployInfoFuture.set(deployInfo);
      }
    };

    ListenableFuture<Void> buildFuture = BlazeExecutor.submitTask(
      project,
      String.format("Executing %s apk build", Blaze.buildSystemName(project)),
      buildTask
    );

    try {
      Futures.get(buildFuture, ExecutionException.class);
    }
    catch (ExecutionException e) {
      context.setHasError();
    }
    catch (CancellationException e) {
      context.setCancelled();
    }
    return context.shouldContinue();
  }

  public ListenableFuture<BlazeAndroidDeployInfo> getDeployInfo() {
    return deployInfoFuture;
  }

  private static File getSdkAdb(Project project) {
    BlazeProjectData projectData = BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (projectData == null) {
      return null;
    }
    BlazeAndroidSyncData syncData = projectData.syncState.get(BlazeAndroidSyncData.class);
    if (syncData == null) {
      return null;
    }
    AndroidSdkPlatform androidSdkPlatform = syncData.androidSdkPlatform;
    if (androidSdkPlatform == null) {
      return null;
    }
    Sdk sdk = AndroidSdkUtils.findSuitableAndroidSdk(androidSdkPlatform.androidSdk);
    if (sdk == null) {
      return null;
    }
    String homePath = sdk.getHomePath();
    if (homePath == null) {
      return null;
    }
    File adb = Paths.get(homePath, "platform-tools", "adb").toFile();
    if (!adb.exists()) {
      return null;
    }
    return adb;
  }

  @Nullable
  private static IDevice resolveDevice(@NotNull BlazeContext context, @NotNull DeviceFutures deviceFutures) {
    if (deviceFutures.get().size() != 1) {
      IssueOutput
        .error("Only one device can be used with mobile-install.")
        .submit(context);
      return null;
    }
    context.output(new PrintOutput("Waiting for mobile-install device target..."));
    try {
      return Futures.get(
        Iterables.getOnlyElement(deviceFutures.get()),
        ExecutionException.class
      );
    } catch (ExecutionException|UncheckedExecutionException e) {
      IssueOutput
        .error("Could not get device: " + e.getMessage())
        .submit(context);
      return null;
    } catch (CancellationException e) {
      // The user cancelled the device launch.
      context.setCancelled();
      return null;
    }
  }
}
