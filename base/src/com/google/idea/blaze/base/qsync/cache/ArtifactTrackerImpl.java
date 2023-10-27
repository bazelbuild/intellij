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
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.util.concurrent.Uninterruptibles.getUninterruptibly;
import static com.google.idea.blaze.qsync.project.BlazeProjectDataStorage.AAR_DIRECTORY;
import static com.google.idea.blaze.qsync.project.BlazeProjectDataStorage.DEPENDENCIES_SOURCES;
import static com.google.idea.blaze.qsync.project.BlazeProjectDataStorage.GEN_HEADERS_DIRECTORY;
import static com.google.idea.blaze.qsync.project.BlazeProjectDataStorage.GEN_SRC_DIRECTORY;
import static com.google.idea.blaze.qsync.project.BlazeProjectDataStorage.LIBRARY_DIRECTORY;
import static com.google.idea.blaze.qsync.project.BlazeProjectDataStorage.RENDER_JARS_DIRECTORY;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.function.Predicate.not;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.intellij.qsync.ArtifactTrackerData.ArtifactTrackerState;
import com.google.devtools.intellij.qsync.ArtifactTrackerData.CachedArtifacts;
import com.google.devtools.intellij.qsync.ArtifactTrackerData.JavaArtifacts;
import com.google.devtools.intellij.qsync.ArtifactTrackerData.JavaTargetArtifacts;
import com.google.devtools.intellij.qsync.CcCompilationInfoOuterClass.CcCompilationInfo;
import com.google.idea.blaze.base.command.buildresult.OutputArtifact;
import com.google.idea.blaze.base.logging.utils.querysync.BuildDepsStats;
import com.google.idea.blaze.base.logging.utils.querysync.BuildDepsStatsScope;
import com.google.idea.blaze.base.qsync.ArtifactTracker;
import com.google.idea.blaze.base.qsync.ArtifactTrackerUpdateResult;
import com.google.idea.blaze.base.qsync.OutputGroup;
import com.google.idea.blaze.base.qsync.OutputInfo;
import com.google.idea.blaze.base.qsync.RenderJarArtifactTracker;
import com.google.idea.blaze.base.qsync.RenderJarInfo;
import com.google.idea.blaze.base.qsync.cache.ArtifactFetcher.ArtifactDestination;
import com.google.idea.blaze.base.qsync.cache.FileCache.CacheLayout;
import com.google.idea.blaze.base.qsync.cache.FileCache.OutputArtifactDestination;
import com.google.idea.blaze.base.qsync.cache.FileCache.OutputArtifactDestinationAndLayout;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.common.DownloadTrackingScope;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.GeneratedSourceProjectUpdater;
import com.google.idea.blaze.qsync.SrcJarProjectUpdater;
import com.google.idea.blaze.qsync.cc.CcDependenciesInfo;
import com.google.idea.blaze.qsync.project.BuildGraphData;
import com.google.idea.blaze.qsync.project.ProjectDefinition;
import com.google.idea.blaze.qsync.project.ProjectPath;
import com.google.idea.blaze.qsync.project.ProjectProto;
import com.google.idea.common.experiments.BoolExperiment;
import com.google.protobuf.ExtensionRegistry;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * A class that track the artifacts during build and its local copy.
 *
 * <p>This class maps all the targets that have been built to their artifacts.
 */
public class ArtifactTrackerImpl implements ArtifactTracker, RenderJarArtifactTracker {

  private static final BoolExperiment ATTACH_DEP_SRCJARS =
      new BoolExperiment("querysync.attach.dep.srcjars", true);

  public static final String DIGESTS_DIRECTORY_NAME = ".digests";
  public static final int STORAGE_VERSION = 3;
  private static final Logger logger = Logger.getInstance(ArtifactTrackerImpl.class);

