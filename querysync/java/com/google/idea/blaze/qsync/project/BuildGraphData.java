/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.qsync.project;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.idea.blaze.common.BuildTarget;
import com.google.idea.blaze.common.Label;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * The build graph of all the rules that make up the project.
 *
 * <p>This class is immutable. A new instance of it will be created every time there is any change
 * to the project structure.
 */
@AutoValue
public abstract class BuildGraphData {

  /** A map from target to file on disk for all source files */
  public abstract ImmutableMap<Label, Location> locations();
  /** A set of all the targets that show up in java rules 'src' attributes */
  public abstract ImmutableSet<Label> javaSources();
  /** A set of all the BUILD files */
  public abstract ImmutableSet<Path> packages();
  /** A map from a file path to its target */
  abstract ImmutableMap<Path, Label> fileToTarget();
  /** From source target to the rule that builds it. If multiple one is picked. */
  abstract ImmutableMap<Label, Label> sourceOwner();
  /**
   * All the dependencies of a java rule.
   *
   * <p>Note that we don't use a MultiMap here as that does not allow us to distinguish between a
   * rule with no dependencies vs a rules that does not exist.
   */
  abstract ImmutableMap<Label, ImmutableSet<Label>> ruleDeps();
  /**
   * All the runtime dependencies of a java rule.
   *
   * <p>Note that we don't use a MultiMap here as that does not allow us to distinguish between a
   * rule with no runtime dependencies vs a rules that does not exist.
   */
  abstract ImmutableMap<Label, ImmutableSet<Label>> ruleRuntimeDeps();
  /** All dependencies external to this project */
  public abstract ImmutableSet<Label> projectDeps();

  abstract ImmutableSet<Label> androidTargets();

  abstract ImmutableMap<Label, BuildTarget> targetMap();

  /**
   * All in-project targets with a direct compile or runtime dependency on a specified target, which
   * may be external.
   */
  abstract ImmutableMultimap<Label, Label> reverseDeps();

  /**
   * Returns all in project targets that depend on the source file at {@code sourcePath} via an
   * in-project dependency chain. Used to determine possible test targets for a given file.
   *
   * <p>If project target A depends on external target B, and external target B depends on project
   * target C, target A is *not* included in {@code getReverseDeps} for a source file in target C.
   */
  public Collection<BuildTarget> getReverseDepsForSource(Path sourcePath) {

    Label targetOwner = getTargetOwner(sourcePath);

    if (targetOwner == null) {
      return ImmutableList.of();
    }

    Queue<Label> toVisit = Queues.newArrayDeque();
    Set<Label> visited = Sets.newHashSet();

    toVisit.add(targetOwner);
    while (!toVisit.isEmpty()) {
      Label next = toVisit.remove();
      if (visited.add(next)) {
        toVisit.addAll(reverseDeps().get(next));
      }
    }

    return visited.stream()
        .map(label -> targetMap().get(label))
        .filter(Objects::nonNull)
        .collect(toImmutableList());
  }

