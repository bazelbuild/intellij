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
package com.google.idea.blaze.android.run.deployinfo;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.android.manifest.ManifestParser;
import com.google.idea.blaze.base.async.process.LineProcessingOutputStream;
import com.google.idea.blaze.base.command.ExperimentalShowArtifactsLineProcessor;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.repackaged.devtools.build.lib.rules.android.deployinfo.AndroidDeployInfoOuterClass;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;

/** Reads the deploy info from a build step. */
public class BlazeApkDeployInfoProtoHelper {
  private static final Logger LOG = Logger.getInstance(BlazeApkDeployInfoProtoHelper.class);

  private final Project project;
  private final WorkspaceRoot workspaceRoot;
  private final ImmutableList<String> buildFlags;
  private final List<File> deployInfoFiles = Lists.newArrayList();
  private final LineProcessingOutputStream.LineProcessor lineProcessor =
      new ExperimentalShowArtifactsLineProcessor(deployInfoFiles, ".deployinfo.pb");

  public BlazeApkDeployInfoProtoHelper(Project project, ImmutableList<String> buildFlags) {
    this.project = project;
    this.buildFlags = buildFlags;
    this.workspaceRoot = WorkspaceRoot.fromProject(project);
  }

  public LineProcessingOutputStream.LineProcessor getLineProcessor() {
    return lineProcessor;
  }

  @Nullable
  public BlazeAndroidDeployInfo readDeployInfo(BlazeContext context) {
    File deployInfoFile = Iterables.getOnlyElement(deployInfoFiles, null);
    if (deployInfoFile == null) {
      return null;
    }
    AndroidDeployInfoOuterClass.AndroidDeployInfo deployInfo;
    try (InputStream inputStream = new FileInputStream(deployInfoFile)) {
      deployInfo = AndroidDeployInfoOuterClass.AndroidDeployInfo.parseFrom(inputStream);
    } catch (IOException e) {
      LOG.error(e);
      return null;
    }
    String executionRoot = getExecutionRoot(context);
    if (executionRoot == null) {
      return null;
    }
    BlazeAndroidDeployInfo androidDeployInfo =
        new BlazeAndroidDeployInfo(project, new File(executionRoot), deployInfo);

    List<File> manifestFiles = androidDeployInfo.getManifestFiles();
    ManifestParser.getInstance(project).refreshManifests(manifestFiles);

    return androidDeployInfo;
  }

  @Nullable
  private String getExecutionRoot(BlazeContext context) {
    ListenableFuture<String> execRootFuture =
        BlazeInfo.getInstance()
            .runBlazeInfo(
                context,
                Blaze.getBuildSystem(project),
                workspaceRoot,
                buildFlags,
                BlazeInfo.EXECUTION_ROOT_KEY);
    try {
      return execRootFuture.get();
    } catch (InterruptedException e) {
      context.setCancelled();
    } catch (ExecutionException e) {
      LOG.error(e);
      context.setHasError();
    }
    return null;
  }
}
