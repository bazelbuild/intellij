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
package com.google.idea.blaze.qsync;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
  abstract ImmutableMap<String, Location> locations();
  /** A set of all the targets that show up in java rules 'src' attributes */
  abstract ImmutableSet<String> javaSources();
  /** A map from a file path to its target */
  abstract ImmutableMap<Path, String> fileToTarget();
  /** From source target to the rule that builds it. If multiple one is picked. */
  abstract ImmutableMap<String, String> sourceOwner();
  /**
   * All the dependencies from source files to things it needs outside the project
   *
   * <p>TODO: this should be moved to a separate class so it's lifecycle is decoupled from the graph
   */
  abstract Map<String, ImmutableSet<String>> transitiveSourceDeps();
  /**
   * All the dependencies of a java rule.
   *
   * <p>Note that we don't use a MultiMap here as that does not allow us to distinguish between a
   * rule with no dependencies vs a rules that does not exist.
   */
  abstract ImmutableMap<String, ImmutableSet<String>> ruleDeps();
  /** All dependencies external to this project */
  abstract ImmutableSet<String> projectDeps();

  abstract ImmutableSet<String> androidTargets();

  static Builder builder() {
    return new AutoValue_BuildGraphData.Builder().transitiveSourceDeps(Maps.newHashMap());
  }

  public static final BuildGraphData EMPTY =
      builder()
          .sourceOwner(ImmutableMap.of())
          .ruleDeps(ImmutableMap.of())
          .projectDeps(ImmutableSet.of())
          .build();

  @AutoValue.Builder
  abstract static class Builder {

    public abstract ImmutableMap.Builder<String, Location> locationsBuilder();

    public abstract ImmutableSet.Builder<String> javaSourcesBuilder();

    public abstract ImmutableMap.Builder<Path, String> fileToTargetBuilder();

    public abstract Builder sourceOwner(Map<String, String> value);

    public abstract Builder transitiveSourceDeps(Map<String, ImmutableSet<String>> value);

    public abstract ImmutableMap.Builder<String, ImmutableSet<String>> ruleDepsBuilder();

    @CanIgnoreReturnValue
    public Builder ruleDeps(Map<String, Set<String>> value) {
      ImmutableMap.Builder<String, ImmutableSet<String>> builder = ruleDepsBuilder();
      for (String key : value.keySet()) {
        builder.put(key, ImmutableSet.copyOf(value.get(key)));
      }
      return this;
    }

    public abstract Builder projectDeps(Set<String> value);

    public abstract ImmutableSet.Builder<String> androidTargetsBuilder();

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

  /** Recursively get all the transitive deps outside the project */
  private ImmutableSet<String> getTargetDependencies(String target) {
    ImmutableSet<String> transitiveDeps = transitiveSourceDeps().get(target);
    if (transitiveDeps != null) {
      return transitiveDeps;
    }
    ImmutableSet.Builder<String> builder = ImmutableSet.builder();
    // There are no cycles in blaze, so we can recursively call down
    if (!ruleDeps().containsKey(target)) {
      builder.add(target);
    } else {
      for (String dep : ruleDeps().get(target)) {
        builder.addAll(getTargetDependencies(dep));
      }
    }
    transitiveDeps = Sets.intersection(builder.build(), projectDeps()).immutableCopy();
    transitiveSourceDeps().put(target, transitiveDeps);
    return transitiveDeps;
  }

  /**
   * Given a path to a file it returns the target that owns the file. Note that in general there
   * could be multiple targets that compile a file, but we try to choose the smallest one, as it
   * would have everything the file needs to be compiled.
   */
  public String getTargetOwner(String path) {
    // TODO make the parameter a Path
    String syncTarget = fileToTarget().get(Path.of(path));
    return sourceOwner().get(syncTarget);
  }

  /**
   * For a given path to a file, returns all the targets outside the project that this file needs to
   * be edited fully.
   */
  @Nullable
  public ImmutableSet<String> getFileDependencies(String path) {
    String target = getTargetOwner(path);
    if (target == null) {
      return null;
    }
    return getTargetDependencies(target);
  }

  /** Returns a list of all the source files of the project, relative to the workspace root. */
  public List<Path> getJavaSourceFiles() {
    List<Path> files = new ArrayList<>();
    for (String src : javaSources()) {
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
    for (String source : javaSources()) {
      String owningTarget = sourceOwner().get(source);
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
