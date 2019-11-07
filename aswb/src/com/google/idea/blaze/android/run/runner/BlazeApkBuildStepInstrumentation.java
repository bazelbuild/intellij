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
package com.google.idea.blaze.android.run.runner;

import com.android.tools.idea.run.ApkProvisionException;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.android.run.deployinfo.BlazeAndroidDeployInfo;
import com.google.idea.blaze.android.run.deployinfo.BlazeApkDeployInfoProtoHelper;
import com.google.idea.blaze.android.run.deployinfo.BlazeApkDeployInfoProtoHelper.GetDeployInfoException;
import com.google.idea.blaze.base.async.executor.ProgressiveTaskWithProgressIndicator;
import com.google.idea.blaze.base.async.process.ExternalTask;
import com.google.idea.blaze.base.async.process.LineProcessingOutputStream;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelperProvider;
import com.google.idea.blaze.base.console.BlazeConsoleLineProcessorProvider;
import com.google.idea.blaze.base.filecache.FileCaches;
import com.google.idea.blaze.base.ideinfo.AndroidIdeInfo;
import com.google.idea.blaze.base.ideinfo.AndroidInstrumentationInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.ScopedTask;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.util.SaveUtil;
import com.google.idea.blaze.java.AndroidBlazeRules.RuleTypes;
import com.intellij.execution.ExecutionException;
import com.intellij.openapi.project.Project;
import java.util.concurrent.CancellationException;

/** Builds the APKs required for an android instrumentation test. */
public class BlazeApkBuildStepInstrumentation implements BlazeApkBuildStep {

  /** Do not modify this suffix without confirming it's required by a build rule change. */
  private static final String DEPLOY_INFO_FILE_SUFFIX = ".deployinfo.pb";

  private final Project project;
  private final Label instrumentationTestLabel;
  private final ImmutableList<String> buildFlags;
  private final BlazeApkDeployInfoProtoHelper deployInfoProtoHelper;
  private BlazeAndroidDeployInfo deployInfo = null;

  /**
   * Note: Target kind of {@param instrumentationTestlabel} must be "android_instrumentation_test".
   */
  public BlazeApkBuildStepInstrumentation(
      Project project, Label instrumentationTestLabel, ImmutableList<String> buildFlags) {
    this(
        project,
        instrumentationTestLabel,
        buildFlags,
        new BlazeApkDeployInfoProtoHelper(
            project, buildFlags, fileName -> fileName.endsWith(DEPLOY_INFO_FILE_SUFFIX)));
  }

  @VisibleForTesting
  public BlazeApkBuildStepInstrumentation(
      Project project,
      Label instrumentationTestLabel,
      ImmutableList<String> buildFlags,
      BlazeApkDeployInfoProtoHelper deployInfoProtoHelper) {
    this.project = project;
    this.instrumentationTestLabel = instrumentationTestLabel;
    this.buildFlags = buildFlags;
    this.deployInfoProtoHelper = deployInfoProtoHelper;
  }

