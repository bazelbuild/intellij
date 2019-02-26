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
package com.google.idea.blaze.android.run.runner;

import com.android.tools.idea.run.ApkProvisionException;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.android.run.deployinfo.BlazeAndroidDeployInfo;
import com.google.idea.blaze.android.run.deployinfo.BlazeApkDeployInfoProtoHelper;
import com.google.idea.blaze.base.async.executor.ProgressiveTaskWithProgressIndicator;
import com.google.idea.blaze.base.async.process.ExternalTask;
import com.google.idea.blaze.base.async.process.LineProcessingOutputStream;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper.GetArtifactsException;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelperProvider;
import com.google.idea.blaze.base.console.BlazeConsoleLineProcessorProvider;
import com.google.idea.blaze.base.filecache.FileCaches;
import com.google.idea.blaze.base.ideinfo.Dependency;
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
import com.google.idea.blaze.java.AndroidBlazeRules;
import com.intellij.execution.ExecutionException;
import com.intellij.openapi.project.Project;
import java.util.concurrent.CancellationException;

/** Builds the APK using normal blaze build. */
public class BlazeApkBuildStepNormalBuild implements BlazeApkBuildStep {
  private final Project project;
  private final Label label;
  private final ImmutableList<String> buildFlags;
  private BlazeAndroidDeployInfo deployInfo = null;

  public BlazeApkBuildStepNormalBuild(
      Project project, Label label, ImmutableList<String> buildFlags) {
    this.project = project;
    this.label = label;
    this.buildFlags = buildFlags;
  }

  /**
   * In case we're dealing with an {@link AndroidBlazeRules.RuleTypes#ANDROID_INSTRUMENTATION_TEST},
   * build the underlying {@link AndroidBlazeRules.RuleTypes#ANDROID_BINARY} instead.
   */
  private Label getTargetToBuild() {
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (projectData == null) {
      return label;
    }
    TargetMap targetMap = projectData.getTargetMap();
    TargetIdeInfo target = targetMap.get(TargetKey.forPlainTarget(label));
    if (target == null
        || target.getKind() != AndroidBlazeRules.RuleTypes.ANDROID_INSTRUMENTATION_TEST.getKind()) {
      return label;
    }
    for (Dependency dependency : target.getDependencies()) {
      TargetIdeInfo dependencyInfo = targetMap.get(dependency.getTargetKey());
      // Should exist via test_app attribute, and be unique.
      if (dependencyInfo != null
          && dependencyInfo.getKind() == AndroidBlazeRules.RuleTypes.ANDROID_BINARY.getKind()) {
        return dependency.getTargetKey().getLabel();
      }
    }
    return label;
  }

  @Override
  public boolean build(
      BlazeContext context, BlazeAndroidDeviceSelector.DeviceSession deviceSession) {
    ScopedTask<Void> buildTask =
        new ScopedTask<Void>(context) {
          @Override
          protected Void execute(BlazeContext context) {
            BlazeCommand.Builder command =
                BlazeCommand.builder(
                    Blaze.getBuildSystemProvider(project).getBinaryPath(project),
                    BlazeCommandName.BUILD);
            WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProject(project);

            BlazeApkDeployInfoProtoHelper deployInfoHelper =
                new BlazeApkDeployInfoProtoHelper(project, buildFlags);
            try (BuildResultHelper buildResultHelper =
                BuildResultHelperProvider.forFiles(
                    project, fileName -> fileName.endsWith(".deployinfo.pb"))) {

              command
                  .addTargets(getTargetToBuild())
                  .addBlazeFlags("--output_groups=+android_deploy_info")
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
}