  // Information about java dependency artifacts derived when the dependencies were built.
  // Note that artifacts that do not produce files are also stored here.
  private final Map<Label, ArtifactInfo> javaArtifacts = new HashMap<>();
  private CcDependenciesInfo ccDepencenciesInfo = CcDependenciesInfo.EMPTY;
  // Information about the origin of files in the cache. For each file in the cache, stores the
  // artifact key that the file was derived from.
  private final Map<Path, Path> cachePathToArtifactKeyMap = new HashMap<>();

  private final ArtifactFetcher<OutputArtifact> artifactFetcher;
  private final ProjectPath.Resolver projectPathResolver;
  private final ProjectDefinition projectDefinition;
  @VisibleForTesting public final CacheDirectoryManager cacheDirectoryManager;
  private final Path jarCacheDirectory;
  private final FileCache jarCache;
  private final Path aarCacheDirectory;
  private final Path renderJarCacheDirectory;
  @VisibleForTesting final Path generatedHeadersDirectory;
  private final FileCache renderJarCache;
  private final FileCache aarCache;
  private final Path generatedSrcFileCacheDirectory;
  private final FileCache generatedSrcFileCache;
  private final Path generatedExternalSrcFileCacheDirectory;
  private final FileCache generatedExternalSrcFileCache;
  private final FileCache generatedHeadersCache;
  private final Path persistentFile;
  private final Path ideProjectBasePath;

  public ArtifactTrackerImpl(
      Path projectDirectory,
      Path ideProjectBasePath,
      ArtifactFetcher<OutputArtifact> artifactFetcher,
      ProjectPath.Resolver projectPathResolver,
      ProjectDefinition projectDefinition) {
    this.ideProjectBasePath = ideProjectBasePath;
    this.artifactFetcher = artifactFetcher;
    this.projectPathResolver = projectPathResolver;
    this.projectDefinition = projectDefinition;

    FileCacheCreator fileCacheCreator = new FileCacheCreator();
    jarCacheDirectory = projectDirectory.resolve(LIBRARY_DIRECTORY);
    jarCache = fileCacheCreator.createFileCache(jarCacheDirectory, ImmutableSet.of(), false);
    aarCacheDirectory = projectDirectory.resolve(AAR_DIRECTORY);
    aarCache = fileCacheCreator.createFileCache(aarCacheDirectory, ImmutableSet.of("aar"), false);
    renderJarCacheDirectory = projectDirectory.resolve(RENDER_JARS_DIRECTORY);
    renderJarCache =
        fileCacheCreator.createFileCache(renderJarCacheDirectory, ImmutableSet.of(), false);
    generatedSrcFileCacheDirectory = projectDirectory.resolve(GEN_SRC_DIRECTORY);
    generatedSrcFileCache =
        fileCacheCreator.createFileCache(
            generatedSrcFileCacheDirectory, ImmutableSet.of("jar", "srcjar"), true);
    generatedExternalSrcFileCacheDirectory = projectDirectory.resolve(DEPENDENCIES_SOURCES);
    generatedExternalSrcFileCache =
        fileCacheCreator.createFileCache(
            generatedExternalSrcFileCacheDirectory, ImmutableSet.of(), false);
    generatedHeadersDirectory = projectDirectory.resolve(GEN_HEADERS_DIRECTORY);
    generatedHeadersCache =
        fileCacheCreator.createFileCache(
            new ArtifactPathCacheLayout(generatedHeadersDirectory), generatedHeadersDirectory);
    cacheDirectoryManager =
        new CacheDirectoryManager(
            projectDirectory.resolve(DIGESTS_DIRECTORY_NAME),
            fileCacheCreator.getCacheDirectories());
    persistentFile = projectDirectory.resolve("artifact_tracker_state");
  }

  private static class FileCacheCreator {
    private final ImmutableList.Builder<Path> cacheDirectories = ImmutableList.builder();

