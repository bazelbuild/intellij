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

import com.google.auto.value.AutoValue;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import com.google.devtools.intellij.qsync.ArtifactTrackerData;
import com.google.devtools.intellij.qsync.ArtifactTrackerData.TargetToDeps;
import com.google.devtools.intellij.qsync.ArtifactTrackerData.TargetToDirectArtifact;
import com.google.idea.blaze.base.qsync.OutputInfo;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.google.idea.blaze.common.Label;
import com.intellij.openapi.project.Project;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A class that track the artifacts during build and its local copy.
 *
 * <p>In order to keep tracking the artifacts e.g. decide if an artifact has been cached/ get cached
 * files of specific target, we need a map between (top-level target, deps of this target, the
 * artifacts generate by deps directly and its checksum). This information is collected and passed
 * as {@link ArtifactTrackerData.TargetToDeps}. But it's hard to query the information between them.
 * So we convert them into three maps (map from target to its deps, map from deps to its artifact
 * and map from artifact to its checksum) in this class in order to query between them efficiently.
 *
 * <p>These maps reflect the artifacts that have been cached to local successfully. So it may
 * contain out of date targets if its associated artifacts cannot be cached. An exception will be
 * thrown in this case to inform users to trigger another sync and try to re-sync this target again.
 */
public class ArtifactTracker {
  private final SetMultimap<Label, Label> topLevelTargetToDeps = HashMultimap.create();

  private final SetMultimap<Label, String> targetToArtifacts = HashMultimap.create();
  private final JarCache jarCache;

  public ArtifactTracker(Project project) {
    jarCache = new JarCache(getProjectDirectory(project).resolve("libraries"));
    initialize();
  }

  private void initialize() {
    jarCache.initialize();
    // TODO(xinruiy): make it persistent -- should load existing map from disk while initializing
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
   * Merges ArtifactTrackerData.TargetToDeps into tracker maps and cache necessary OutputArtifact to
   * local. The artifacts will not be added into tracker if it's failed to be cached.
   */
  public UpdateResult add(OutputInfo outputInfo) throws IOException {
    ImmutableSet.Builder<Path> updatedBuilder = ImmutableSet.builder();
    ImmutableSet.Builder<String> removedBuilder = ImmutableSet.builder();

    for (ArtifactTrackerData.TargetToDeps artifactInfo : outputInfo.getArtifactInfos()) {
      ImmutableSet<String> removed = ImmutableSet.of();
      try {
        ArtifactDiff diff = diffArtifacts(artifactInfo);
        removed = jarCache.remove(diff.getToRemoveKey());
        removedBuilder.addAll(removed);
        updatedBuilder.addAll(
            jarCache.cache(
                diff.getToUpdateKey().stream()
                    .map(outputInfo::getArtifact)
                    .filter(Objects::nonNull)
                    .collect(toImmutableSet())));
      } finally {
        updateMaps(artifactInfo, removed);
      }
    }
    return UpdateResult.create(updatedBuilder.build(), removedBuilder.build());
  }

  /**
   * Get the difference between newArtifactInfo and the existing one. We store {@link
   * ArtifactTrackerData.TargetToDeps} as three maps in memory to make finding the artifact
   * efficiently, so we pass the map instead of {@link ArtifactTrackerData.TargetToDeps}.The
   * artifact paths are the keys that used to decide if an artifact has been cached.
   */
  public ArtifactDiff diffArtifacts(ArtifactTrackerData.TargetToDeps newArtifactInfo) {
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

  /**
   * Updates the stored map to include new data from {@link ArtifactTrackerData.TargetToDeps} and
   * remove artifacts.
   */
  private void updateMaps(
      ArtifactTrackerData.TargetToDeps artifactInfo, ImmutableSet<String> removedArtifacts) {
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
    topLevelTargetToDeps.clear();
    targetToArtifacts.clear();
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
