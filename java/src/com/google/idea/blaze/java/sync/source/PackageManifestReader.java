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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.idea.blaze.base.async.FutureUtil;
import com.google.idea.blaze.base.filecache.FileDiffer;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.io.InputStreamProvider;
import com.google.idea.blaze.base.prefetch.PrefetchService;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.repackaged.devtools.build.lib.ideinfo.androidstudio.PackageManifestOuterClass.JavaSourcePackage;
import com.google.repackaged.devtools.build.lib.ideinfo.androidstudio.PackageManifestOuterClass.PackageManifest;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/** Reads package manifests. */
public class PackageManifestReader {
  private static final Logger LOG = Logger.getInstance(SourceDirectoryCalculator.class);

  public static PackageManifestReader getInstance() {
    return ServiceManager.getService(PackageManifestReader.class);
  }

  private ImmutableMap<File, Long> fileDiffState;

  private Map<File, TargetKey> fileToLabelMap = Maps.newHashMap();
  private final Map<TargetKey, Map<ArtifactLocation, String>> manifestMap = Maps.newConcurrentMap();

  /** @return A map from java source absolute file path to declared package string. */
  public Map<TargetKey, Map<ArtifactLocation, String>> readPackageManifestFiles(
      Project project,
      BlazeContext context,
      ArtifactLocationDecoder decoder,
      Map<TargetKey, ArtifactLocation> javaPackageManifests,
      ListeningExecutorService executorService) {

    Map<File, TargetKey> fileToLabelMap = Maps.newHashMap();
    for (Map.Entry<TargetKey, ArtifactLocation> entry : javaPackageManifests.entrySet()) {
      TargetKey key = entry.getKey();
      File file = decoder.decode(entry.getValue());
      fileToLabelMap.put(file, key);
    }
    List<File> updatedFiles = Lists.newArrayList();
    List<File> removedFiles = Lists.newArrayList();
    fileDiffState =
        FileDiffer.updateFiles(fileDiffState, fileToLabelMap.keySet(), updatedFiles, removedFiles);

    ListenableFuture<?> fetchFuture =
        PrefetchService.getInstance().prefetchFiles(project, updatedFiles);
    if (!FutureUtil.waitForFuture(context, fetchFuture)
        .timed("FetchPackageManifests")
        .withProgressMessage("Reading package manifests...")
        .run()
        .success()) {
      return null;
    }

    List<ListenableFuture<Void>> futures = Lists.newArrayList();
    for (File file : updatedFiles) {
      futures.add(
          executorService.submit(
              () -> {
                Map<ArtifactLocation, String> manifest = parseManifestFile(file);
                manifestMap.put(fileToLabelMap.get(file), manifest);
                return null;
              }));
    }
    for (File file : removedFiles) {
      TargetKey key = this.fileToLabelMap.get(file);
      if (key != null) {
        manifestMap.remove(key);
      }
    }
    this.fileToLabelMap = fileToLabelMap;

    try {
      Futures.allAsList(futures).get();
    } catch (ExecutionException | InterruptedException e) {
      LOG.error(e);
      throw new IllegalStateException("Could not read sources");
    }
    return manifestMap;
  }

  protected Map<ArtifactLocation, String> parseManifestFile(File packageManifest) {
    Map<ArtifactLocation, String> outputMap = Maps.newHashMap();
    InputStreamProvider inputStreamProvider = InputStreamProvider.getInstance();

    try (InputStream input = inputStreamProvider.getFile(packageManifest)) {
      try (BufferedInputStream bufferedInputStream = new BufferedInputStream(input)) {
        PackageManifest proto = PackageManifest.parseFrom(bufferedInputStream);
        for (JavaSourcePackage source : proto.getSourcesList()) {
          ArtifactLocation artifactLocation =
              ArtifactLocation.builder()
                  .setRootExecutionPathFragment(
                      source.getArtifactLocation().getRootExecutionPathFragment())
                  .setRelativePath(source.getArtifactLocation().getRelativePath())
                  .setIsSource(source.getArtifactLocation().getIsSource())
                  .build();
          outputMap.put(artifactLocation, source.getPackageString());
        }
      }
      return outputMap;
    } catch (IOException e) {
      LOG.error(e);
      return outputMap;
    }
  }
}
