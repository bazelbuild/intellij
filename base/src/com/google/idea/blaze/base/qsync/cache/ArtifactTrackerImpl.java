/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.qsync.cache;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.idea.blaze.qsync.project.BlazeProjectDataStorage.AAR_DIRECTORY;
import static com.google.idea.blaze.qsync.project.BlazeProjectDataStorage.GEN_SRC_DIRECTORY;
import static com.google.idea.blaze.qsync.project.BlazeProjectDataStorage.LIBRARY_DIRECTORY;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.Uninterruptibles;
import com.google.devtools.intellij.qsync.ArtifactTrackerData.BuildArtifacts;
import com.google.devtools.intellij.qsync.ArtifactTrackerData.TargetArtifacts;
import com.google.idea.blaze.base.command.buildresult.OutputArtifact;
import com.google.idea.blaze.base.qsync.ArtifactTracker;
import com.google.idea.blaze.base.qsync.OutputInfo;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.google.idea.blaze.common.DownloadTrackingScope;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.exception.BuildException;
import com.google.protobuf.ExtensionRegistry;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * A class that track the artifacts during build and its local copy.
 *
 * <p>This class maps all the targets that have been built to their artifacts.
 */
public class ArtifactTrackerImpl implements ArtifactTracker {

  private static final Logger logger = Logger.getInstance(ArtifactTrackerImpl.class);

  // The artifacts in the cache. Note that artifacts that do not produce files are also stored here.
  // So, it is not the same for a label not to be present, than a label to have an empty list.
  private final HashMap<Label, List<Path>> artifacts = new HashMap<>();

  private final FileCache jarCache;
  private final FileCache aarCache;
  private final FileCache generatedSrcFileCache;
  private final Path persistentFile;

  public ArtifactTrackerImpl(
      BlazeImportSettings importSettings, ArtifactFetcher<OutputArtifact> artifactFetcher) {
    jarCache =
        createFileCache(
            artifactFetcher,
            getProjectDirectory(importSettings).resolve(LIBRARY_DIRECTORY),
            ImmutableSet.of());
    aarCache =
        createFileCache(
            artifactFetcher,
            getProjectDirectory(importSettings).resolve(AAR_DIRECTORY),
            ImmutableSet.of("aar"));
    generatedSrcFileCache =
        createFileCache(
            artifactFetcher,
            getProjectDirectory(importSettings).resolve(GEN_SRC_DIRECTORY),
            ImmutableSet.of("jar", "srcjar"));
    persistentFile = getProjectDirectory(importSettings).resolve(".artifact.info");
  }

  private static FileCache createFileCache(
      ArtifactFetcher<OutputArtifact> artifactFetcher,
      Path cacheDirectory,
      ImmutableSet<String> zipFileExtensions) {
    Path cacheDotDirectory = cacheDirectory.resolveSibling("." + cacheDirectory.getFileName());
    return new FileCache(
        artifactFetcher,
        new CacheDirectoryManager(cacheDirectory, cacheDotDirectory),
        new DefaultCacheLayout(cacheDirectory, cacheDotDirectory, zipFileExtensions));
  }

  public void initialize() {
    jarCache.initialize();
    aarCache.initialize();
    generatedSrcFileCache.initialize();
    loadFromDisk();
  }

  @Override
  public void clear() throws IOException {
    artifacts.clear();
    jarCache.clear();
    aarCache.clear();
    generatedSrcFileCache.clear();
    saveState();
  }

  private void saveState() throws IOException {
    BuildArtifacts.Builder builder = BuildArtifacts.newBuilder();
    for (Entry<Label, List<Path>> entry : artifacts.entrySet()) {
      ImmutableList<String> paths =
          entry.getValue().stream().map(Path::toString).collect(toImmutableList());
      builder.addArtifacts(
          TargetArtifacts.newBuilder()
              .setTarget(entry.getKey().toString())
              .addAllArtifactPaths(paths));
    }
    try (OutputStream stream = new GZIPOutputStream(Files.newOutputStream(persistentFile))) {
      builder.build().writeTo(stream);
    }
  }

