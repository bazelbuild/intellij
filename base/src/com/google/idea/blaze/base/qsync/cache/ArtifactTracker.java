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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import com.google.devtools.intellij.qsync.ArtifactTrackerData.BuildArtifacts;
import com.google.devtools.intellij.qsync.ArtifactTrackerData.TargetArtifacts;
import com.google.idea.blaze.base.qsync.OutputInfo;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.google.idea.blaze.common.Label;
import com.google.protobuf.ExtensionRegistry;
import com.intellij.openapi.diagnostic.Logger;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * A class that track the artifacts during build and its local copy.
 *
 * <p>This class maps all the targets that have been built to their artifacts.
 */
public class ArtifactTracker {

  private static final Logger logger = Logger.getInstance(ArtifactTracker.class);

  // The artifacts in the cache. Note that artifacts that do not produce files are also stored here.
  // So, it is not the same for a label not to be present, than a label to have an empty list.
  private final HashMap<Label, List<Path>> artifacts = new HashMap<>();

  private final FileCache jarCache;
  private final FileCache aarCache;
  private final FileCache generatedSrcFileCache;
  private final Path persistentFile;

  public ArtifactTracker(BlazeImportSettings importSettings, ArtifactFetcher artifactFetcher) {
    jarCache =
        new FileCache(
            /* cacheDir= */ getProjectDirectory(importSettings).resolve(LIBRARY_DIRECTORY),
            /* toCacheFileExtension= */ ImmutableSet.of("jar"),
            /* extractAfterFetch= */ false,
            /* artifactFetcher= */ artifactFetcher);
    aarCache =
        new FileCache(
            /* cacheDir= */ getExternalAarDirectory(importSettings),
            /* toCacheFileExtension= */ ImmutableSet.of("aar"),
            /* extractAfterFetch= */ true,
            /* artifactFetcher= */ artifactFetcher);
    generatedSrcFileCache =
        new FileCache(
            /* cacheDir= */ getProjectDirectory(importSettings).resolve(GEN_SRC_DIRECTORY),
            /* toCacheFileExtension= */ ImmutableSet.of("java", "kt", "srcjar"),
            /* extractAfterFetch= */ true,
            /* artifactFetcher= */ artifactFetcher);
    persistentFile = getProjectDirectory(importSettings).resolve(".artifact.info");
  }

  public void initialize() {
    jarCache.initialize();
    aarCache.initialize();
    generatedSrcFileCache.initialize();
    loadFromDisk();
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

  public void saveToDisk() throws IOException {
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

  /**
   * Merges TargetToDeps into tracker maps and cache necessary OutputArtifact to local. The
   * artifacts will not be added into tracker if it's failed to be cached.
   */
  public UpdateResult add(Set<Label> targets, OutputInfo outputInfo) throws IOException {
    ImmutableSet.Builder<Path> updatedBuilder = ImmutableSet.builder();
    ImmutableSet.Builder<String> removedBuilder = ImmutableSet.builder();

    updatedBuilder
        .addAll(jarCache.cache(outputInfo.getJars()))
        .addAll(aarCache.cache(outputInfo.getAars()))
        .addAll(generatedSrcFileCache.cache(outputInfo.getGeneratedSources()));
    for (BuildArtifacts artifacts : outputInfo.getArtifacts()) {
      updateMaps(targets, artifacts);
    }
    return UpdateResult.create(updatedBuilder.build(), removedBuilder.build());
  }

  /**
   * Updates the index with the newly built targets.
   *
   * @param targets the list of targets that were expected to be built. (From blaze query)
   * @param newArtifacts the artifacts that were actually built. From (blaze build)
   */
  private void updateMaps(Set<Label> targets, BuildArtifacts newArtifacts) {
    Set<Label> built = new HashSet<>();
    for (TargetArtifacts targetArtifacts : newArtifacts.getArtifactsList()) {
      Label label = Label.of(targetArtifacts.getTarget());
      artifacts.remove(label);
      if (targets.contains(label)) {
        built.add(label);
      }
    }
    SetView<Label> notBuilt = Sets.difference(targets, built);
    for (Label label : notBuilt) {
      logger.warn("Target " + label + " was not built.");
    }
    for (TargetArtifacts targetArtifacts : newArtifacts.getArtifactsList()) {
      ImmutableList<Path> paths =
          targetArtifacts.getArtifactPathsList().stream().map(Path::of).collect(toImmutableList());
      Label label = Label.of(targetArtifacts.getTarget());
      if (targets.contains(label)) {
        List<Path> value = artifacts.computeIfAbsent(label, k -> new ArrayList<>());
        value.addAll(paths);
      } else {
        // This can happen when there is an alias, we expect the alias name to be built,
        // but we see the 'actual' label being built instead.
        // Here for all the targets that we did not expect to be built, we add their
        // artifacts to all the labels that we didn't see.
        for (Label notBuiltLabel : notBuilt) {
          List<Path> value = artifacts.computeIfAbsent(notBuiltLabel, k -> new ArrayList<>());
          value.addAll(paths);
        }
      }
    }
  }

  public void clear() throws IOException {
    artifacts.clear();
    jarCache.clear();
    aarCache.clear();
    generatedSrcFileCache.clear();
    saveToDisk();
  }

  /** Returns directory of project. */
  private static Path getProjectDirectory(BlazeImportSettings importSettings) {
    return BlazeDataStorage.getProjectDataDir(importSettings).toPath();
  }

  public static Path getExternalAarDirectory(BlazeImportSettings importSettings) {
    return getProjectDirectory(importSettings).resolve(AAR_DIRECTORY);
  }

  public Set<Label> getCachedTargets() {
    return artifacts.keySet();
  }

  /** A data class representing the result of updating artifacts. */
  @AutoValue
  public abstract static class UpdateResult {
    public abstract ImmutableSet<Path> updatedFiles();

    public abstract ImmutableSet<String> removedKeys();

    public static UpdateResult create(
        ImmutableSet<Path> updatedFiles, ImmutableSet<String> removedKeys) {
      return new AutoValue_ArtifactTracker_UpdateResult(updatedFiles, removedKeys);
    }
  }
}
