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
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.collect.ImmutableSetMultimap.toImmutableSetMultimap;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.common.RuleKinds;
import com.google.idea.blaze.qsync.project.ProjectDefinition.LanguageClass;
import com.google.idea.blaze.qsync.query.PackageSet;
import java.nio.file.Path;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
  public abstract PackageSet packages();

  /** A map from a file path to its target */
  abstract ImmutableMap<Path, Label> fileToTarget();

  /** All dependencies external to this project */
  public abstract ImmutableSet<Label> projectDeps();

  public abstract TargetTree allTargets();

  abstract ImmutableSet<Label> androidTargets();

  public abstract ImmutableMap<Label, ProjectTarget> targetMap();

  /**
   * All in-project targets with a direct compile or runtime dependency on a specified target, which
   * may be external.
   */
  @Memoized
  ImmutableMultimap<Label, Label> reverseDeps() {
    ImmutableMultimap.Builder<Label, Label> map = ImmutableMultimap.builder();
    for (ProjectTarget t : targetMap().values()) {
      for (Label dep : t.deps()) {
        map.put(dep, t.label());
      }
      for (Label runtimeDep : t.runtimeDeps()) {
        map.put(runtimeDep, t.label());
      }
    }
    return map.build();
  }

  /**
   * Calculates the set of direct reverse dependencies for a set of targets (including the targets
   * themselves).
   */
  public ImmutableSet<Label> getSameLanguageTargetsDependingOn(Set<Label> targets) {
    ImmutableMultimap<Label, Label> rdeps = reverseDeps();
    ImmutableSet.Builder<Label> directRdeps = ImmutableSet.builder();
    directRdeps.addAll(targets);
    for (Label target : targets) {
      ImmutableSet<LanguageClass> targetLanguages = targetMap().get(target).languages();
      // filter the rdeps based on the languages, removing those that don't have a common
      // language. This ensures we don't follow reverse deps of (e.g.) a java target depending on
      // a cc target.
      rdeps.get(target).stream()
          .filter(d -> !Collections.disjoint(targetMap().get(d).languages(), targetLanguages))
          .forEach(directRdeps::add);
    }
    return directRdeps.build();
  }

  /**
   * Returns all in project targets that depend on the source file at {@code sourcePath} via an
   * in-project dependency chain. Used to determine possible test targets for a given file.
   *
   * <p>If project target A depends on external target B, and external target B depends on project
   * target C, target A is *not* included in {@code getReverseDeps} for a source file in target C.
   */
  public Collection<ProjectTarget> getReverseDepsForSource(Path sourcePath) {

    ImmutableSet<Label> targetOwners = getTargetOwners(sourcePath);

    if (targetOwners == null || targetOwners.isEmpty()) {
      return ImmutableList.of();
    }

    Queue<Label> toVisit = Queues.newArrayDeque(targetOwners);
    Set<Label> visited = Sets.newHashSet();

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

  public ImmutableSet<Path> getTargetSources(Label target) {
    return Optional.ofNullable(targetMap().get(target)).stream()
        .map(ProjectTarget::sourceLabels)
        .flatMap(Set::stream)
        .map(locations()::get)
        .map(l -> l.file)
        .collect(toImmutableSet());
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
      builder().projectDeps(ImmutableSet.of()).packages(PackageSet.EMPTY).build();

  /** Builder for {@link BuildGraphData}. */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract ImmutableMap.Builder<Label, Location> locationsBuilder();

    public abstract ImmutableSet.Builder<Label> javaSourcesBuilder();

    public abstract ImmutableMap.Builder<Path, Label> fileToTargetBuilder();

    public abstract ImmutableMap.Builder<Label, ProjectTarget> targetMapBuilder();

    public abstract Builder projectDeps(Set<Label> value);

    public abstract TargetTree.Builder allTargetsBuilder();

    public abstract ImmutableSet.Builder<Label> androidTargetsBuilder();

    public abstract Builder packages(PackageSet value);

    abstract BuildGraphData autoBuild();

    public final BuildGraphData build() {
      BuildGraphData result = autoBuild();
      // these are memoized, but we choose to pay the cost of building it now so that it's done at
      // sync time rather than later on.
      ImmutableSetMultimap<Label, Label> unused = result.sourceOwners();
      ImmutableMultimap<Label, Label> unused2 = result.reverseDeps();
      return result;
    }
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

  public ImmutableSet<Label> getTransitiveExternalDependencies(Label target) {
    return transitiveDeps.getUnchecked(target);
  }

  private ImmutableSet<Label> calculateTransitiveExternalDependencies(Label target) {
    ImmutableSet.Builder<Label> builder = ImmutableSet.builder();
    // There are no cycles in blaze, so we can recursively call down
    if (!targetMap().containsKey(target)) {
      builder.add(target);
    } else {
      if (projectDeps().contains(target)) {
        builder.add(target);
      }
      for (Label dep : targetMap().get(target).deps()) {
        builder.addAll(getTransitiveExternalDependencies(dep));
      }
    }
    return Sets.intersection(builder.build(), projectDeps()).immutableCopy();
  }

  @Memoized
  public ImmutableSetMultimap<Label, Label> sourceOwners() {
    return targetMap().values().stream()
        .flatMap(t -> t.sourceLabels().stream().map(src -> new SimpleEntry<>(src, t.label())))
        .collect(toImmutableSetMultimap(e -> e.getKey(), e -> e.getValue()));
  }

  @Nullable
  public ImmutableSet<Label> getTargetOwners(Path path) {
    Label syncTarget = fileToTarget().get(path);
    return sourceOwners().get(syncTarget);
  }

  /**
   * @deprecated Choosing a target based on the number of deps it has is not a good strategy, as we
   *     could end up selecting one that doesn't build in the current config. Allow the user to
   *     choose, or require the projects source -> target mapping to be unambiguous instead.
   */
  @Deprecated
  @Nullable
  public Label selectLabelWithLeastDeps(Collection<Label> candidates) {
    return candidates.stream()
        .min(Comparator.comparingInt(label -> targetMap().get(label).deps().size()))
        .orElse(null);
  }

  @VisibleForTesting
  @Nullable
  ImmutableSet<Label> getFileDependencies(Path path) {
    ImmutableSet<Label> targets = getTargetOwners(path);
    if (targets == null) {
      return null;
    }
    return targets.stream()
        .map(this::getTransitiveExternalDependencies)
        .flatMap(Set::stream)
        .collect(toImmutableSet());
  }

  /** Returns a list of all the java source files of the project, relative to the workspace root. */
  public List<Path> getJavaSourceFiles() {
    return pathListFromLabels(javaSources());
  }

  /**
   * Returns a list of all the proto source files of the project, relative to the workspace root.
   */
  @Memoized
  public List<Path> getProtoSourceFiles() {
    return pathListFromLabels(protoSources());
  }

  private ImmutableSet<Label> protoSources() {
    return targetMap().values().stream()
        .filter(t -> RuleKinds.PROTO_SOURCE_RULE_KINDS.contains(t.kind()))
        .flatMap(t -> t.sourceLabels().stream())
        .collect(toImmutableSet());
  }

  private List<Path> pathListFromLabels(Collection<Label> labels) {
    List<Path> paths = new ArrayList<>();
    for (Label src : labels) {
      Location location = locations().get(src);
      if (location == null) {
        continue;
      }
      paths.add(location.file);
    }
    return paths;
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
      for (Label owningTarget : sourceOwners().get(source)) {
        if (androidTargets().contains(owningTarget)) {
          Location location = locations().get(source);
          if (location == null) {
            continue;
          }
          files.add(location.file);
        }
      }
    }
    return files;
  }

  /** Returns a list of custom_package fields that used by current project. */
  public ImmutableSet<String> getAllCustomPackages() {
    return targetMap().values().stream()
        .map(ProjectTarget::customPackage)
        .flatMap(Optional::stream)
        .collect(toImmutableSet());
  }
}
