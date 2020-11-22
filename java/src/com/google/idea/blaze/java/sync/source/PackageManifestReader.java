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
package com.google.idea.blaze.java.sync.source;

import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.intellij.aspect.Common;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.JavaSourcePackage;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.PackageManifest;
import com.google.idea.blaze.base.async.FutureUtil;
import com.google.idea.blaze.base.command.buildresult.BlazeArtifact;
import com.google.idea.blaze.base.command.buildresult.OutputArtifact;
import com.google.idea.blaze.base.command.buildresult.RemoteOutputArtifact;
import com.google.idea.blaze.base.filecache.ArtifactState;
import com.google.idea.blaze.base.filecache.ArtifactsDiff;
import com.google.idea.blaze.base.filecache.RemoteOutputsCache;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.io.InputStreamProvider;
import com.google.idea.blaze.base.prefetch.PrefetchService;
import com.google.idea.blaze.base.prefetch.RemoteArtifactPrefetcher;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.scopes.TimingScope.EventType;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;

/** Reads package manifests. */
public class PackageManifestReader {
  private static final Logger logger = Logger.getInstance(PackageManifestReader.class);

  public static PackageManifestReader getInstance() {
    return ServiceManager.getService(PackageManifestReader.class);
  }

  private ImmutableMap<String, ArtifactState> artifactState;
  private Map<ArtifactState, TargetKey> fileToLabelMap = new HashMap<>();
  private final Map<TargetKey, Map<ArtifactLocation, String>> manifestMap = Maps.newConcurrentMap();

  /** @return A map from java source absolute file path to declared package string. */
  public Map<TargetKey, Map<ArtifactLocation, String>> readPackageManifestFiles(
      Project project,
      BlazeContext context,
      ArtifactLocationDecoder decoder,
      Map<TargetKey, ArtifactLocation> javaPackageManifests,
      ListeningExecutorService executorService) {

    Map<OutputArtifact, TargetKey> fileToLabelMap = Maps.newHashMap();
    for (Map.Entry<TargetKey, ArtifactLocation> entry : javaPackageManifests.entrySet()) {
      TargetKey key = entry.getKey();
      BlazeArtifact artifact = decoder.resolveOutput(entry.getValue());
      if (artifact instanceof OutputArtifact) {
        fileToLabelMap.put((OutputArtifact) artifact, key);
      }
    }
    ArtifactsDiff diff;
    try {
      diff = ArtifactsDiff.diffArtifacts(artifactState, fileToLabelMap.keySet());
      artifactState = diff.getNewState();
    } catch (InterruptedException e) {
      throw new ProcessCanceledException(e);
    } catch (ExecutionException e) {
      context.setHasError();
      IssueOutput.error("Updating package manifest files failed: " + e).submit(context);
      throw new AssertionError("Unhandled exception", e);
    }

    // Find all not cached {@link RemoteOutputArtifact} and download them before parsing manifest
    // file
    ImmutableList.Builder<RemoteOutputArtifact> toDownload = ImmutableList.builder();
    for (OutputArtifact outputArtifact : diff.getUpdatedOutputs()) {
      if (!(outputArtifact instanceof RemoteOutputArtifact)) {
        continue;
      }
      if (findArtifactInCache(project, outputArtifact) != null) {
        continue;
      }
      toDownload.add((RemoteOutputArtifact) outputArtifact);
    }

    ListenableFuture<?> fetchRemoteArtifactFuture =
        RemoteArtifactPrefetcher.getInstance()
            .downloadArtifacts(project.getName(), toDownload.build());
    ListenableFuture<?> fetchFuture =
        PrefetchService.getInstance()
            .prefetchFiles(BlazeArtifact.getLocalFiles(diff.getUpdatedOutputs()), true, false);

    if (!FutureUtil.waitForFuture(
            context, Futures.allAsList(fetchRemoteArtifactFuture, fetchFuture))
        .timed("FetchPackageManifests", EventType.Prefetching)
        .withProgressMessage("Reading package manifests...")
        .run()
        .success()) {
      return null;
    }

    List<ListenableFuture<Void>> futures = Lists.newArrayList();
    for (OutputArtifact file : diff.getUpdatedOutputs()) {
      futures.add(
          executorService.submit(
              () -> {
                Map<ArtifactLocation, String> manifest = parseManifestFile(project, file);
                manifestMap.put(fileToLabelMap.get(file), manifest);
                return null;
              }));
    }
    for (ArtifactState file : diff.getRemovedOutputs()) {
      TargetKey key = this.fileToLabelMap.get(file);
      if (key != null) {
        manifestMap.remove(key);
      }
    }
    this.fileToLabelMap =
        fileToLabelMap.entrySet().stream()
            .filter(e -> diff.getNewState().containsKey(e.getKey().getKey()))
            .collect(toImmutableMap(e -> e.getKey().toArtifactState(), Map.Entry::getValue));

    try {
      Futures.allAsList(futures).get();
    } catch (ExecutionException | InterruptedException e) {
      logger.error(e);
      throw new IllegalStateException("Could not read sources");
    }
    return manifestMap;
  }

  @Nullable
  private static File findArtifactInCache(Project project, OutputArtifact outputArtifact) {
    if (outputArtifact instanceof RemoteOutputArtifact) {
      return RemoteOutputsCache.getInstance(project)
          .resolveOutput((RemoteOutputArtifact) outputArtifact);
    }
    return null;
  }

  private static Map<ArtifactLocation, String> parseManifestFile(
      Project project, OutputArtifact packageManifest) {
    Map<ArtifactLocation, String> outputMap = Maps.newHashMap();
    InputStreamProvider inputStreamProvider = InputStreamProvider.getInstance();

    // Read file from local cache if it's available
    File cachedFile = findArtifactInCache(project, packageManifest);
    try (InputStream input =
        (cachedFile == null)
            ? inputStreamProvider.forOutputArtifact(packageManifest)
            : new BufferedInputStream(new FileInputStream(cachedFile))) {
      PackageManifest proto = PackageManifest.parseFrom(input);
      for (JavaSourcePackage source : proto.getSourcesList()) {
        outputMap.put(fromProto(source.getArtifactLocation()), source.getPackageString());
      }
      return outputMap;
    } catch (IOException e) {
      logger.error(e);
      return outputMap;
    }
  }

  private static ArtifactLocation fromProto(Common.ArtifactLocation location) {
    String relativePath = location.getRelativePath();
    String rootExecutionPathFragment = location.getRootExecutionPathFragment();
    if (!location.getIsNewExternalVersion() && location.getIsExternal()) {
      // fix up incorrect paths created with older aspect version
      // Note: bazel always uses the '/' separator here, even on windows.
      List<String> components = StringUtil.split(relativePath, "/");
      if (components.size() > 2) {
        relativePath = Joiner.on('/').join(components.subList(2, components.size()));
        String prefix = components.get(0) + "/" + components.get(1);
        rootExecutionPathFragment =
            rootExecutionPathFragment.isEmpty() ? prefix : rootExecutionPathFragment + "/" + prefix;
      }
    }
    return ArtifactLocation.builder()
        .setRootExecutionPathFragment(rootExecutionPathFragment)
        .setRelativePath(relativePath)
        .setIsSource(location.getIsSource())
        .setIsExternal(location.getIsExternal())
        .build();
  }
}