  private void loadFromDisk() {
    if (!Files.exists(persistentFile)) {
      return;
    }
    artifacts.clear();
    try (InputStream stream = new GZIPInputStream(Files.newInputStream(persistentFile))) {
      BuildArtifacts saved = BuildArtifacts.parseFrom(stream, ExtensionRegistry.getEmptyRegistry());
      for (TargetArtifacts targetArtifact : saved.getArtifactsList()) {
        Label label = Label.of(targetArtifact.getTarget());
        List<Path> artifactPathList = artifacts.computeIfAbsent(label, k -> new ArrayList<>());
        for (String path : targetArtifact.getArtifactPathsList()) {
          artifactPathList.add(Path.of(path));
        }
      }
    } catch (IOException e) {
      // TODO: If there is an error parsing the index, reinitialize the cache properly.
    }
  }

  /**
   * Merges TargetToDeps into tracker maps and cache necessary OutputArtifact to local. The
   * artifacts will not be added into tracker if it's failed to be cached.
   */
  @Override
  public UpdateResult update(Set<Label> targets, OutputInfo outputInfo, BlazeContext outerContext)
      throws BuildException {
    try (BlazeContext context = BlazeContext.create(outerContext)) {
      DownloadTrackingScope downloads = new DownloadTrackingScope();
      context.push(downloads);
      ListenableFuture<ImmutableSet<Path>> jars = jarCache.cache(outputInfo.getJars(), context);
      ListenableFuture<ImmutableSet<Path>> aars = aarCache.cache(outputInfo.getAars(), context);
      ListenableFuture<ImmutableSet<Path>> genSrcs =
          generatedSrcFileCache.cache(outputInfo.getGeneratedSources(), context);
      if (downloads.getFileCount() > 0) {
        context.output(
            PrintOutput.log(
                "Downloading %d build artifacts (%s)",
                downloads.getFileCount(), StringUtil.formatFileSize(downloads.getTotalBytes())));
      }

      ListenableFuture<?> cacheFetches = Futures.allAsList(jars, aars, genSrcs);
      context.addCancellationHandler(() -> cacheFetches.cancel(false));
      Uninterruptibles.getUninterruptibly(cacheFetches);
      ImmutableSet<Path> updated =
          ImmutableSet.<Path>builder()
              .addAll(Futures.getDone(jars))
              .addAll(Futures.getDone(aars))
              .addAll(Futures.getDone(genSrcs))
              .build();

      for (BuildArtifacts artifacts : outputInfo.getArtifacts()) {
        updateMaps(targets, artifacts);
      }
      saveState();
      return UpdateResult.create(updated, ImmutableSet.of());
    } catch (ExecutionException | IOException e) {
      throw new BuildException(e);
    }
  }

  /**
   * Updates the index with the newly built targets.
   *
   * @param targets the list of targets that were expected to be built. (From blaze query)
   * @param newArtifacts the artifacts that were actually built. From (blaze build)
   */
  private void updateMaps(Set<Label> targets, BuildArtifacts newArtifacts) {
    for (TargetArtifacts targetArtifacts : newArtifacts.getArtifactsList()) {
      ImmutableList<Path> paths =
          targetArtifacts.getArtifactPathsList().stream().map(Path::of).collect(toImmutableList());
      Label label = Label.of(targetArtifacts.getTarget());
      artifacts.put(label, paths);
    }
    for (Label label : targets) {
      if (!artifacts.containsKey(label)) {
        logger.warn(
            "Target " + label + " was not built. If the target is an alias, this is expected");
        artifacts.put(label, new ArrayList<>());
      }
    }
  }

  @Override
  public Path getExternalAarDirectory() {
    return aarCache.getDirectory();
  }

  @Override
  public Path getGenSrcCacheDirectory() {
    return generatedSrcFileCache.getDirectory();
  }

  @Override
  public ImmutableList<Path> getGenSrcSubfolders() throws IOException {
    try (Stream<Path> pathStream = Files.list(generatedSrcFileCache.getDirectory())) {
      return pathStream.collect(toImmutableList());
    }
  }

  @Override
  public Set<Label> getLiveCachedTargets() {
    return artifacts.keySet();
  }

  /** Returns directory of project. */
  private static Path getProjectDirectory(BlazeImportSettings importSettings) {
    return BlazeDataStorage.getProjectDataDir(importSettings).toPath();
  }
}
