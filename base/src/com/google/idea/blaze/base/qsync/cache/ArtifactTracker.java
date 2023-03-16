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

import static com.google.idea.blaze.qsync.project.BlazeProjectDataStorage.AAR_DIRECTORY;
import static com.google.idea.blaze.qsync.project.BlazeProjectDataStorage.GEN_SRC_DIRECTORY;
import static com.google.idea.blaze.qsync.project.BlazeProjectDataStorage.LIBRARY_DIRECTORY;

import com.google.auto.value.AutoValue;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import com.google.devtools.intellij.qsync.ArtifactTrackerData.BuildArtifacts;
import com.google.devtools.intellij.qsync.ArtifactTrackerData.TargetArtifacts;
import com.google.idea.blaze.base.qsync.OutputInfo;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.google.idea.blaze.common.Label;
import com.google.protobuf.ExtensionRegistry;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * A class that track the artifacts during build and its local copy.
 *
 * <p>This class maps all the targets that have been built to their artifacts.
 */
public class ArtifactTracker {

  private static final Logger logger = Logger.getInstance(ArtifactTracker.class);

  private final SetMultimap<Label, Path> artifacts = HashMultimap.create();

  private final JarCache jarCache;
  private final Path persistentFile;

  public ArtifactTracker(Project project, ArtifactFetcher artifactFetcher) {
    jarCache =
        new JarCache(
            getProjectDirectory(project).resolve(LIBRARY_DIRECTORY),
            getExternalAarDirectory(project),
            getProjectDirectory(project).resolve(GEN_SRC_DIRECTORY),
            artifactFetcher);
    persistentFile = getProjectDirectory(project).resolve(".artifact.info");
  }

  public void initialize() {
    jarCache.initialize();
    loadFromDisk();
  }

  private void loadFromDisk() {
    if (!Files.exists(persistentFile)) {
      return;
    }
    artifacts.clear();
    try (InputStream stream = new GZIPInputStream(Files.newInputStream(persistentFile))) {
      BuildArtifacts saved = BuildArtifacts.parseFrom(stream, ExtensionRegistry.getEmptyRegistry());
      for (TargetArtifacts arts : saved.getArtifactsList()) {
        for (String path : arts.getArtifactPathsList()) {
          artifacts.put(Label.of(arts.getTarget()), Path.of(path));
        }
      }
    } catch (IOException e) {
      // TODO: If there is an error parsing the index, reinitialize the cache properly.
    }
  }

  public void saveToDisk() throws IOException {
    BuildArtifacts.Builder builder = BuildArtifacts.newBuilder();
    for (Entry<Label, Collection<Path>> entry : artifacts.asMap().entrySet()) {
      List<String> paths =
          entry.getValue().stream().map(Path::toString).collect(Collectors.toList());
      ;
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

    for (BuildArtifacts artifacts : outputInfo.getArtifacts()) {
      updatedBuilder.addAll(
          jarCache.cache(
              outputInfo.getJars(), outputInfo.getAars(), outputInfo.getGeneratedSources()));
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
      artifacts.removeAll(label);
      if (targets.contains(label)) {
        built.add(label);
      }
    }
    SetView<Label> notBuilt = Sets.difference(targets, built);
    for (Label label : notBuilt) {
      logger.warn("Target " + label + " was not built.");
    }
    for (TargetArtifacts targetArtifacts : newArtifacts.getArtifactsList()) {
      List<Path> paths =
          targetArtifacts.getArtifactPathsList().stream()
              .map(Path::of)
              .collect(Collectors.toList());
      Label label = Label.of(targetArtifacts.getTarget());
      if (targets.contains(label)) {
        artifacts.putAll(label, paths);
      } else {
        // This can happen when there is an alias, we expect the alias name to be built,
        // but we see the 'actual' label being built instead.
        // Here for all the targets that we did not expect to be built, we add their
        // artifacts to all the labels that we didn't see.
        logger.warn("Target " + label + " was unexpectedly built.");
        for (Label notBuiltLabel : notBuilt) {
          artifacts.putAll(notBuiltLabel, paths);
        }
      }
    }
  }

  public void clear() throws IOException {
    artifacts.clear();
    jarCache.clear();
  }

  /** Returns directory of project. */
  private static Path getProjectDirectory(Project project) {
    BlazeImportSettings importSettings =
        BlazeImportSettingsManager.getInstance(project).getImportSettings();

    if (importSettings == null) {
      throw new IllegalArgumentException(
          String.format("Could not get directory for project '%s'", project.getName()));
    }

    return BlazeDataStorage.getProjectDataDir(importSettings).toPath();
  }

  public static Path getExternalAarDirectory(Project project) {
    return getProjectDirectory(project).resolve(AAR_DIRECTORY);
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
