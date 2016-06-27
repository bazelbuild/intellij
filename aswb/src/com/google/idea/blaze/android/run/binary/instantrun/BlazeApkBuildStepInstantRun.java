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
package com.google.idea.blaze.android.run.binary.instantrun;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.fd.*;
import com.android.tools.idea.gradle.run.MakeBeforeRunTaskProvider;
import com.android.tools.idea.run.*;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.idea.blaze.android.manifest.ManifestParser;
import com.google.idea.blaze.android.run.BlazeAndroidRunConfigurationCommonState;
import com.google.idea.blaze.android.run.runner.BlazeAndroidDeviceSelector;
import com.google.idea.blaze.android.run.runner.BlazeApkBuildStep;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.google.idea.blaze.base.async.process.ExternalTask;
import com.google.idea.blaze.base.async.process.LineProcessingOutputStream;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.command.ExperimentalShowArtifactsLineProcessor;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.issueparser.IssueOutputLineProcessor;
import com.google.idea.blaze.base.metrics.Action;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.ScopedTask;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.scopes.LoggedTimingScope;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.util.SaveUtil;
import com.google.repackaged.devtools.build.lib.rules.android.apkmanifest.ApkManifestOuterClass;
import com.intellij.execution.Executor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Builds the APK using normal blaze build.
 */
class BlazeApkBuildStepInstantRun implements BlazeApkBuildStep {
  private static final Logger LOG = Logger.getInstance(BlazeApkBuildStepInstantRun.class);

  private final Project project;
  private final Executor executor;
  private final ExecutionEnvironment env;
  private final BlazeAndroidRunConfigurationCommonState commonState;
  private final ImmutableList<String> buildFlags;
  private final File instantRunArtifactDirectory;
  private final File instantRunGradleBuildFile;
  private final File instantRunBuildInfoFile;
  private final File instantRunGradlePropertiesFile;


  public static class BuildResult {
    public final File executionRoot;
    public final File mergedManifestFile;
    public final File apkManifestProtoFile;
    public final ApkManifestOuterClass.ApkManifest apkManifestProto;
    public BuildResult(File executionRoot,
                       File mergedManifestFile,
                       File apkManifestProtoFile,
                       ApkManifestOuterClass.ApkManifest apkManifestProto) {
      this.executionRoot = executionRoot;
      this.mergedManifestFile = mergedManifestFile;
      this.apkManifestProtoFile = apkManifestProtoFile;
      this.apkManifestProto = apkManifestProto;
    }
  }
  private final SettableFuture<BuildResult> buildResultFuture = SettableFuture.create();
  private final SettableFuture<ApplicationIdProvider> applicationIdProviderFuture = SettableFuture.create();
  private final SettableFuture<InstantRunContext> instantRunContextFuture = SettableFuture.create();
  private final SettableFuture<InstantRunBuildAnalyzer> instantRunBuildAnalyzerFuture = SettableFuture.create();

  public BlazeApkBuildStepInstantRun(Project project,
                                     ExecutionEnvironment env,
                                     BlazeAndroidRunConfigurationCommonState commonState,
                                     ImmutableList<String> buildFlags) {
    this.project = project;
    this.executor = env.getExecutor();
    this.env = env;
    this.commonState = commonState;
    this.buildFlags = buildFlags;
    this.instantRunArtifactDirectory = BlazeInstantRunGradleIntegration.getInstantRunArtifactDirectory(project, commonState.getTarget());
    this.instantRunBuildInfoFile = new File(instantRunArtifactDirectory, "build/reload-dex/debug/build-info.xml");
    this.instantRunGradleBuildFile = new File(instantRunArtifactDirectory, "build.gradle");
    this.instantRunGradlePropertiesFile = new File(instantRunArtifactDirectory, "gradle.properties");
  }

