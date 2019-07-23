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
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.NamedSetOfFiles;
import com.google.idea.blaze.base.model.primitives.Label;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
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

/** A data class representing blaze's build event protocol (BEP) output for a build. */
public final class ParsedBepOutput {

  static ParsedBepOutput parseBepArtifacts(InputStream bepStream) throws IOException {
    BuildEventStreamProtos.BuildEvent event;
    Map<String, String> configIdToMnemonic = new HashMap<>();
    Map<String, NamedSetOfFiles> fileSets = new LinkedHashMap<>();
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
    fillInTransitiveFileSetConfigs(fileSets, fileSetConfigs);
    Map<String, FileSet> filesMap = new LinkedHashMap<>();
    final long startTimeMs = startTimeMillis;
    fileSets.forEach(
        (id, files) -> filesMap.put(id, new FileSet(files, fileSetConfigs.get(id), startTimeMs)));
    return new ParsedBepOutput(filesMap, outputGroupToFileSets.build(), targetToFileSets.build());
  }

  private static List<String> getFileSets(BuildEventStreamProtos.OutputGroup group) {
    return group.getFileSetsList().stream()
        .map(NamedSetOfFilesId::getId)
        .collect(Collectors.toList());
  }

  /**
   * BEP explicitly lists the configuration mnemonics of top-level file sets. This method fills in a
   * file set ID to mnemonic map for the transitive closure.
   */
  private static void fillInTransitiveFileSetConfigs(
      Map<String, NamedSetOfFiles> fileSets, Map<String, String> fileSetConfigs) {
    Queue<String> toVisit = Queues.newArrayDeque();
    toVisit.addAll(fileSetConfigs.keySet());
    while (!toVisit.isEmpty()) {
      String setId = toVisit.remove();
      String config = fileSetConfigs.get(setId);
      fileSets.get(setId).getFileSetsList().stream()
          .map(NamedSetOfFilesId::getId)
          .filter(s -> !fileSetConfigs.containsKey(s))
          .forEach(
              child -> {
                fileSetConfigs.put(child, config);
                toVisit.add(child);
              });
    }
  }

  private static class FileSet {
    private final NamedSetOfFiles namedSet;
    private final ImmutableList<OutputArtifact> parsedOutputs;

    FileSet(NamedSetOfFiles namedSet, String configuration, long startTimeMillis) {
      this.namedSet = namedSet;
      this.parsedOutputs = parseFiles(namedSet, configuration, startTimeMillis);
    }
  }

  /** A map from file set ID to file set. */
  private final Map<String, FileSet> fileSets;

  /** A map from each output group to its direct set of NamedSetOfFiles */
  private final SetMultimap<String, String> outputGroupFileSets;

  /** The set of named file sets directly produced by each target. */
  private final SetMultimap<String, String> targetFileSets;

  private ParsedBepOutput(
      Map<String, FileSet> fileSets,
      ImmutableSetMultimap<String, String> outputGroupFileSets,
      ImmutableSetMultimap<String, String> targetFileSets) {
    this.fileSets = fileSets;
    this.outputGroupFileSets = outputGroupFileSets;
    this.targetFileSets = targetFileSets;
  }

  /** Returns all output artifacts of the build. */
  public ImmutableSet<OutputArtifact> getAllOutputArtifacts(Predicate<String> pathFilter) {
    return fileSets.values().stream()
        .map(s -> s.parsedOutputs)
        .map(list -> filter(list, pathFilter))
        .flatMap(List::stream)
        .collect(toImmutableSet());
  }

  /** Returns the set of artifacts directly produced by the given target. */
  public ImmutableSet<OutputArtifact> getDirectArtifactsForTarget(
      Label label, Predicate<String> pathFilter) {
    return targetFileSets.get(label.toString()).stream()
        .map(s -> fileSets.get(s).parsedOutputs)
        .map(list -> filter(list, pathFilter))
        .flatMap(List::stream)
        .collect(toImmutableSet());
  }

  /** Returns the artifacts in the given output groups. */
  public ImmutableListMultimap<String, OutputArtifact> getPerOutputGroupArtifacts(
      Predicate<String> pathFilter) {
    ImmutableListMultimap.Builder<String, OutputArtifact> builder = ImmutableListMultimap.builder();
    for (String group : outputGroupFileSets.keySet()) {
      Set<String> directSetIds = outputGroupFileSets.get(group);
      builder.putAll(group, traverseFileSetsTransitively(directSetIds, pathFilter));
    }
    return builder.build();
  }

  /** Returns the artifacts transitively associated with each top-level target. */
  public ImmutableListMultimap<Label, OutputArtifact> getPerTargetOutputArtifacts(
      Predicate<String> outputGroupFilter) {
    Set<String> directFileSets =
        outputGroupFileSets.asMap().entrySet().stream()
            .filter(e -> outputGroupFilter.test(e.getKey()))
            .flatMap(e -> e.getValue().stream())
            .collect(toImmutableSet());
    ImmutableListMultimap.Builder<Label, OutputArtifact> builder = ImmutableListMultimap.builder();
    for (String target : targetFileSets.keySet()) {
      Set<String> directSetIds =
          targetFileSets.get(target).stream()
              .filter(directFileSets::contains)
              .collect(toImmutableSet());
      builder.putAll(
          Label.create(target), traverseFileSetsTransitively(directSetIds, path -> true));
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
      allFiles.addAll(filter(fileSet.parsedOutputs, pathFilter));
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

  private static ImmutableList<OutputArtifact> filter(
      Collection<OutputArtifact> outputs, Predicate<String> pathFilter) {
    return outputs.stream()
        .filter(o -> pathFilter.test(o.getRelativePath()))
        .collect(toImmutableList());
  }

  private static ImmutableList<OutputArtifact> parseFiles(
      NamedSetOfFiles namedSet, String config, long startTimeMillis) {
    return namedSet.getFilesList().stream()
        .map(f -> OutputArtifactParser.parseArtifact(f, config, startTimeMillis))
        .filter(Objects::nonNull)
        .collect(toImmutableList());
  }
}
