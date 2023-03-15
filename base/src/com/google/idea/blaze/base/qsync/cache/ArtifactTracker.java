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

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.idea.blaze.qsync.project.BlazeProjectDataStorage.AAR_DIRECTORY;
import static com.google.idea.blaze.qsync.project.BlazeProjectDataStorage.GEN_SRC_DIRECTORY;
import static com.google.idea.blaze.qsync.project.BlazeProjectDataStorage.LIBRARY_DIRECTORY;

import com.google.auto.value.AutoValue;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import com.google.devtools.intellij.qsync.ArtifactTrackerData.TargetToDeps;
import com.google.devtools.intellij.qsync.ArtifactTrackerData.TargetToDepsList;
import com.google.devtools.intellij.qsync.ArtifactTrackerData.TargetToDirectArtifact;
import com.google.idea.blaze.base.qsync.OutputInfo;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.google.idea.blaze.common.Label;
import com.google.protobuf.ExtensionRegistry;
import com.intellij.openapi.project.Project;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * A class that track the artifacts during build and its local copy.
 *
 * <p>In order to keep tracking the artifacts e.g. decide if an artifact has been cached/ get cached
 * files of specific target, we need a map between (top-level target, deps of this target, the
 * artifacts generate by deps directly and its checksum). This information is collected and passed
 * as {@link TargetToDeps}. But it's hard to query the information between them. So we convert them
 * into three maps (map from target to its deps, map from deps to its artifact and map from artifact
 * to its checksum) in this class in order to query between them efficiently.
 *
 * <p>These maps reflect the artifacts that have been cached to local successfully. So it may
 * contain out of date targets if its associated artifacts cannot be cached. An exception will be
 * thrown in this case to inform users to trigger another sync and try to re-sync this target again.
 */
public class ArtifactTracker {
  private final SetMultimap<Label, Label> topLevelTargetToDeps = HashMultimap.create();
  private final SetMultimap<Label, String> targetToArtifacts = HashMultimap.create();

  private final JarCache jarCache;
  private final Path persistentFile;