  @Override
  public boolean build(
      BlazeContext context, BlazeAndroidDeviceSelector.DeviceSession deviceSession) {
    ScopedTask<Void> buildTask =
        new ScopedTask<Void>(context) {
          @Override
          protected Void execute(BlazeContext context) {
            InstrumentorToTarget instrumentationCompoments = getInstrumentorToTargetPair(context);
            BlazeCommand.Builder command =
                BlazeCommand.builder(
                    Blaze.getBuildSystemProvider(project).getBinaryPath(project),
                    BlazeCommandName.BUILD);
            WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProject(project);

            try (BuildResultHelper buildResultHelper = BuildResultHelperProvider.create(project)) {
              command
                  .addTargets(
                      instrumentationCompoments.instrumentor, instrumentationCompoments.target)
                  .addBlazeFlags("--output_groups=android_deploy_info")
                  .addBlazeFlags(buildFlags)
                  .addBlazeFlags(buildResultHelper.getBuildFlags());

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
              FileCaches.refresh(project, context);

              if (retVal != 0) {
                context.setHasError();
                return null;
              }
              try {
                deployInfo =
                    deployInfoProtoHelper.readDeployInfoForInstrumentationTest(
                        context,
                        buildResultHelper,
                        instrumentationCompoments.instrumentor,
                        instrumentationCompoments.target);
              } catch (GetDeployInfoException e) {
                IssueOutput.error("Could not read apk deploy info from build: " + e.getMessage())
                    .submit(context);
              }
            }
            return null;
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
    throw new ApkProvisionException(
        "Failed to read APK deploy info.  Either build step hasn't been executed or there was an"
            + " error obtaining deploy info after build.");
  }

  /**
   * Extracts test instrumentor and instrumentation target labels from the target map.
   *
   * @return The labels contained in an {@link InstrumentorToTarget} object.
   */
  @VisibleForTesting
  public InstrumentorToTarget getInstrumentorToTargetPair(BlazeContext context) {
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (projectData == null) {
      IssueOutput.error("Invalid project data. Please sync the project.").submit(context);
      return null;
    }

    // The following extracts the dependency info required during an instrumentation test.
    // To disambiguate, the following terms are used:
    // - test: The android_instrumentation_test target.
    // - instrumentor: The target of kind android_binary that's used as the binary that
    // orchestrates the instrumentation test.
    // - target: The target of kind android_binary that's being tested in this
    // instrumentation test through the instrumentor.
    TargetMap targetMap = projectData.getTargetMap();
    TargetIdeInfo testTarget = targetMap.get(TargetKey.forPlainTarget(instrumentationTestLabel));
    if (testTarget == null
        || testTarget.getKind() != RuleTypes.ANDROID_INSTRUMENTATION_TEST.getKind()) {
      IssueOutput.error("Invalid target map. Please sync the project.").submit(context);
      return null;
    }
    AndroidInstrumentationInfo testInstrumentationInfo = testTarget.getAndroidInstrumentationInfo();
    if (testInstrumentationInfo == null) {
      IssueOutput.error("Test instrumentor data is missing. Please sync the project.")
          .submit(context);
      return null;
    }

    Label instrumentorLabel = testInstrumentationInfo.getTestApp();
    if (instrumentorLabel == null) {
      IssueOutput.error(
              "No instrumentator target defined in "
                  + testTarget.getKey().getLabel()
                  + ". Please ensure a instrumentator target is defined.  See"
                  + " https://docs.bazel.build/versions/master/be/android.html#android_instrumentation_test.test_app"
                  + " for more information.")
          .submit(context);
      return null;
    }

    TargetIdeInfo instrumentorTarget = targetMap.get(TargetKey.forPlainTarget(instrumentorLabel));
    if (instrumentorTarget == null) {
      IssueOutput.error("Cannot find instrumentation target. Please sync the project.")
          .submit(context);
      return null;
    }
    AndroidIdeInfo instrumentorAndroidInfo = instrumentorTarget.getAndroidIdeInfo();
    if (instrumentorAndroidInfo == null) {
      IssueOutput.error("Required data about the test target is missing. Please sync the project.")
          .submit(context);
      return null;
    }
    Label targetLabel = instrumentorAndroidInfo.getInstruments();
    if (targetLabel == null) {
      IssueOutput.error(
              "No instrumentation target defined in "
                  + instrumentorLabel
                  + ". Please ensure a instrumentation target is defined.  See"
                  + " https://docs.bazel.build/versions/master/be/android.html#android_binary.instruments"
                  + " for more information.")
          .submit(context);
      return null;
    }

    return new InstrumentorToTarget(targetLabel, instrumentorLabel);
  }

  /** A container for a instrumentation test instrumentor and the target app it instruments. */
  @VisibleForTesting
  public static class InstrumentorToTarget {
    public final Label target;
    public final Label instrumentor;

    InstrumentorToTarget(Label target, Label instrumentor) {
      this.target = target;
      this.instrumentor = instrumentor;
    }
  }
}
