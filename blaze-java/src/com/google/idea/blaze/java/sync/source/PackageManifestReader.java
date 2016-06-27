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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.idea.blaze.base.async.FutureUtil;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.io.InputStreamProvider;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.prefetch.PrefetchService;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.filediff.FileDiffService;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.repackaged.devtools.build.lib.ideinfo.androidstudio.PackageManifestOuterClass.JavaSourcePackage;
import com.google.repackaged.devtools.build.lib.ideinfo.androidstudio.PackageManifestOuterClass.PackageManifest;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;

import javax.annotation.Nullable;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class PackageManifestReader {
  private static final Logger LOG = Logger.getInstance(SourceDirectoryCalculator.class);

  public static PackageManifestReader getInstance() {
    return ServiceManager.getService(PackageManifestReader.class);
  }

  private FileDiffService fileDiffService = new FileDiffService();
  private FileDiffService.State fileDiffState;

  private Map<File, Label> fileToLabelMap = Maps.newHashMap();
  private final Map<Label, Map<String, String>> manifestMap = Maps.newConcurrentMap();

  /**
   * @return A map from java source absolute file path to declared package string.
   */
  public Map<Label, Map<String, String>> readPackageManifestFiles(
    BlazeContext context,
    ArtifactLocationDecoder decoder,
    Map<Label, ArtifactLocation> javaPackageManifests,
    ListeningExecutorService executorService) {

    Map<File, Label> fileToLabelMap = Maps.newHashMap();
    for (Map.Entry<Label, ArtifactLocation> entry : javaPackageManifests.entrySet()) {
      Label label = entry.getKey();
      File file = entry.getValue().getFile();
      fileToLabelMap.put(file, label);
    }
    List<File> updatedFiles = Lists.newArrayList();
    List<File> removedFiles = Lists.newArrayList();
    fileDiffState = fileDiffService.updateFiles(fileDiffState, fileToLabelMap.keySet(), updatedFiles, removedFiles);

    ListenableFuture<?> fetchFuture = PrefetchService.getInstance().prefetchFiles(updatedFiles, true);
    if (!FutureUtil.waitForFuture(context, fetchFuture)
      .timed("FetchPackageManifests")
      .run()
      .success()) {
      return null;
    }

    List<ListenableFuture<Void>> futures = Lists.newArrayList();
    for (File file : updatedFiles) {
      futures.add(executorService.submit(() -> {
        Map<String, String> manifest = parseManifestFile(decoder, file);
        manifestMap.put(fileToLabelMap.get(file), manifest);
        return null;
      }));
    }
    for (File file : removedFiles) {
      Label label = this.fileToLabelMap.get(file);
      if (label != null) {
        manifestMap.remove(label);
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

  protected Map<String, String> parseManifestFile(ArtifactLocationDecoder decoder,
                                                  File packageManifest) {
    Map<String, String> outputMap = Maps.newHashMap();
    InputStreamProvider inputStreamProvider = InputStreamProvider.getInstance();

    try (InputStream input = inputStreamProvider.getFile(packageManifest)) {
      try (BufferedInputStream bufferedInputStream = new BufferedInputStream(input)) {
        PackageManifest proto = PackageManifest.parseFrom(bufferedInputStream);
        for (JavaSourcePackage source : proto.getSourcesList()) {
          String absPath = getAbsolutePath(decoder, source);
          if (absPath != null) {
            outputMap.put(absPath, source.getPackageString());
          }
        }
      }
      return outputMap;
    }
    catch (IOException e) {
      LOG.error(e);
      return outputMap;
    }
  }

  /**
   * Returns null if the artifact location file can't be found,
   * presumably because it's been removed from the file system since the blaze build.
   */
  @Nullable
  private static String getAbsolutePath(ArtifactLocationDecoder decoder,
                                        JavaSourcePackage source) {
    if (!source.hasArtifactLocation()) {
      return source.getAbsolutePath();
    }
    ArtifactLocation location = decoder.decode(source.getArtifactLocation());
    if (location == null) {
      return null;
    }
    return location.getFile().getAbsolutePath();
  }

}