  @Override
  public final String toString() {
    // The default autovalue toString() implementation can result in a very large string which
    // chokes the debugger.
    return getClass().getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(this));
  }

  public static Builder builder() {
    return new AutoValue_BuildGraphData.Builder();
  }

  @VisibleForTesting
  public static final BuildGraphData EMPTY =
      builder()
          .sourceOwner(ImmutableMap.of())
          .ruleDeps(ImmutableMap.of())
          .projectDeps(ImmutableSet.of())
          .packages(ImmutableSet.of())
          .build();

  /** Builder for {@link BuildGraphData}. */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract ImmutableMap.Builder<Label, Location> locationsBuilder();

    public abstract ImmutableSet.Builder<Label> javaSourcesBuilder();

    public abstract ImmutableMap.Builder<Path, Label> fileToTargetBuilder();

    public abstract Builder sourceOwner(Map<Label, Label> value);

    public abstract ImmutableMap.Builder<Label, ImmutableSet<Label>> ruleDepsBuilder();

    public abstract ImmutableMap.Builder<Label, ImmutableSet<Label>> ruleRuntimeDepsBuilder();

    public abstract ImmutableMap.Builder<Label, BuildTarget> targetMapBuilder();

    public abstract ImmutableMultimap.Builder<Label, Label> reverseDepsBuilder();

    @CanIgnoreReturnValue
    public Builder ruleDeps(Map<Label, Set<Label>> value) {
      ImmutableMap.Builder<Label, ImmutableSet<Label>> builder = ruleDepsBuilder();
      for (Label key : value.keySet()) {
        builder.put(key, ImmutableSet.copyOf(value.get(key)));
      }
      return this;
    }

    @CanIgnoreReturnValue
    public Builder ruleRuntimeDeps(Map<Label, Set<Label>> value) {
      ImmutableMap.Builder<Label, ImmutableSet<Label>> builder = ruleRuntimeDepsBuilder();
      for (Label key : value.keySet()) {
        builder.put(key, ImmutableSet.copyOf(value.get(key)));
      }
      return this;
    }

    @CanIgnoreReturnValue
    public Builder reverseDeps(Multimap<Label, Label> value) {
      ImmutableMultimap.Builder<Label, Label> builder = reverseDepsBuilder();
      for (Label key : value.keySet()) {
        builder.putAll(key, value.get(key));
      }
      return this;
    }

    public abstract Builder projectDeps(Set<Label> value);

    public abstract ImmutableSet.Builder<Label> androidTargetsBuilder();

    public abstract Builder packages(Set<Path> value);

    public abstract BuildGraphData build();
  }

  /** Represents a location on a file. */
  public static class Location {

    private static final Pattern PATTERN = Pattern.compile("(.*):(\\d+):(\\d+)");

    public final Path file; // Relative to workspace root
    public final int row;
    public final int column;

    /**
     * @param location A location as provided by bazel, i.e. {@code path/to/file:lineno:columnno}
     */
    public Location(String location) {
      Matcher matcher = PATTERN.matcher(location);
      Preconditions.checkArgument(matcher.matches(), "Location not recognized: %s", location);
      file = Path.of(matcher.group(1));
      Preconditions.checkState(
          !file.startsWith("/"),
          "Filename starts with /: ensure that "
              + "`--relative_locations=true` was specified in the query invocation.");
      row = Integer.parseInt(matcher.group(2));
      column = Integer.parseInt(matcher.group(3));
    }
  }

  private final LoadingCache<Label, ImmutableSet<Label>> transitiveDeps =
      CacheBuilder.newBuilder()
          .build(CacheLoader.from(this::calculateTransitiveExternalDependencies));

  private ImmutableSet<Label> getTransitiveExternalDependencies(Label target) {
    return transitiveDeps.getUnchecked(target);
  }

  private ImmutableSet<Label> calculateTransitiveExternalDependencies(Label target) {
    ImmutableSet.Builder<Label> builder = ImmutableSet.builder();
    // There are no cycles in blaze, so we can recursively call down
    if (!ruleDeps().containsKey(target)) {
      builder.add(target);
    } else {
      if (projectDeps().contains(target)) {
        builder.add(target);
      }
      for (Label dep : ruleDeps().get(target)) {
        builder.addAll(getTransitiveExternalDependencies(dep));
      }
    }
    return Sets.intersection(builder.build(), projectDeps()).immutableCopy();
  }

  public Label getTargetOwner(Path path) {
    Label syncTarget = fileToTarget().get(path);
    return sourceOwner().get(syncTarget);
  }

  @Nullable
  ImmutableSet<Label> getFileDependencies(Path path) {
    Label target = getTargetOwner(path);
    if (target == null) {
      return null;
    }
    return getTransitiveExternalDependencies(target);
  }

  /** Returns a list of all the source files of the project, relative to the workspace root. */
  public List<Path> getJavaSourceFiles() {
    List<Path> files = new ArrayList<>();
    for (Label src : javaSources()) {
      Location location = locations().get(src);
      if (location == null) {
        continue;
      }
      files.add(location.file);
    }
    return files;
  }

  public List<Path> getAllSourceFiles() {
    List<Path> files = new ArrayList<>();
    files.addAll(fileToTarget().keySet());
    return files;
  }

  /** Returns a list of source files owned by an Android target, relative to the workspace root. */
  public List<Path> getAndroidSourceFiles() {
    List<Path> files = new ArrayList<>();
    for (Label source : javaSources()) {
      Label owningTarget = sourceOwner().get(source);
      if (androidTargets().contains(owningTarget)) {
        Location location = locations().get(source);
        if (location == null) {
          continue;
        }
        files.add(location.file);
      }
    }
    return files;
  }
}
