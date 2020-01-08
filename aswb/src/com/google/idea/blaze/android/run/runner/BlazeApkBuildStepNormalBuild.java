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
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.rules.android.deployinfo.AndroidDeployInfoOuterClass.AndroidDeployInfo;
import com.google.idea.blaze.android.run.deployinfo.BlazeAndroidDeployInfo;
import com.google.idea.blaze.android.run.deployinfo.BlazeApkDeployInfoProtoHelper;
import com.google.idea.blaze.android.run.deployinfo.BlazeApkDeployInfoProtoHelper.GetDeployInfoException;
import com.google.idea.blaze.base.async.process.ExternalTask;
import com.google.idea.blaze.base.async.process.LineProcessingOutputStream;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
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
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.util.SaveUtil;
import com.intellij.openapi.project.Project;
import java.io.File;

/** Builds the APK using normal blaze build. */
public class BlazeApkBuildStepNormalBuild implements BlazeApkBuildStep {
  @VisibleForTesting public static final String DEPLOY_INFO_SUFFIX = ".deployinfo.pb";

  private final Project project;
  private final Label label;
  private final ImmutableList<String> buildFlags;
  private final BlazeApkDeployInfoProtoHelper deployInfoHelper;
  private BlazeAndroidDeployInfo deployInfo = null;

  @VisibleForTesting
  public BlazeApkBuildStepNormalBuild(
      Project project,
      Label label,
      ImmutableList<String> buildFlags,
      BlazeApkDeployInfoProtoHelper deployInfoHelper) {
    this.project = project;
    this.label = label;
    this.buildFlags = buildFlags;
    this.deployInfoHelper = deployInfoHelper;
  }

  public BlazeApkBuildStepNormalBuild(
      Project project, Label label, ImmutableList<String> buildFlags) {
    this(project, label, buildFlags, new BlazeApkDeployInfoProtoHelper());
  }

  @Override
  public void build(BlazeContext context, BlazeAndroidDeviceSelector.DeviceSession deviceSession) {
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();

    if (projectData == null) {
      IssueOutput.error("Missing project data. Please sync and try again.").submit(context);
      return;
    }

    BlazeCommand.Builder command =
        BlazeCommand.builder(
            Blaze.getBuildSystemProvider(project).getBinaryPath(project), BlazeCommandName.BUILD);
    WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProject(project);

    try (BuildResultHelper buildResultHelper = BuildResultHelperProvider.create(project)) {
      command
          .addTargets(label)
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
                      BlazeConsoleLineProcessorProvider.getAllStderrLineProcessors(context)))
              .build()
              .run();
      FileCaches.refresh(project, context);

      if (retVal != 0) {
        IssueOutput.error("Blaze build failed. See Blaze Console for details.").submit(context);
        return;
      }

      context.output(new StatusOutput("Reading deployment information..."));
      String executionRoot =
          ExecRootUtil.getExecutionRoot(buildResultHelper, project, buildFlags, context);
      if (executionRoot == null) {
        IssueOutput.error("Could not locate execroot!").submit(context);
        return;
      }

      AndroidDeployInfo deployInfoProto =
          deployInfoHelper.readDeployInfoProtoForTarget(
              label, buildResultHelper, fileName -> fileName.endsWith(DEPLOY_INFO_SUFFIX));
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
}
