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
package com.google.idea.blaze.android.run.runner;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.idea.blaze.android.run.BlazeAndroidRunConfigurationCommonState;
import com.google.idea.blaze.android.run.deployinfo.BlazeAndroidDeployInfo;
import com.google.idea.blaze.android.run.deployinfo.BlazeApkDeployInfoProtoHelper;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.google.idea.blaze.base.async.process.ExternalTask;
import com.google.idea.blaze.base.async.process.LineProcessingOutputStream;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.issueparser.IssueOutputLineProcessor;
import com.google.idea.blaze.base.metrics.Action;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.ScopedTask;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.scopes.LoggedTimingScope;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.util.SaveUtil;
import com.intellij.execution.ExecutionException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CancellationException;

/**
 * Builds the APK using normal blaze build.
 */
public class BlazeApkBuildStepNormalBuild implements BlazeApkBuildStep {
  private final Project project;
  private final BlazeAndroidRunConfigurationCommonState commonState;
  private final ImmutableList<String> buildFlags;
  private final SettableFuture<BlazeAndroidDeployInfo> deployInfoFuture = SettableFuture.create();

  public BlazeApkBuildStepNormalBuild(Project project,
                                      BlazeAndroidRunConfigurationCommonState commonState,
                                      ImmutableList<String> buildFlags) {
    this.project = project;
    this.commonState = commonState;
    this.buildFlags = buildFlags;
  }

  @Override
  public boolean build(BlazeContext context, BlazeAndroidDeviceSelector.DeviceSession deviceSession) {
    final ScopedTask buildTask = new ScopedTask(context) {
      @Override
      protected void execute(@NotNull BlazeContext context) {
        BlazeCommand.Builder command = BlazeCommand.builder(Blaze.getBuildSystem(project), BlazeCommandName.BUILD);
        WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProject(project);

        command
          .addTargets(commonState.getTarget())
          .addBlazeFlags("--output_groups=+android_deploy_info")
          .addBlazeFlags(buildFlags)
          .addBlazeFlags(BlazeFlags.EXPERIMENTAL_SHOW_ARTIFACTS)
        ;

        BlazeApkDeployInfoProtoHelper deployInfoHelper = new BlazeApkDeployInfoProtoHelper(project, buildFlags);

        SaveUtil.saveAllFiles();
        int retVal = ExternalTask.builder(workspaceRoot, command.build())
          .context(context)
          .stderr(LineProcessingOutputStream.of(
            deployInfoHelper.getLineProcessor(),
            new IssueOutputLineProcessor(project, context, workspaceRoot)
          ))
          .build()
          .run(new LoggedTimingScope(project, Action.BLAZE_BUILD));
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
}
