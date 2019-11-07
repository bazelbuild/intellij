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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.build.lib.rules.android.deployinfo.AndroidDeployInfoOuterClass.AndroidDeployInfo;
import com.google.devtools.build.lib.rules.android.deployinfo.AndroidDeployInfoOuterClass.Artifact;
import com.google.idea.blaze.android.manifest.ManifestParser.ParsedManifest;
import com.google.idea.blaze.android.manifest.ParsedManifestService;
import com.google.idea.blaze.base.command.buildresult.BlazeArtifact;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper.GetArtifactsException;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.command.info.BlazeInfoRunner;
import com.google.idea.blaze.base.model.primitives.Label;
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
import java.util.stream.Stream;
import javax.annotation.Nullable;

/** Reads the deploy info from a build step. */
public class BlazeApkDeployInfoProtoHelper {
  private static final Logger LOG = Logger.getInstance(BlazeApkDeployInfoProtoHelper.class);

  private final Project project;
  private final WorkspaceRoot workspaceRoot;
  private final ImmutableList<String> buildFlags;
  private final AndroidDeployInfoReader androidDeployInfoReader;

  /** Indicates a failure when extracting deploy info. */
  public static class GetDeployInfoException extends Exception {
    GetDeployInfoException(String message) {
      super(message);
    }
  }

  /** Gets deploy info file from the local file system. This class is exposed for easier testing. */
  @VisibleForTesting
  static class AndroidDeployInfoReader {
    private final Predicate<String> pathFilter;

    AndroidDeployInfoReader(Predicate<String> pathFilter) {
      this.pathFilter = pathFilter;
    }

    AndroidDeployInfo getDeployInfo(BuildResultHelper buildResultHelper, Label filterTarget)
        throws GetDeployInfoException {
      ImmutableList<File> deployInfoFiles;
      try {
        deployInfoFiles =
            BlazeArtifact.getLocalFiles(
                buildResultHelper.getBuildArtifactsForTarget(filterTarget, pathFilter));
      } catch (GetArtifactsException e) {
        throw new GetDeployInfoException(e.getMessage());
      }

      if (deployInfoFiles.isEmpty()) {
        throw new GetDeployInfoException(
            "No deploy info files found. Were build flags for BuildResultHelper set up correctly?");
      }

      if (deployInfoFiles.size() > 1) {
        throw new GetDeployInfoException(
            "More than one deploy info file found for target "
                + filterTarget
                + ". This may be due to a stale project state. Please sync the project and try"
                + " again.");
      }

      try (InputStream inputStream = new FileInputStream(deployInfoFiles.get(0))) {
        return AndroidDeployInfo.parseFrom(inputStream);
      } catch (IOException e) {
        throw new GetDeployInfoException(e.getMessage());
      }
    }
  }

  public BlazeApkDeployInfoProtoHelper(
      Project project, ImmutableList<String> buildFlags, Predicate<String> pathFilter) {
    this(project, buildFlags, new AndroidDeployInfoReader(pathFilter));
  }

  @VisibleForTesting
  BlazeApkDeployInfoProtoHelper(
      Project project,
      ImmutableList<String> buildFlags,
      AndroidDeployInfoReader androidDeployInfoReader) {
    this.project = project;
    this.buildFlags = buildFlags;
    this.workspaceRoot = WorkspaceRoot.fromProject(project);
    this.androidDeployInfoReader = androidDeployInfoReader;
  }

  public BlazeAndroidDeployInfo readDeployInfoForNormalBuild(
      BlazeContext context, BuildResultHelper buildResultHelper, Label target)
      throws GetDeployInfoException {
    String executionRoot = getExecutionRoot(context);

    // Assume there's only one deploy info file during normal build.
    AndroidDeployInfo deployInfo = androidDeployInfoReader.getDeployInfo(buildResultHelper, target);

    File mergedManifestFile =
        new File(executionRoot, deployInfo.getMergedManifest().getExecRootPath());
    ParsedManifest mergedManifest = getParsedManifest(mergedManifestFile);
    ParsedManifestService.getInstance(project).invalidateCachedManifest(mergedManifestFile);

    // android_test targets uses additional merged manifests field of the deploy info proto to hold
    // the manifest of the test target APK.
    ParsedManifest testTargetMergedManifest = null;
    List<Artifact> additionalManifests = deployInfo.getAdditionalMergedManifestsList();
    if (additionalManifests.size() == 1) {
      File testTargetMergedManifestFile =
          new File(executionRoot, additionalManifests.get(0).getExecRootPath());
      testTargetMergedManifest = getParsedManifest(testTargetMergedManifestFile);
      ParsedManifestService.getInstance(project)
          .invalidateCachedManifest(testTargetMergedManifestFile);
    }

    List<File> apksToDeploy =
        deployInfo.getApksToDeployList().stream()
            .map(artifact -> new File(executionRoot, artifact.getExecRootPath()))
            .collect(Collectors.toList());

    return new BlazeAndroidDeployInfo(mergedManifest, testTargetMergedManifest, apksToDeploy);
  }

  @Nullable
  public BlazeAndroidDeployInfo readDeployInfoForInstrumentationTest(
      BlazeContext context, BuildResultHelper buildResultHelper, Label testLabel, Label targetLabel)
      throws GetDeployInfoException {
    String executionRoot = getExecutionRoot(context);
    AndroidDeployInfo testDeployInfo =
        androidDeployInfoReader.getDeployInfo(buildResultHelper, testLabel);
    AndroidDeployInfo targetDeployInfo =
        androidDeployInfoReader.getDeployInfo(buildResultHelper, targetLabel);

    File testMergedManifestFile =
        new File(executionRoot, testDeployInfo.getMergedManifest().getExecRootPath());
    ParsedManifest testMergedManifest = getParsedManifest(testMergedManifestFile);
    ParsedManifestService.getInstance(project).invalidateCachedManifest(testMergedManifestFile);

    File testTargetMergedManifestFile =
        new File(executionRoot, targetDeployInfo.getMergedManifest().getExecRootPath());
    ParsedManifest testTargetMergedManifest = getParsedManifest(testTargetMergedManifestFile);
    ParsedManifestService.getInstance(project)
        .invalidateCachedManifest(testTargetMergedManifestFile);

    List<File> apksToDeploy =
        Stream.concat(
                testDeployInfo.getApksToDeployList().stream(),
                targetDeployInfo.getApksToDeployList().stream())
            .map(artifact -> new File(executionRoot, artifact.getExecRootPath()))
            .collect(Collectors.toList());

    return new BlazeAndroidDeployInfo(testMergedManifest, testTargetMergedManifest, apksToDeploy);
  }

  private String getExecutionRoot(BlazeContext context) throws GetDeployInfoException {
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
    throw new GetDeployInfoException("Could not determine project execution root.");
  }

  /** Transforms thrown {@link IOException} to {@link GetArtifactsException} */
  private ParsedManifest getParsedManifest(File manifestFile) throws GetDeployInfoException {
    try {
      return ParsedManifestService.getInstance(project).getParsedManifest(manifestFile);
    } catch (IOException e) {
      throw new GetDeployInfoException(
          "Could not read merged manifest file "
              + manifestFile
              + " due to error: "
              + e.getMessage());
    }
  }
}
