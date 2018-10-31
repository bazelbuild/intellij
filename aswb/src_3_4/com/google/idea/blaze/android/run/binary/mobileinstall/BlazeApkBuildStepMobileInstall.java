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

import com.android.ddmlib.IDevice;
import com.android.tools.idea.run.ApkProvisionException;
import com.android.tools.idea.run.DeviceFutures;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.idea.blaze.android.run.deployinfo.BlazeAndroidDeployInfo;
import com.google.idea.blaze.android.run.deployinfo.BlazeApkDeployInfoProtoHelper;
import com.google.idea.blaze.android.run.runner.BlazeAndroidDeviceSelector;
import com.google.idea.blaze.android.run.runner.BlazeApkBuildStep;
import com.google.idea.blaze.base.async.executor.ProgressiveTaskWithProgressIndicator;
import com.google.idea.blaze.base.async.process.ExternalTask;
import com.google.idea.blaze.base.async.process.LineProcessingOutputStream;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper.GetArtifactsException;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelperProvider;
import com.google.idea.blaze.base.console.BlazeConsoleLineProcessorProvider;
import com.google.idea.blaze.base.filecache.FileCaches;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.ScopedTask;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.output.StatusOutput;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.util.SaveUtil;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.execution.ExecutionException;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.concurrent.CancellationException;
import javax.annotation.Nullable;
import org.jetbrains.android.sdk.AndroidSdkUtils;

/** Builds and installs the APK using mobile-install. */
public class BlazeApkBuildStepMobileInstall implements BlazeApkBuildStep {
  private static final BoolExperiment USE_SDK_ADB = new BoolExperiment("use.sdk.adb", true);

  private final Project project;
  private final Label label;
  private final ImmutableList<String> blazeFlags;
  private final ImmutableList<String> exeFlags;
  private BlazeAndroidDeployInfo deployInfo = null;

  public BlazeApkBuildStepMobileInstall(
      Project project,
      Label label,
      ImmutableList<String> blazeFlags,
      ImmutableList<String> exeFlags) {
    this.project = project;
    this.label = label;
    this.blazeFlags = blazeFlags;
    this.exeFlags = exeFlags;
  }

  @Override
  public boolean build(
      BlazeContext context, BlazeAndroidDeviceSelector.DeviceSession deviceSession) {
    ScopedTask<Void> buildTask =
        new ScopedTask<Void>(context) {
          @Override
          protected Void execute(BlazeContext context) {
            DeviceFutures deviceFutures = deviceSession.deviceFutures;
            assert deviceFutures != null;

            context.output(new StatusOutput("Waiting for target device..."));
            IDevice device = resolveDevice(context, deviceFutures);
            if (device == null) {
              return null;
            }

            context.output(new StatusOutput("Invoking mobile-install..."));
            BlazeCommand.Builder command =
                BlazeCommand.builder(
                    Blaze.getBuildSystemProvider(project).getBinaryPath(project),
                    BlazeCommandName.MOBILE_INSTALL);

            command.addBlazeFlags(BlazeFlags.DEVICE, device.getSerialNumber());
            // Redundant, but we need this to get around bug in bazel.
            // https://github.com/bazelbuild/bazel/issues/4922
            command.addBlazeFlags(
                BlazeFlags.ADB_ARG + "-s ", BlazeFlags.ADB_ARG + device.getSerialNumber());

            if (USE_SDK_ADB.getValue()) {
              File adb = AndroidSdkUtils.getAdb(project);
              if (adb != null) {
                command.addBlazeFlags(BlazeFlags.ADB, adb.toString());
              }
            }

            WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProject(project);

            BlazeApkDeployInfoProtoHelper deployInfoHelper =
                new BlazeApkDeployInfoProtoHelper(project, blazeFlags);
            try (BuildResultHelper buildResultHelper =
                BuildResultHelperProvider.forFiles(
                    project, fileName -> fileName.endsWith("_mi.deployinfo.pb"))) {

              command
                  .addTargets(label)
                  .addBlazeFlags(blazeFlags)
                  .addBlazeFlags(buildResultHelper.getBuildFlags())
                  .addExeFlags(exeFlags);

              SaveUtil.saveAllFiles();
              int retVal =
                  ExternalTask.builder(workspaceRoot)
                      .addBlazeCommand(command.build())
                      .context(context)
                      .stderr(
                          LineProcessingOutputStream.of(
                              BlazeConsoleLineProcessorProvider.getAllStderrLineProcessors(
                                  context)))
                      .build()
                      .run();
              FileCaches.refresh(project);

              if (retVal != 0) {
                context.setHasError();
                return null;
              }
              try {
                context.output(new StatusOutput("Reading deployment information..."));
                deployInfo = deployInfoHelper.readDeployInfo(context, buildResultHelper);
              } catch (GetArtifactsException e) {
                IssueOutput.error("Could not read apk deploy info from build: " + e.getMessage())
                    .submit(context);
                return null;
              }
              if (deployInfo == null) {
                IssueOutput.error("Could not read apk deploy info from build").submit(context);
              }
              return null;
            }
          }
        };

    ListenableFuture<Void> buildFuture =
        ProgressiveTaskWithProgressIndicator.builder(
                project, String.format("Executing %s apk build", Blaze.buildSystemName(project)))
            .submitTaskWithResult(buildTask);

    try {
      Futures.getChecked(buildFuture, ExecutionException.class);
    } catch (ExecutionException e) {
      context.setHasError();
    } catch (CancellationException e) {
      context.setCancelled();
    }
    return context.shouldContinue();
  }

  @Override
  public BlazeAndroidDeployInfo getDeployInfo() throws ApkProvisionException {
    if (deployInfo != null) {
      return deployInfo;
    }
    throw new ApkProvisionException("Failed to read APK deploy info");
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
