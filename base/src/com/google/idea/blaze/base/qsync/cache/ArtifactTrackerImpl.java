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
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.idea.blaze.qsync.project.BlazeProjectDataStorage.AAR_DIRECTORY;
import static com.google.idea.blaze.qsync.project.BlazeProjectDataStorage.GEN_SRC_DIRECTORY;
import static com.google.idea.blaze.qsync.project.BlazeProjectDataStorage.LIBRARY_DIRECTORY;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.Uninterruptibles;
import com.google.devtools.intellij.qsync.ArtifactTrackerData.BuildArtifacts;
import com.google.devtools.intellij.qsync.ArtifactTrackerData.TargetArtifacts;
import com.google.idea.blaze.base.command.buildresult.OutputArtifact;
import com.google.idea.blaze.base.qsync.ArtifactTracker;
import com.google.idea.blaze.base.qsync.OutputInfo;
import com.google.idea.blaze.base.qsync.cache.ArtifactFetcher.ArtifactDestination;
import com.google.idea.blaze.base.qsync.cache.FileCache.OutputArtifactDestination;
import com.google.idea.blaze.base.qsync.cache.FileCache.OutputArtifactDestinationAndLayout;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.common.DownloadTrackingScope;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.GeneratedSourceProjectUpdater;
import com.google.idea.blaze.qsync.project.BlazeProjectSnapshot;
import com.google.idea.blaze.qsync.project.ProjectProto;
import com.google.protobuf.ExtensionRegistry;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * A class that track the artifacts during build and its local copy.
 *
 * <p>This class maps all the targets that have been built to their artifacts.
 */
public class ArtifactTrackerImpl implements ArtifactTracker {

  public static final String DIGESTS_DIRECTORY_NAME = ".digests";
  private static final Logger logger = Logger.getInstance(ArtifactTrackerImpl.class);

  // The artifacts relative path (blaze-out/xxx) that can be used to retrieve local copy in the
  // cache. Note that artifacts that do not produce files are also stored here. So, it is not the
  // same for a label not to be present, than a label to have an empty list.
  private final HashMap<Label, List<Path>> artifacts = new HashMap<>();

  private final ArtifactFetcher<OutputArtifact> artifactFetcher;
  @VisibleForTesting public final CacheDirectoryManager cacheDirectoryManager;
  private final FileCache jarCache;
  private final Path aarCacheDirectory;
  private final FileCache aarCache;
  private final Path generatedSrcFileCacheDirectory;
  private final FileCache generatedSrcFileCache;
  private final Path persistentFile;
  private final Path ideProjectBasePath;

  public ArtifactTrackerImpl(
      Path projectDirectory,
      Path ideProjectBasePath,
      ArtifactFetcher<OutputArtifact> artifactFetcher) {
    this.ideProjectBasePath = ideProjectBasePath;
    this.artifactFetcher = artifactFetcher;

    FileCacheCreator fileCacheCreator = new FileCacheCreator();
    jarCache =
        fileCacheCreator.createFileCache(
            projectDirectory.resolve(LIBRARY_DIRECTORY), ImmutableSet.of(), ImmutableSet.of());
    aarCacheDirectory = projectDirectory.resolve(AAR_DIRECTORY);
    aarCache =
        fileCacheCreator.createFileCache(
            aarCacheDirectory, ImmutableSet.of("aar"), ImmutableSet.of());
    generatedSrcFileCacheDirectory = projectDirectory.resolve(GEN_SRC_DIRECTORY);
    generatedSrcFileCache =
        fileCacheCreator.createFileCache(
            generatedSrcFileCacheDirectory,
            ImmutableSet.of("jar", "srcjar"),
            ImmutableSet.of("java", "kt"));
    cacheDirectoryManager =
        new CacheDirectoryManager(
            projectDirectory.resolve(DIGESTS_DIRECTORY_NAME),
            fileCacheCreator.getCacheDirectories());
    persistentFile = projectDirectory.resolve(".artifact.info");
  }

  private static class FileCacheCreator {
    private final ImmutableList.Builder<Path> cacheDirectories = ImmutableList.builder();