    public FileCache createFileCache(
        Path cacheDirectory, ImmutableSet<String> zipFileExtensions, boolean handleJavaSources) {
      // TODO(mathewi) this is a bit messy, make a cleaner way of dealing with zips & java srcs
      Path cacheDotDirectory = cacheDirectory.resolveSibling("." + cacheDirectory.getFileName());
      CacheLayout layout =
          new DefaultCacheLayout(cacheDirectory, cacheDotDirectory, zipFileExtensions);
      if (handleJavaSources) {
        layout =
            new DelegatingCacheLayout(
                layout, new JavaSourcesCacheLayout(cacheDirectory, cacheDotDirectory));
      }
      return createFileCache(layout, cacheDirectory, cacheDotDirectory);
    }

    public FileCache createFileCache(CacheLayout layout, Path... cacheDirs) {
      cacheDirectories.add(cacheDirs);
      return new FileCache(layout);
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
    javaArtifacts.clear();
    cacheDirectoryManager.clear();
    saveState();
  }

  private void saveState() throws IOException {
    JavaArtifacts.Builder builder = JavaArtifacts.newBuilder();
    javaArtifacts.values().stream().map(ArtifactInfo::toProto).forEach(builder::addArtifacts);
    CcCompilationInfo ccCompilationInfo = ccDepencenciesInfo.toProto();
    CachedArtifacts.Builder cachedArtifactsBuilder = CachedArtifacts.newBuilder();
    for (Map.Entry<Path, Path> entry : cachePathToArtifactKeyMap.entrySet()) {
      cachedArtifactsBuilder.putCachePathToArtifactPath(
          entry.getKey().toString(), entry.getValue().toString());
    }
    try (OutputStream stream = new GZIPOutputStream(Files.newOutputStream(persistentFile))) {
      ArtifactTrackerState.newBuilder()
          .setVersion(STORAGE_VERSION)
          .setArtifactInfo(builder.build())
          .setCachedArtifacts(cachedArtifactsBuilder.build())
          .setCcCompilationInfo(ccCompilationInfo)
          .build()
          .writeTo(stream);
    }
  }

  private void loadFromDisk() {
    if (!Files.exists(persistentFile)) {
      return;
    }
    javaArtifacts.clear();
    cachePathToArtifactKeyMap.clear();
    try (InputStream stream = new GZIPInputStream(Files.newInputStream(persistentFile))) {
      ArtifactTrackerState saved =
          ArtifactTrackerState.parseFrom(stream, ExtensionRegistry.getEmptyRegistry());
      if (saved.getVersion() != STORAGE_VERSION) {
        return;
      }
      cachePathToArtifactKeyMap.putAll(
          saved.getCachedArtifacts().getCachePathToArtifactPathMap().entrySet().stream()
              .collect(toImmutableMap(e -> Path.of(e.getKey()), e -> Path.of(e.getValue()))));
      javaArtifacts.putAll(
          saved.getArtifactInfo().getArtifactsList().stream()
              .map(ArtifactInfo::create)
              .collect(toImmutableMap(ArtifactInfo::label, Function.identity())));
      for (JavaTargetArtifacts targetArtifact : saved.getArtifactInfo().getArtifactsList()) {
        ArtifactInfo artifactInfo = ArtifactInfo.create(targetArtifact);
        javaArtifacts.put(artifactInfo.label(), artifactInfo);
      }
      ccDepencenciesInfo = CcDependenciesInfo.create(saved.getCcCompilationInfo());
    } catch (IOException e) {
      logger.warn("Failed to load artifact tracker state", e);
      // TODO: If there is an error parsing the index, reinitialize the cache properly.
    }
  }

  @Override
  public ImmutableSet<Path> getTargetSources(Path cachedArtifact) {
    if (!cachePathToArtifactKeyMap.containsKey(cachedArtifact)) {
      return ImmutableSet.of();
    }
    Path artifactPath = cachePathToArtifactKeyMap.get(cachedArtifact);
    return javaArtifacts.values().stream()
        .filter(d -> d.containsPath(artifactPath))
        .map(ArtifactInfo::sources)
        .flatMap(Set::stream)
        .collect(toImmutableSet());
  }