  public ArtifactTracker(Project project) {
    jarCache =
        new JarCache(
            getProjectDirectory(project).resolve(LIBRARY_DIRECTORY),
            getExternalAarDirectory(project),
            getProjectDirectory(project).resolve(GEN_SRC_DIRECTORY));
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
    cleanMaps();
    try (InputStream stream = new GZIPInputStream(Files.newInputStream(persistentFile))) {
      TargetToDepsList targetToDepsList =
          TargetToDepsList.parseFrom(stream, ExtensionRegistry.getEmptyRegistry());
      for (TargetToDeps targetToDeps : targetToDepsList.getTargetToDepsList()) {
        for (TargetToDirectArtifact dep : targetToDeps.getDepsList()) {
          String depTarget = dep.getTarget();
          topLevelTargetToDeps.put(Label.of(targetToDeps.getTarget()), Label.of(depTarget));
          targetToArtifacts.putAll(Label.of(depTarget), dep.getArtifactPathsList());
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public void saveToDisk() throws IOException {
    TargetToDepsList.Builder targetToDepsList = TargetToDepsList.newBuilder();
    for (Label topLevelTarget : topLevelTargetToDeps.keys()) {
      TargetToDeps.Builder targetToDeps = TargetToDeps.newBuilder();
      targetToDeps.setTarget(topLevelTarget.toString());
      for (Label dep : topLevelTargetToDeps.get(topLevelTarget)) {
        targetToDeps.addDeps(
            TargetToDirectArtifact.newBuilder()
                .addAllArtifactPaths(targetToArtifacts.get(dep))
                .setTarget(dep.toString())
                .build());
      }
      targetToDepsList.addTargetToDeps(targetToDeps);
    }
    try (OutputStream stream = new GZIPOutputStream(Files.newOutputStream(persistentFile))) {
      targetToDepsList.build().writeTo(stream);
    }
  }

  private void cleanMaps() {
    topLevelTargetToDeps.clear();
    targetToArtifacts.clear();
  }

  /** Returns all local copy of artifacts that are needed by a top-level target. */
  public ImmutableSet<Path> get(Label toplevelTarget) {
    ImmutableSet.Builder<Path> result = ImmutableSet.builder();
    for (Label dep : topLevelTargetToDeps.get(toplevelTarget)) {
      for (String artifact : targetToArtifacts.get(dep)) {
        Optional<Path> cachedFile = jarCache.get(artifact);
        if (cachedFile.isPresent()) {
          result.add(cachedFile.get());
        }
      }
    }
    return result.build();
  }

  /**
   * Merges TargetToDeps into tracker maps and cache necessary OutputArtifact to local. The
   * artifacts will not be added into tracker if it's failed to be cached.
   */
  public UpdateResult add(OutputInfo outputInfo) throws IOException {
    ImmutableSet.Builder<Path> updatedBuilder = ImmutableSet.builder();
    ImmutableSet.Builder<String> removedBuilder = ImmutableSet.builder();

    for (TargetToDeps artifactInfo : outputInfo.getArtifactInfos()) {
      ImmutableSet<String> removed = ImmutableSet.of();
      try {
        ArtifactDiff diff = diffArtifacts(artifactInfo);
        removed = jarCache.remove(diff.getToRemoveKey());
        removedBuilder.addAll(removed);

        ImmutableSet<String> artifactsToUpdate = diff.getToUpdateKey();
        updatedBuilder.addAll(
            jarCache.cache(
                outputInfo.getJars().stream()
                    .filter(jar -> artifactsToUpdate.contains(jar.getKey()))
                    .collect(toImmutableSet()),
                outputInfo.getAars().stream()
                    .filter(aar -> artifactsToUpdate.contains(aar.getKey()))
                    .collect(toImmutableSet()),
                outputInfo.getGeneratedSources().stream()
                    .filter(source -> artifactsToUpdate.contains(source.getKey()))
                    .collect(toImmutableSet())));
      } finally {
        updateMaps(artifactInfo, removed);
      }
    }
    return UpdateResult.create(updatedBuilder.build(), removedBuilder.build());
  }

  /**
   * Get the difference between newArtifactInfo and the existing one. We store {@link TargetToDeps}
   * as three maps in memory to make finding the artifact efficiently, so we pass the map instead of
   * {@link TargetToDeps}.The artifact paths are the keys that used to decide if an artifact has
   * been cached.
   */
  public ArtifactDiff diffArtifacts(TargetToDeps newArtifactInfo) {
    Label topLevelTarget = Label.of(newArtifactInfo.getTarget());
    List<TargetToDirectArtifact> newDeps = newArtifactInfo.getDepsList();
    SetMultimap<Label, Label> depsToTopLevelTarget =
        Multimaps.invertFrom(topLevelTargetToDeps, HashMultimap.create());
    ImmutableSet.Builder<String> toUpdateArtifacts = ImmutableSet.builder();
    Set<Label> toRemoveTargets = new HashSet<>();

    // The target that only used by current top level target, it may be removed if it's not used
    // in new build. Collect it here and check if it's used by newArtifactInfo in next step.
    for (Label dep : topLevelTargetToDeps.get(topLevelTarget)) {
      //  The target that only used by current top level target
      if (depsToTopLevelTarget.get(dep).size() == 1) {
        toRemoveTargets.add(dep);
      }
    }

    for (TargetToDirectArtifact dep : newDeps) {
      Label target = Label.of(dep.getTarget());
      List<String> artifactPathsList = dep.getArtifactPathsList();
      toRemoveTargets.remove(target);
      Set<String> oldArtifactPathsList = targetToArtifacts.get(target);
      for (String artifact : artifactPathsList) {
        if (!oldArtifactPathsList.contains(artifact)) {
          toUpdateArtifacts.add(artifact);
        }
      }
    }
    return ArtifactDiff.create(
        toUpdateArtifacts.build(),
        toRemoveTargets.stream()
            .flatMap(target -> targetToArtifacts.get(target).stream())
            .collect(toImmutableSet()));
  }

  /** Updates the stored map to include new data from {@link TargetToDeps} and remove artifacts. */
  private void updateMaps(TargetToDeps artifactInfo, ImmutableSet<String> removedArtifacts) {
    Label topLevelTarget = Label.of(artifactInfo.getTarget());
    List<TargetToDirectArtifact> newDeps = artifactInfo.getDepsList();
    SetMultimap<Label, Label> depsToTopLevelTarget =
        Multimaps.invertFrom(topLevelTargetToDeps, HashMultimap.create());
    SetMultimap<String, Label> artifactsToTargets =
        Multimaps.invertFrom(targetToArtifacts, HashMultimap.create());

    for (TargetToDirectArtifact dep : newDeps) {
      Label target = Label.of(dep.getTarget());
      targetToArtifacts.putAll(target, dep.getArtifactPathsList());
      topLevelTargetToDeps.put(topLevelTarget, target);
    }

    for (String artifact : removedArtifacts) {
      for (Label dep : artifactsToTargets.get(artifact)) {
        targetToArtifacts.remove(dep, artifact);
        if (!targetToArtifacts.containsKey(dep)) {
          for (Label target : depsToTopLevelTarget.get(dep)) {
            topLevelTargetToDeps.remove(target, dep);
          }
        }
      }
    }
  }

  public void clear() throws IOException {
    cleanMaps();
    jarCache.cleanupCacheDir();
  }

  /**
   * Removes artifacts that used by topLevelTarget from cache. Even if other top level targets
   * depend on them, these artifacts and their direct targets will be removed from the map.
   */
  public void removeDepsOf(Label topLevelTarget) throws IOException {
    // TODO(xinruiy): remove this function if it's not used in the future
    Set<String> toRemoveArtifacts = new HashSet<>();
    for (Label dep : topLevelTargetToDeps.get(topLevelTarget)) {
      toRemoveArtifacts.addAll(targetToArtifacts.get(dep));
    }
    updateMaps(
        TargetToDeps.newBuilder().setTarget(topLevelTarget.toString()).build(),
        jarCache.remove(toRemoveArtifacts));
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
