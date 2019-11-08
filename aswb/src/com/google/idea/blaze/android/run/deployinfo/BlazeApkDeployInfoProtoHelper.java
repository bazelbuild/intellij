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
package com.google.idea.blaze.android.run.deployinfo;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.build.lib.rules.android.deployinfo.AndroidDeployInfoOuterClass;
import com.google.devtools.build.lib.rules.android.deployinfo.AndroidDeployInfoOuterClass.Artifact;
import com.google.idea.blaze.android.manifest.ManifestParser.ParsedManifest;
import com.google.idea.blaze.android.manifest.ParsedManifestService;
import com.google.idea.blaze.base.command.buildresult.BlazeArtifact;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper.GetArtifactsException;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.command.info.BlazeInfoRunner;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** Reads the deploy info from a build step. */
public class BlazeApkDeployInfoProtoHelper {
  private static final Logger LOG = Logger.getInstance(BlazeApkDeployInfoProtoHelper.class);

  private final Project project;
  private final WorkspaceRoot workspaceRoot;
  private final ImmutableList<String> buildFlags;

  public BlazeApkDeployInfoProtoHelper(Project project, ImmutableList<String> buildFlags) {
    this.project = project;
    this.buildFlags = buildFlags;
    this.workspaceRoot = WorkspaceRoot.fromProject(project);
  }

  @Nullable
  public BlazeAndroidDeployInfo readDeployInfo(
      BlazeContext context, BuildResultHelper buildResultHelper, Predicate<String> pathFilter)
      throws GetArtifactsException {
    File deployInfoFile =
        Iterables.getOnlyElement(
            BlazeArtifact.getLocalFiles(buildResultHelper.getAllOutputArtifacts(pathFilter)), null);
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

    File mergedManifestFile =
        new File(executionRoot, deployInfo.getMergedManifest().getExecRootPath());
    ParsedManifest mergedManifest = getParsedManifestSafe(mergedManifestFile);
    ParsedManifestService.getInstance(project)
        .invalidateCachedManifests(ImmutableList.of(mergedManifestFile));

    // android_test targets uses additional merged manifests field of the deploy info proto to hold
    // the manifest of the test target APK.
    ParsedManifest testTargetMergedManifest = null;
    List<Artifact> additionalManifests = deployInfo.getAdditionalMergedManifestsList();
    if (additionalManifests.size() == 1) {
      File testTargetMergedManifestFile =
          new File(executionRoot, additionalManifests.get(0).getExecRootPath());
      testTargetMergedManifest = getParsedManifestSafe(testTargetMergedManifestFile);
      ParsedManifestService.getInstance(project)
          .invalidateCachedManifests(ImmutableList.of(testTargetMergedManifestFile));
    }

    List<File> apksToDeploy =
        deployInfo.getApksToDeployList().stream()
            .map(artifact -> new File(executionRoot, artifact.getExecRootPath()))
            .collect(Collectors.toList());

    return new BlazeAndroidDeployInfo(mergedManifest, testTargetMergedManifest, apksToDeploy);
  }

  /** Transforms thrown {@link IOException} to {@link GetArtifactsException} */
  private ParsedManifest getParsedManifestSafe(File manifestFile) throws GetArtifactsException {
    try {
      return ParsedManifestService.getInstance(project).getParsedManifest(manifestFile);
    } catch (IOException e) {
      throw new GetArtifactsException(
          "Could not read merged manifest file "
              + manifestFile
              + " due to error: "
              + e.getMessage());
    }
  }

  @Nullable
  private String getExecutionRoot(BlazeContext context) {
    ListenableFuture<String> execRootFuture =
        BlazeInfoRunner.getInstance()
            .runBlazeInfo(
                context,
                Blaze.getBuildSystemProvider(project).getBinaryPath(project),
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