  @Override
  public boolean build(BlazeContext context, BlazeAndroidDeviceSelector.DeviceSession deviceSession) {
    if (!instantRunArtifactDirectory.exists() && !instantRunArtifactDirectory.mkdirs()) {
      IssueOutput.error("Could not create instant run artifact directory: " + instantRunArtifactDirectory).submit(context);
      return false;
    }

    BuildResult buildResult = buildApkManifest(context);
    if (buildResult == null) {
      return false;
    }

    String gradleUrl = BlazeInstantRunGradleIntegration.getGradleUrl(context);
    if (gradleUrl == null) {
      return false;
    }

    ApplicationIdProvider applicationIdProvider = new BlazeInstantRunApplicationIdProvider(project, buildResult);
    applicationIdProviderFuture.set(applicationIdProvider);

    // Write build.gradle
    try (PrintWriter printWriter = new PrintWriter(instantRunGradleBuildFile)) {
      printWriter.print(BlazeInstantRunGradleIntegration.getGradleBuildInfoString(
        gradleUrl,
        buildResult.executionRoot,
        buildResult.apkManifestProtoFile
      ));
    }
    catch (IOException e) {
      IssueOutput.error("Could not write build.gradle file: " + e).submit(context);
      return false;
    }

    // Write gradle.properties
    try (PrintWriter printWriter = new PrintWriter(instantRunGradlePropertiesFile)) {
      printWriter.print(BlazeInstantRunGradleIntegration.getGradlePropertiesString());
    }
    catch (IOException e) {
      IssueOutput.error("Could not write build.gradle file: " + e).submit(context);
      return false;
    }

    String applicationId = null;
    try {
      applicationId = applicationIdProvider.getPackageName();
    }
    catch (ApkProvisionException e) {
      return false;
    }

    return invokeGradleIrTasks(context, deviceSession, buildResult, applicationId);
  }

  private BuildResult buildApkManifest(BlazeContext context) {
    final ScopedTask buildTask = new ScopedTask(context) {
      @Override
      protected void execute(@NotNull BlazeContext context) {
        WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProject(project);
        String executionRoot = getExecutionRoot(context, workspaceRoot);
        if (executionRoot == null) {
          IssueOutput.error("Could not get execution root").submit(context);
          return;
        }

        BlazeCommand.Builder command = BlazeCommand.builder(Blaze.getBuildSystem(project), BlazeCommandName.BUILD);

        command
          .addTargets(commonState.getTarget())
          .addBlazeFlags(buildFlags)
          .addBlazeFlags("--output_groups=apk_manifest")
          .addBlazeFlags(BlazeFlags.EXPERIMENTAL_SHOW_ARTIFACTS)
        ;

        List<File> apkManifestFiles = Lists.newArrayList();

        SaveUtil.saveAllFiles();
        int retVal = ExternalTask.builder(workspaceRoot, command.build())
          .context(context)
          .stderr(LineProcessingOutputStream.of(
            new ExperimentalShowArtifactsLineProcessor(apkManifestFiles, "apk_manifest"),
            new IssueOutputLineProcessor(project, context, workspaceRoot)
          ))
          .build()
          .run(new LoggedTimingScope(project, Action.BLAZE_BUILD));
        LocalFileSystem.getInstance().refresh(true);

        if (retVal != 0) {
          context.setHasError();
          return;
        }

        File apkManifestFile = Iterables.getOnlyElement(apkManifestFiles, null);
        if (apkManifestFile == null) {
          IssueOutput.error("Could not find APK manifest file").submit(context);
          return;
        }

        ApkManifestOuterClass.ApkManifest apkManifestProto;
        try (InputStream inputStream = new FileInputStream(apkManifestFile)) {
          apkManifestProto = ApkManifestOuterClass.ApkManifest.parseFrom(inputStream);
        }
        catch (IOException e) {
          LOG.error(e);
          IssueOutput.error("Error parsing apk proto").submit(context);
          return;
        }

        // Refresh the manifest
        File mergedManifestFile = new File(executionRoot, apkManifestProto.getAndroidManifest().getExecRootPath());
        ManifestParser.getInstance(project).refreshManifests(ImmutableList.of(mergedManifestFile));

        BuildResult buildResult = new BuildResult(
          new File(executionRoot),
          mergedManifestFile,
          apkManifestFile,
          apkManifestProto
        );
        buildResultFuture.set(buildResult);
      }
    };

    BlazeExecutor.submitTask(
      project,
      String.format("Executing %s apk build", Blaze.buildSystemName(project)),
      buildTask
    );

    try {
      BuildResult buildResult = buildResultFuture.get();
      if (!context.shouldContinue()) {
        return null;
      }
      return buildResult;
    }
    catch (InterruptedException|ExecutionException e) {
      context.setHasError();
    }
    catch (CancellationException e) {
      context.setCancelled();
    }
    return null;
  }