    public FileCache createFileCache(
        Path cacheDirectory,
        ImmutableSet<String> zipFileExtensions,
        ImmutableSet<String> directoryFileExtension) {
      Path cacheDotDirectory = cacheDirectory.resolveSibling("." + cacheDirectory.getFileName());
      cacheDirectories.add(cacheDirectory);
      cacheDirectories.add(cacheDotDirectory);
      return new FileCache(
          new DefaultCacheLayout(
              cacheDirectory, cacheDotDirectory, zipFileExtensions, directoryFileExtension));
    }

    public ImmutableList<Path> getCacheDirectories() {
      return cacheDirectories.build();
    }
  }

  public void initialize() {
    cacheDirectoryManager.initialize();
    loadFromDisk();
  }

  @Override
  public void clear() throws IOException {
    artifacts.clear();
    cacheDirectoryManager.clear();
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

  @Override
  public Optional<ImmutableSet<Path>> getCachedFiles(Label target) {
    List<Path> paths = artifacts.get(target);
    if (paths == null) {
      return Optional.empty();
    }
    return Optional.of(
        paths.stream()
            .map(
                artifactPath ->
                    getCachedFile(artifactPath)
                        .orElseThrow(
                            () ->
                                new IllegalStateException(
                                    "File cache and artifact map are not matched. Failed to get"
                                        + " cached file for artifact "
                                        + artifactPath)))
            .collect(toImmutableSet()));
  }

  private Optional<Path> getCachedFile(Path artifactPath) {
    String path = artifactPath.toString();
    if (path.endsWith(".aar")) {
      return aarCache.getCacheFile(path);
    }
    Optional<Path> cachedFile = jarCache.getCacheFile(path);
    if (cachedFile.isPresent()) {
      return cachedFile;
    }

    return generatedSrcFileCache.getCacheFile(path);
  }

  /**
   * Fetches the output artifacts requested in {@code artifactToDestinationMap}.
   *
   * @return {@link OutputArtifactDestinationAndLayout}'s from the original request.
   */
  private <T extends OutputArtifactDestination> ListenableFuture<Collection<T>> fetchArtifacts(
      BlazeContext context, ImmutableMap<OutputArtifact, T> artifactToDestinationMap) {
    final ImmutableMap<OutputArtifact, ArtifactDestination> artifactToDestinationPathMap =
        runMeasureAndLog(
            () ->
                artifactToDestinationMap.entrySet().stream()
                    .filter(
                        it ->
                            !Objects.equals(
                                it.getKey().getDigest(),
                                cacheDirectoryManager.getStoredArtifactDigest(it.getKey())))
                    .collect(
                        ImmutableMap.toImmutableMap(
                            Entry::getKey,
                            it -> new ArtifactDestination(it.getValue().getCopyDestination()))),
            String.format("Read %d artifact digests", artifactToDestinationMap.size()),
            1000);

    runMeasureAndLog(
        () -> {
          for (OutputArtifact outputArtifact : artifactToDestinationPathMap.keySet()) {
            // Once fetching starts we do not know the state of downloaded files. If fetching fails,
            // consider files lost.
            cacheDirectoryManager.setStoredArtifactDigest(outputArtifact, "");
          }
        },
        String.format("Reset %d artifact digests", artifactToDestinationPathMap.size()),
        1000);
    return Futures.transform(
        artifactFetcher.copy(artifactToDestinationPathMap, context),
        fetchedArtifacts ->
            runMeasureAndLog(
                () -> {
                  ImmutableList.Builder<T> result = ImmutableList.builder();
                  for (Entry<OutputArtifact, ArtifactDestination> entry :
                      artifactToDestinationPathMap.entrySet()) {
                    T artifactDestination = artifactToDestinationMap.get(entry.getKey());
                    Preconditions.checkNotNull(artifactDestination);
                    cacheDirectoryManager.setStoredArtifactDigest(
                        entry.getKey(), entry.getKey().getDigest());
                    result.add(artifactDestination);
                  }
                  return result.build();
                },
                String.format("Store %d artifact digests", artifactToDestinationPathMap.size()),
                1000),
        ArtifactFetcher.EXECUTOR);
  }

  /**
   * Caches {@code artifacts} in the local cache and returns paths that the IDE should use to find
   * them.
   *
   * @noinspection UnstableApiUsage
   */
  @VisibleForTesting
  public ListenableFuture<ImmutableSet<Path>> cache(
      ImmutableMap<OutputArtifact, OutputArtifactDestinationAndLayout> artifactToDestinationMap,
      BlazeContext context)
      throws IOException {
    return Futures.transform(
        fetchArtifacts(context, artifactToDestinationMap),
        this::prepareFinalLayouts,
        ArtifactFetcher.EXECUTOR);
  }

  /**
   * Extracts zip-like files in the {@code sourcePaths} into the final destination directories.
   *
   * <p>Any existing files and directories at the destination paths are deleted.
   */
  private ImmutableSet<Path> prepareFinalLayouts(
      Collection<OutputArtifactDestinationAndLayout> destinations) {
    ImmutableSet.Builder<Path> result = ImmutableSet.builder();
    try {
      for (OutputArtifactDestinationAndLayout destination : destinations) {
        result.add(destination.prepareFinalLayout());
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return result.build();
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
      ImmutableMap<OutputArtifact, OutputArtifactDestinationAndLayout> artifactMap =
          ImmutableMap.<OutputArtifact, OutputArtifactDestinationAndLayout>builder()
              .putAll(jarCache.prepareDestinationPathsAndDirectories(outputInfo.getJars()))
              .putAll(aarCache.prepareDestinationPathsAndDirectories(outputInfo.getAars()))
              .putAll(
                  generatedSrcFileCache.prepareDestinationPathsAndDirectories(
                      outputInfo.getGeneratedSources()))
              .buildOrThrow();

      ListenableFuture<ImmutableSet<Path>> artifactPaths = cache(artifactMap, context);
      if (downloads.getFileCount() > 0) {
        context.output(
            PrintOutput.log(
                "Downloading %d build artifacts (%s)",
                downloads.getFileCount(), StringUtil.formatFileSize(downloads.getTotalBytes())));
      }

      ImmutableSet<Path> updated = Uninterruptibles.getUninterruptibly(artifactPaths);

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
  public BlazeProjectSnapshot updateSnapshot(BlazeProjectSnapshot snapshot) throws IOException {
    ProjectProto.Project projectProto = snapshot.project();

    Path genSrcCacheRelativeToProject =
        ideProjectBasePath.relativize(generatedSrcFileCacheDirectory);
    ImmutableList<Path> subfolders = getGenSrcSubfolders();
    GeneratedSourceProjectUpdater updater =
        new GeneratedSourceProjectUpdater(projectProto, genSrcCacheRelativeToProject, subfolders);

    projectProto = updater.addGenSrcContentEntry();
    return BlazeProjectSnapshot.builder()
        .queryData(snapshot.queryData())
        .graph(snapshot.graph())
        .project(projectProto)
        .build();
  }

  private ImmutableList<Path> getGenSrcSubfolders() throws IOException {
    try (Stream<Path> pathStream = Files.list(generatedSrcFileCacheDirectory)) {
      return pathStream.collect(toImmutableList());
    }
  }

  @Override
  public Set<Label> getLiveCachedTargets() {
    return artifacts.keySet();
  }

  @Override
  public Path getExternalAarDirectory() {
    return aarCacheDirectory;
  }

  private <T> T runMeasureAndLog(Supplier<T> block, String operation, int maxToleratedMs) {
    final Stopwatch stopwatch = Stopwatch.createStarted();
    try {
      return block.get();
    } finally {
      final long elapsed = stopwatch.elapsed(MILLISECONDS);
      if (elapsed > maxToleratedMs) {
        logger.warn(String.format("%s took %d ms", operation, elapsed));
      }
    }
  }

  private void runMeasureAndLog(Runnable block, String operation, int maxToleratedMs) {
    Object unused =
        runMeasureAndLog(
            () -> {
              block.run();
              return null;
            },
            operation,
            maxToleratedMs);
  }
}