  @Override
  public Optional<ImmutableSet<Path>> getCachedFiles(Label target) {
    ArtifactInfo artifactInfo = javaArtifacts.get(target);
    if (artifactInfo == null) {
      return Optional.empty();
    }

    Multimap<Path, Path> artifactToCachedMap =
        Multimaps.invertFrom(
            Multimaps.forMap(cachePathToArtifactKeyMap), ArrayListMultimap.create());
    return Optional.of(
        artifactInfo
            .artifactStream()
            .map(artifactToCachedMap::get)
            .flatMap(Collection::stream)
            .collect(toImmutableSet()));
  }

  /**
   * Fetches the output artifacts requested in {@code artifactToDestinationMap}.
   *
   * @return A map of {@link OutputArtifactDestination}'s from the original request to the original
   *     artifact key that it was derived from.
   */
  private <T extends OutputArtifactDestination> ListenableFuture<Map<T, Path>> fetchArtifacts(
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
                        toImmutableMap(
                            Entry::getKey,
                            it -> new ArtifactDestination(it.getValue().getCopyDestination()))),
            String.format("Read %d artifact digests", artifactToDestinationMap.size()),
            Duration.ofSeconds(1));

    runMeasureAndLog(
        () -> {
          for (OutputArtifact outputArtifact : artifactToDestinationPathMap.keySet()) {
            // Once fetching starts we do not know the state of downloaded files. If fetching fails,
            // consider files lost.
            cacheDirectoryManager.setStoredArtifactDigest(outputArtifact, "");
          }
        },
        String.format("Reset %d artifact digests", artifactToDestinationPathMap.size()),
        Duration.ofSeconds(1));
    return Futures.transform(
        artifactFetcher.copy(artifactToDestinationPathMap, context),
        unused ->
            runMeasureAndLog(
                () -> {
                  ImmutableMap.Builder<T, Path> destinationToArtifactMap = ImmutableMap.builder();
                  for (OutputArtifact artifact : artifactToDestinationPathMap.keySet()) {
                    T artifactDestination = artifactToDestinationMap.get(artifact);
                    Preconditions.checkNotNull(artifactDestination);
                    cacheDirectoryManager.setStoredArtifactDigest(artifact, artifact.getDigest());
                    destinationToArtifactMap.put(
                        artifactDestination, Path.of(artifact.getRelativePath()));
                  }
                  return destinationToArtifactMap.buildOrThrow();
                },
                String.format("Store %d artifact digests", artifactToDestinationPathMap.size()),
                Duration.ofSeconds(1)),
        ArtifactFetcher.EXECUTOR);
  }

  /**
   * Extracts zip-like files in the {@code sourcePaths} into the final destination directories.
   *
   * <p>Any existing files and directories at the destination paths are deleted.
   *
   * @return A map of final destination path to the key of the artifact that it was derived from.
   */
  private ImmutableMap<Path, Path> prepareFinalLayouts(
      Map<OutputArtifactDestinationAndLayout, Path> destinationToArtifact) {
    ImmutableMap.Builder<Path, Path> result = ImmutableMap.builder();
    for (OutputArtifactDestinationAndLayout destination : destinationToArtifact.keySet()) {
      try {
        result.put(destination.prepareFinalLayout(), destinationToArtifact.get(destination));
      } catch (IOException e) {
        throw new UncheckedIOException(
            "Failed to prepare " + destinationToArtifact.get(destination), e);
      }
    }
    return result.build();
  }

  /** Fetches, caches and sets up new render jar artifacts. */
  @Override
  public ArtifactTrackerUpdateResult update(
      Set<Label> targets, RenderJarInfo renderJarInfo, BlazeContext outerContext)
      throws BuildException {
    ImmutableMap<OutputArtifact, OutputArtifactDestinationAndLayout> artifactMap;
    try {
      artifactMap = renderJarInfoToArtifactMap(renderJarInfo);
    } catch (IOException e) {
      throw new BuildException(e);
    }
    try (BlazeContext context = BlazeContext.create(outerContext)) {
      ImmutableMap<Path, Path> updated = cache(context, artifactMap);
      saveState();
      return ArtifactTrackerUpdateResult.create(updated.keySet(), ImmutableSet.of());
    } catch (ExecutionException | IOException e) {
      throw new BuildException(e);
    }
  }

  /** Fetches, caches and sets up new artifacts. */
  @Override
  public ArtifactTrackerUpdateResult update(
      Set<Label> targets, OutputInfo outputInfo, BlazeContext outerContext) throws BuildException {
    ImmutableMap<OutputArtifact, OutputArtifactDestinationAndLayout> artifactMap;
    try {
      artifactMap = outputInfoToArtifactMap(outputInfo);
    } catch (IOException e) {
      throw new BuildException(e);
    }
    try (BlazeContext context = BlazeContext.create(outerContext)) {
      ImmutableMap<Path, Path> updated = cache(context, artifactMap);

      this.cachePathToArtifactKeyMap.putAll(updated);
      for (JavaArtifacts artifacts : outputInfo.getArtifactInfo()) {
        updateMaps(targets, artifacts);
      }
      CcDependenciesInfo.Builder ccDepsBuilder = ccDepencenciesInfo.toBuilder();
      outputInfo.getCcCompilationInfo().forEach(ccDepsBuilder::add);
      ccDepencenciesInfo = ccDepsBuilder.build();

      saveState();
      return ArtifactTrackerUpdateResult.create(updated.keySet(), ImmutableSet.of());
    } catch (ExecutionException | IOException e) {
      throw new BuildException(e);
    }
  }

  /**
   * Caches {@code artifacts} in the local cache and returns a map from paths that the IDE should
   * use to find them to the original artifact path.
   *
   * @noinspection UnstableApiUsage
   */
  private ImmutableMap<Path, Path> cache(
      BlazeContext context,
      ImmutableMap<OutputArtifact, OutputArtifactDestinationAndLayout> artifactMap)
      throws ExecutionException {
    Stopwatch stopwatch = Stopwatch.createStarted();
    DownloadTrackingScope downloads = new DownloadTrackingScope();
    context.push(downloads);
    Optional<BuildDepsStats.Builder> builder = BuildDepsStatsScope.fromContext(context);

    ListenableFuture<ImmutableMap<Path, Path>> cachePathToArtifactKeyMapFuture =
        Futures.transform(
            fetchArtifacts(context, artifactMap),
            this::prepareFinalLayouts,
            ArtifactFetcher.EXECUTOR);
    if (downloads.getFileCount() > 0) {
      context.output(
          PrintOutput.log(
              "Downloading %d artifacts (%s)",
              downloads.getFileCount(), StringUtil.formatFileSize(downloads.getTotalBytes())));
      builder.ifPresent(
          stats -> {
            stats.setArtifactBytesConsumed(downloads.getTotalBytes());
          });
    }

    ImmutableMap<Path, Path> updated = getUninterruptibly(cachePathToArtifactKeyMapFuture);
    builder.ifPresent(stats -> stats.setUpdatedFilesCount(updated.size()));
    ImmutableSet<Path> updatedFiles = updated.keySet();
    ImmutableSet<String> removedKeys = ImmutableSet.of();
    context.output(
        PrintOutput.log(
            String.format(
                "Updated cache in %d ms: updated %d artifacts, removed %d artifacts",
                stopwatch.elapsed().toMillis(), updatedFiles.size(), removedKeys.size())));
    return updated;
  }

  private ImmutableMap<OutputArtifact, OutputArtifactDestinationAndLayout>
      renderJarInfoToArtifactMap(RenderJarInfo renderJarInfo) throws IOException {
    return ImmutableMap.<OutputArtifact, OutputArtifactDestinationAndLayout>builder()
        .putAll(renderJarCache.prepareDestinationPathsAndDirectories(renderJarInfo.getRenderJars()))
        .buildOrThrow();
  }

  private ImmutableMap<OutputArtifact, OutputArtifactDestinationAndLayout> outputInfoToArtifactMap(
      OutputInfo outputInfo) throws IOException {

    ImmutableListMultimap<Boolean, OutputArtifact> genSrcsByInclusion =
        getGensrcsByInclusion(outputInfo);
    return ImmutableMap.<OutputArtifact, OutputArtifactDestinationAndLayout>builder()
        .putAll(jarCache.prepareDestinationPathsAndDirectories(outputInfo.get(OutputGroup.JARS)))
        .putAll(aarCache.prepareDestinationPathsAndDirectories(outputInfo.get(OutputGroup.AARS)))
        .putAll(
            generatedSrcFileCache.prepareDestinationPathsAndDirectories(
                genSrcsByInclusion.get(true)))
        .putAll(
            generatedExternalSrcFileCache.prepareDestinationPathsAndDirectories(
                genSrcsByInclusion.get(false)))
        .putAll(
            generatedHeadersCache.prepareDestinationPathsAndDirectories(
                outputInfo.get(OutputGroup.CC_HEADERS)))
        .buildOrThrow();
  }

  /**
   * Extracts gensrscs from {@link OutputInfo} and groups them based on where they belong to a
   * project target ({@code included=true}) or an external target ({@code included=false}).
   */
  private ImmutableListMultimap<Boolean, OutputArtifact> getGensrcsByInclusion(
      OutputInfo outputInfo) {
    // Create a map of (target) -> (all gensrcs build by that target) from the *.java-info.txt
    // files produced by the aspect.
    SetMultimap<Label, String> genSrcsByTarget =
        outputInfo.getArtifactInfo().stream()
            .map(JavaArtifacts::getArtifactsList)
            .flatMap(List::stream)
            .collect(
                Multimaps.flatteningToMultimap(
                    ta -> Label.of(ta.getTarget()),
                    ta -> ta.getGenSrcsList().stream(),
                    MultimapBuilder.hashKeys().hashSetValues()::build));
    // invert the map so we have a map pf (gensrc artifact) -> (target that built it)
    SetMultimap<String, Label> genSrcToTargets =
        Multimaps.invertFrom(genSrcsByTarget, MultimapBuilder.hashKeys().hashSetValues().build());

    // Index the generated sources in the output into using the map above. We rely on the fact that
    // all gensrcs in the build output are also listed in the *.java-info.txt files processed
    // above.
    return Multimaps.index(
        outputInfo.getGeneratedSources(),
        oa ->
            genSrcToTargets.get(oa.getRelativePath()).stream()
                .anyMatch(projectDefinition::isIncluded));
  }

  /**
   * Updates the index with the newly built targets.
   *
   * @param targets the list of targets that were expected to be built. (From blaze query)
   * @param newArtifacts the artifacts that were actually built. From (blaze build)
   */
  private void updateMaps(Set<Label> targets, JavaArtifacts newArtifacts) {
    for (JavaTargetArtifacts targetArtifacts : newArtifacts.getArtifactsList()) {
      ArtifactInfo artifactInfo = ArtifactInfo.create(targetArtifacts);
      javaArtifacts.put(artifactInfo.label(), artifactInfo);
    }
    for (Label label : targets) {
      if (!javaArtifacts.containsKey(label)) {
        logger.warn(
            "Target " + label + " was not built. If the target is an alias, this is expected");
        javaArtifacts.put(label, ArtifactInfo.empty(label));
      }
    }
  }

  private static final ImmutableSet<String> JAR_ZIP_EXTENSIONS =
      ImmutableSet.of("jar", "zip", "srcjar");

  private static boolean hasJarOrZipExtension(Path p) {
    return JAR_ZIP_EXTENSIONS.contains(FileUtil.getExtension(p.toString()));
  }

  @Override
  public ProjectProto.Project updateProjectProto(
      ProjectProto.Project projectProto, BuildGraphData graph, Context<?> context)
      throws BuildException {
    return updateProjectProtoForJavaDeps(projectProto);
  }

  private ProjectProto.Project updateProjectProtoForJavaDeps(ProjectProto.Project projectProto)
      throws BuildException {

    Path genSrcCacheRelativeToProject =
        ideProjectBasePath.relativize(generatedSrcFileCacheDirectory);
    ImmutableList<Path> subfolders;
    try {
      subfolders = getGenSrcSubfolders();
    } catch (IOException e) {
      throw new BuildException(e);
    }
    GeneratedSourceProjectUpdater updater =
        new GeneratedSourceProjectUpdater(projectProto, genSrcCacheRelativeToProject, subfolders);

    projectProto = updater.addGenSrcContentEntry();

    ImmutableSet<ProjectPath> workspaceSrcJars =
        javaArtifacts.values().stream()
            .map(ArtifactInfo::srcJars)
            .flatMap(Set::stream)
            .map(ProjectPath::workspaceRelative)
            .collect(ImmutableSet.toImmutableSet());

    ImmutableSet<ProjectPath> generatedExternalSrcJars =
        javaArtifacts.values().stream()
            .filter(not(ai -> projectDefinition.isIncluded(ai.label())))
            .map(ArtifactInfo::genSrcs)
            .flatMap(List::stream)
            .filter(ArtifactTrackerImpl::hasJarOrZipExtension)
            .map(generatedExternalSrcFileCache::getCacheFile)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .map(ideProjectBasePath::relativize)
            .map(ProjectPath::projectRelative)
            .collect(ImmutableSet.toImmutableSet());

    if (ATTACH_DEP_SRCJARS.getValue()) {
      SrcJarProjectUpdater srcJarUpdater =
          new SrcJarProjectUpdater(
              projectProto,
              Sets.union(workspaceSrcJars, generatedExternalSrcJars),
              projectPathResolver);
      projectProto = srcJarUpdater.addSrcJars();
    } else {
      logger.info("srcjar attachment disabled.");
    }

    return projectProto;
  }

  private ImmutableList<Path> getGenSrcSubfolders() throws IOException {
    try (Stream<Path> pathStream = Files.list(generatedSrcFileCacheDirectory)) {
      return pathStream.collect(toImmutableList());
    }
  }

  @Override
  public Set<Label> getLiveCachedTargets() {
    return javaArtifacts.keySet();
  }

  @Override
  public Path getExternalAarDirectory() {
    return aarCacheDirectory;
  }

  @Override
  public CcDependenciesInfo getCcDependenciesInfo() {
    return ccDepencenciesInfo;
  }

  private <T> T runMeasureAndLog(
      Supplier<T> block, String operation, Duration maxToleratedDuration) {
    final Stopwatch stopwatch = Stopwatch.createStarted();
    try {
      return block.get();
    } finally {
      final long elapsed = stopwatch.elapsed(MILLISECONDS);
      if (elapsed > maxToleratedDuration.toMillis()) {
        logger.warn(String.format("%s took %d ms", operation, elapsed));
      }
    }
  }

  private void runMeasureAndLog(Runnable block, String operation, Duration maxToleratedDuration) {
    Object unused =
        runMeasureAndLog(
            () -> {
              block.run();
              return null;
            },
            operation,
            maxToleratedDuration);
  }

  @Override
  public ImmutableList<File> getRenderJars() {
    return ImmutableList.copyOf(
        renderJarCacheDirectory.toFile().listFiles((file, name) -> name.endsWith(".jar")));
  }

  @Override
  public Integer getJarsCount() {
    try (Stream<Path> pathsStream = Files.walk(jarCacheDirectory)) {
      return pathsStream
          .filter(path -> !Files.isDirectory(path) && path.endsWith(".jar"))
          .collect(Collectors.reducing(0, e -> 1, Integer::sum));
    } catch (IOException e) {
      logger.warn("Faled to read jar cache directory " + jarCacheDirectory);
      throw new UncheckedIOException(e);
    }
  }
}