  private boolean invokeGradleIrTasks(BlazeContext context,
                                      BlazeAndroidDeviceSelector.DeviceSession deviceSession,
                                      BuildResult buildResult,
                                      String applicationId) {
    InstantRunContext instantRunContext = new BlazeInstantRunContext(
      project,
      buildResult.apkManifestProto,
      applicationId,
      instantRunBuildInfoFile
    );
    instantRunContextFuture.set(instantRunContext);
    ProcessHandler previousSessionProcessHandler = deviceSession.sessionInfo != null
                                                   ? deviceSession.sessionInfo.getProcessHandler()
                                                   : null;
    DeviceFutures deviceFutures = deviceSession.deviceFutures;
    assert deviceFutures != null;
    List<AndroidDevice> targetDevices = deviceFutures.getDevices();
    AndroidDevice androidDevice = targetDevices.get(0);
    IDevice device = getLaunchedDevice(androidDevice);

    AndroidRunConfigContext runConfigContext = new AndroidRunConfigContext();
    runConfigContext.setTargetDevices(deviceFutures);

    AndroidSessionInfo info = deviceSession.sessionInfo;
    runConfigContext.setSameExecutorAsPreviousSession(info != null && executor.getId().equals(info.getExecutorId()));
    runConfigContext.setCleanRerun(InstantRunUtils.isCleanReRun(env));

    InstantRunBuilder instantRunBuilder = new InstantRunBuilder(
      device,
      instantRunContext,
      runConfigContext,
      new BlazeInstantRunTasksProvider(),
      RunAsValidityService.getInstance()
    );

    try {
      List<String> cmdLineArgs = Lists.newArrayList();
      cmdLineArgs.addAll(MakeBeforeRunTaskProvider.getDeviceSpecificArguments(targetDevices));
      BlazeInstantRunGradleTaskRunner taskRunner = new BlazeInstantRunGradleTaskRunner(project, context, instantRunGradleBuildFile);
      boolean success = instantRunBuilder.build(taskRunner, cmdLineArgs);
      LOG.info("Gradle invocation complete, success = " + success);
      if (!success) {
        return false;
      }
    }
    catch (InvocationTargetException e) {
      LOG.info("Unexpected error while launching gradle before run tasks", e);
      return false;
    }
    catch (InterruptedException e) {
      LOG.info("Interrupted while launching gradle before run tasks");
      Thread.currentThread().interrupt();
      return false;
    }

    InstantRunBuildAnalyzer analyzer = new InstantRunBuildAnalyzer(
      project,
      instantRunContext,
      previousSessionProcessHandler
    );
    instantRunBuildAnalyzerFuture.set(analyzer);
    return true;
  }

  ListenableFuture<BuildResult> getBuildResult() {
    return buildResultFuture;
  }

  ListenableFuture<ApplicationIdProvider> getApplicationIdProvider() {
    return applicationIdProviderFuture;
  }

  ListenableFuture<InstantRunContext> getInstantRunContext() {
    return instantRunContextFuture;
  }

  ListenableFuture<InstantRunBuildAnalyzer> getInstantRunBuildAnalyzer() {
    return instantRunBuildAnalyzerFuture;
  }

  private String getExecutionRoot(BlazeContext context, WorkspaceRoot workspaceRoot) {
    ListenableFuture<String> execRootFuture = BlazeInfo.getInstance().runBlazeInfo(
      context, Blaze.getBuildSystem(project),
      workspaceRoot,
      buildFlags,
      BlazeInfo.EXECUTION_ROOT_KEY
    );
    try {
      return execRootFuture.get();
    }
    catch (InterruptedException e) {
      context.setCancelled();
    }
    catch (ExecutionException e) {
      LOG.error(e);
      context.setHasError();
    }
    return null;
  }

  @Nullable
  private static IDevice getLaunchedDevice(@NotNull AndroidDevice device) {
    if (!device.getLaunchedDevice().isDone()) {
      // If we don't have access to the device (this happens if the AVD is still launching)
      return null;
    }

    try {
      return device.getLaunchedDevice().get(1, TimeUnit.MILLISECONDS);
    }
    catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return null;
    }
    catch (ExecutionException | TimeoutException e) {
      return null;
    }
  }
}
