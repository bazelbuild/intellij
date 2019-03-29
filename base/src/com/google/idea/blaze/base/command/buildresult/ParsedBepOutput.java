/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.command.buildresult;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Queues;
import com.google.common.collect.SetMultimap;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.BuildEventId.NamedSetOfFilesId;
import com.google.idea.blaze.base.model.primitives.Label;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * An intermediate data class representing blaze's build event protocol (BEP) output for a build.
 */
final class ParsedBepOutput {

  static ParsedBepOutput parseBepArtifacts(InputStream bepStream) throws IOException {
    BuildEventStreamProtos.BuildEvent event;
    Map<String, String> configIdToMnemonic = new HashMap<>();
    Map<String, BuildEventStreamProtos.NamedSetOfFiles> fileSets = new LinkedHashMap<>();
    Map<String, String> fileSetConfigs = new HashMap<>();
    ImmutableSetMultimap.Builder<String, String> outputGroupToFileSets =
        ImmutableSetMultimap.builder();
    ImmutableSetMultimap.Builder<String, String> targetToFileSets = ImmutableSetMultimap.builder();
    long startTimeMillis = 0L;

    while ((event = BuildEventStreamProtos.BuildEvent.parseDelimitedFrom(bepStream)) != null) {
      switch (event.getId().getIdCase()) {
        case CONFIGURATION:
          configIdToMnemonic.put(
              event.getId().getConfiguration().getId(), event.getConfiguration().getMnemonic());
          continue;
        case NAMED_SET:
          fileSets.put(event.getId().getNamedSet().getId(), event.getNamedSetOfFiles());
          continue;
        case TARGET_COMPLETED:
          String label = event.getId().getTargetCompleted().getLabel();
          String configMnemonic =
              configIdToMnemonic.get(event.getId().getTargetCompleted().getConfiguration().getId());
          event
              .getCompleted()
              .getOutputGroupList()
              .forEach(
                  o -> {
                    List<String> sets = getFileSets(o);
                    outputGroupToFileSets.putAll(o.getName(), sets);
                    targetToFileSets.putAll(label, sets);
                    sets.forEach(s -> fileSetConfigs.put(s, configMnemonic));
                  });
          continue;
        case STARTED:
          startTimeMillis = event.getStarted().getStartTimeMillis();
          continue;
        default: // continue
      }
    }

    Map<String, FileSet> filesMap = new LinkedHashMap<>();
    fileSets.forEach((id, files) -> filesMap.put(id, new FileSet(files, fileSetConfigs.get(id))));
    return new ParsedBepOutput(
        startTimeMillis, filesMap, outputGroupToFileSets.build(), targetToFileSets.build());
  }

  private static List<String> getFileSets(BuildEventStreamProtos.OutputGroup group) {
    return group.getFileSetsList().stream()
        .map(NamedSetOfFilesId::getId)
        .collect(Collectors.toList());
  }

  private static class FileSet {
    private final BuildEventStreamProtos.NamedSetOfFiles namedSet;
    private final String configuration;

    FileSet(BuildEventStreamProtos.NamedSetOfFiles namedSet, String configuration) {
      this.namedSet = namedSet;
      this.configuration = configuration;
    }
  }

  /** The start time of the build, in milliseconds since the epoch. */
  private final long startTimeMillis;

  /** A map from file set ID to file set. */
  private final Map<String, FileSet> fileSets;

  /** A map from each output group to its direct set of NamedSetOfFiles */
  private final SetMultimap<String, String> outputGroupFileSets;

  /** The set of named file sets directly produced by each target. */
  private final SetMultimap<String, String> targetFileSets;

  private ParsedBepOutput(
      long startTimeMillis,
      Map<String, FileSet> fileSets,
      ImmutableSetMultimap<String, String> outputGroupFileSets,
      ImmutableSetMultimap<String, String> targetFileSets) {
    this.startTimeMillis = startTimeMillis;
    this.fileSets = fileSets;
    this.outputGroupFileSets = outputGroupFileSets;
    this.targetFileSets = targetFileSets;
  }

  /** Returns all output artifacts of the build. */
  ImmutableSet<OutputArtifact> getAllOutputArtifacts(Predicate<String> pathFilter) {
    ImmutableSet.Builder<OutputArtifact> outputs = ImmutableSet.builder();
    fileSets.values().forEach(s -> outputs.addAll(parseFiles(s, pathFilter)));
    return outputs.build();
  }

  /** Returns the set of artifacts directly produced by the given target. */
  ImmutableSet<OutputArtifact> getArtifactsForTarget(Label label, Predicate<String> pathFilter) {
    ImmutableSet.Builder<OutputArtifact> outputs = ImmutableSet.builder();
    Set<String> setIds = targetFileSets.get(label.toString());
    setIds.forEach(s -> outputs.addAll(parseFiles(fileSets.get(s), pathFilter)));
    return outputs.build();
  }

  /** Returns the set of artifacts in the given output groups. */
  ImmutableListMultimap<String, OutputArtifact> getPerOutputGroupArtifacts(
      Predicate<String> pathFilter) {
    ImmutableListMultimap.Builder<String, OutputArtifact> builder = ImmutableListMultimap.builder();
    for (String group : outputGroupFileSets.keySet()) {
      Set<String> directSetIds = outputGroupFileSets.get(group);
      builder.putAll(group, traverseFileSetsTransitively(directSetIds, pathFilter));
    }
    return builder.build();
  }

  /**
   * Finds transitive closure of all files in the given file sets (traversing child filesets
   * transitively).
   */
  private ImmutableSet<OutputArtifact> traverseFileSetsTransitively(
      Set<String> fileSetsToVisit, Predicate<String> pathFilter) {
    Queue<String> toVisit = Queues.newArrayDeque();
    ImmutableSet.Builder<OutputArtifact> allFiles = ImmutableSet.builder();
    Set<String> visited = new HashSet<>();
    toVisit.addAll(fileSetsToVisit);
    visited.addAll(fileSetsToVisit);
    while (!toVisit.isEmpty()) {
      String setId = toVisit.remove();
      FileSet fileSet = fileSets.get(setId);
      allFiles.addAll(parseFiles(fileSet, pathFilter));
      Set<String> children =
          fileSet.namedSet.getFileSetsList().stream()
              .map(NamedSetOfFilesId::getId)
              .filter(s -> !visited.contains(s))
              .collect(toImmutableSet());
      visited.addAll(children);
      toVisit.addAll(children);
    }
    return allFiles.build();
  }

  private ImmutableList<OutputArtifact> parseFiles(FileSet set, Predicate<String> pathFilter) {
    return set.namedSet.getFilesList().stream()
        .map(
            f ->
                OutputArtifactParser.parseArtifact(
                    f, set.configuration, pathFilter, startTimeMillis))
        .filter(Objects::nonNull)
        .collect(toImmutableList());
  }
}
