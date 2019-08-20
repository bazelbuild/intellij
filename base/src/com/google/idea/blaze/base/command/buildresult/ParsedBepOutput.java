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
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
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
import java.util.stream.Stream;
import javax.annotation.Nullable;

/** A data class representing blaze's build event protocol (BEP) output for a build. */
public final class ParsedBepOutput {

  static ParsedBepOutput parseBepArtifacts(InputStream bepStream) throws IOException {
    BuildEventStreamProtos.BuildEvent event;
    Map<String, String> configIdToMnemonic = new HashMap<>();
    Set<String> topLevelFileSets = new HashSet<>();
    Map<String, FileSet.Builder> fileSets = new LinkedHashMap<>();
    ImmutableSetMultimap.Builder<String, String> targetToFileSets = ImmutableSetMultimap.builder();
    long startTimeMillis = 0L;

    while ((event = BuildEventStreamProtos.BuildEvent.parseDelimitedFrom(bepStream)) != null) {
      switch (event.getId().getIdCase()) {
        case CONFIGURATION:
          configIdToMnemonic.put(
              event.getId().getConfiguration().getId(), event.getConfiguration().getMnemonic());
          continue;
        case NAMED_SET:
          NamedSetOfFiles namedSet = event.getNamedSetOfFiles();
          fileSets.compute(
              event.getId().getNamedSet().getId(),
              (k, v) ->
                  v != null ? v.setNamedSet(namedSet) : FileSet.builder().setNamedSet(namedSet));
          continue;
        case TARGET_COMPLETED:
          String label = event.getId().getTargetCompleted().getLabel();
          String configId = event.getId().getTargetCompleted().getConfiguration().getId();

          event
              .getCompleted()
              .getOutputGroupList()
              .forEach(
                  o -> {
                    List<String> sets = getFileSets(o);
                    targetToFileSets.putAll(label, sets);
                    topLevelFileSets.addAll(sets);
                    for (String id : sets) {
                      fileSets.compute(
                          id,
                          (k, v) -> {
                            FileSet.Builder builder = (v != null) ? v : FileSet.builder();
                            return builder
                                .setConfigId(configId)
                                .addOutputGroups(ImmutableSet.of(o.getName()))
                                .addTargets(ImmutableSet.of(label));
                          });
                    }
                  });
          continue;
        case STARTED:
          startTimeMillis = event.getStarted().getStartTimeMillis();
          continue;
        default: // continue
      }
    }
    ImmutableMap<String, FileSet> filesMap =
        fillInTransitiveFileSetData(
            fileSets, topLevelFileSets, configIdToMnemonic, startTimeMillis);
    return new ParsedBepOutput(filesMap, targetToFileSets.build(), startTimeMillis);
  }

  private static List<String> getFileSets(BuildEventStreamProtos.OutputGroup group) {
    return group.getFileSetsList().stream()
        .map(NamedSetOfFilesId::getId)
        .collect(Collectors.toList());
  }

  /**
   * Only top-level targets have configuration mnemonic, producing target, and output group data
   * explicitly provided in BEP. This method fills in that data for the transitive closure.
   */
  private static ImmutableMap<String, FileSet> fillInTransitiveFileSetData(
      Map<String, FileSet.Builder> fileSets,
      Set<String> topLevelFileSets,
      Map<String, String> configIdToMnemonic,
      long startTimeMillis) {
    Queue<String> toVisit = Queues.newArrayDeque(topLevelFileSets);
    Set<String> visited = new HashSet<>(topLevelFileSets);
    while (!toVisit.isEmpty()) {
      String setId = toVisit.remove();
      FileSet.Builder fileSet = fileSets.get(setId);
      if (fileSet.namedSet == null) {
        continue;
      }
      fileSet.namedSet.getFileSetsList().stream()
          .map(NamedSetOfFilesId::getId)
          .filter(s -> !visited.contains(s))
          .forEach(
              child -> {
                fileSets.get(child).updateFromParent(fileSet);
                toVisit.add(child);
                visited.add(child);
              });
    }
    return fileSets.entrySet().stream()
        .filter(e -> e.getValue().isValid(configIdToMnemonic))
        .collect(
            toImmutableMap(
                Map.Entry::getKey, e -> e.getValue().build(configIdToMnemonic, startTimeMillis)));
  }

  /** A map from file set ID to file set, with the same ordering as the BEP stream. */
  private final ImmutableMap<String, FileSet> fileSets;

  /** The set of named file sets directly produced by each target. */
  private final SetMultimap<String, String> targetFileSets;

  final long syncStartTimeMillis;

  private ParsedBepOutput(
      ImmutableMap<String, FileSet> fileSets,
      ImmutableSetMultimap<String, String> targetFileSets,
      long syncStartTimeMillis) {
    this.fileSets = fileSets;
    this.targetFileSets = targetFileSets;
    this.syncStartTimeMillis = syncStartTimeMillis;
  }

  /** Returns all output artifacts of the build. */
  public ImmutableSet<OutputArtifact> getAllOutputArtifacts(Predicate<String> pathFilter) {
    return fileSets.values().stream()
        .map(s -> s.parsedOutputs)
        .flatMap(List::stream)
        .filter(o -> pathFilter.test(o.getRelativePath()))
        .collect(toImmutableSet());
  }

  /** Returns the set of artifacts directly produced by the given target. */
  public ImmutableSet<OutputArtifact> getDirectArtifactsForTarget(
      Label label, Predicate<String> pathFilter) {
    return targetFileSets.get(label.toString()).stream()
        .map(s -> fileSets.get(s).parsedOutputs)
        .flatMap(List::stream)
        .filter(o -> pathFilter.test(o.getRelativePath()))
        .collect(toImmutableSet());
  }

  public ImmutableList<OutputArtifact> getOutputGroupArtifacts(
      String outputGroup, Predicate<String> pathFilter) {
    return fileSets.values().stream()
        .filter(f -> f.outputGroups.contains(outputGroup))
        .map(f -> f.parsedOutputs)
        .flatMap(List::stream)
        .filter(o -> pathFilter.test(o.getRelativePath()))
        .collect(toImmutableList());
  }

  /**
   * Returns a map from artifact key to {@link BepArtifactData} for all artifacts reported during
   * the build.
   */
  public ImmutableMap<String, BepArtifactData> getFullArtifactData() {
    return fileSets.values().stream()
        .flatMap(FileSet::toPerArtifactData)
        .collect(toImmutableMap(d -> d.artifact.getKey(), d -> d, BepArtifactData::update));
  }

  private static ImmutableList<OutputArtifact> parseFiles(
      NamedSetOfFiles namedSet, String config, long startTimeMillis) {
    return namedSet.getFilesList().stream()
        .map(f -> OutputArtifactParser.parseArtifact(f, config, startTimeMillis))
        .filter(Objects::nonNull)
        .collect(toImmutableList());
  }

  private static class FileSet {
    private final ImmutableList<OutputArtifact> parsedOutputs;
    private final ImmutableSet<String> outputGroups;
    private final ImmutableSet<String> targets;

    FileSet(
        NamedSetOfFiles namedSet,
        String configuration,
        long startTimeMillis,
        Set<String> outputGroups,
        Set<String> targets) {
      this.parsedOutputs = parseFiles(namedSet, configuration, startTimeMillis);
      this.outputGroups = ImmutableSet.copyOf(outputGroups);
      this.targets = ImmutableSet.copyOf(targets);
    }

    static Builder builder() {
      return new Builder();
    }

    private Stream<BepArtifactData> toPerArtifactData() {
      return parsedOutputs.stream().map(a -> new BepArtifactData(a, outputGroups, targets));
    }

    private static class Builder {
      @Nullable NamedSetOfFiles namedSet;
      @Nullable String configId;
      final Set<String> outputGroups = new HashSet<>();
      final Set<String> targets = new HashSet<>();

      Builder updateFromParent(Builder parent) {
        configId = parent.configId;
        outputGroups.addAll(parent.outputGroups);
        targets.addAll(parent.outputGroups);
        return this;
      }

      Builder setNamedSet(NamedSetOfFiles namedSet) {
        this.namedSet = namedSet;
        return this;
      }

      Builder setConfigId(String configId) {
        this.configId = configId;
        return this;
      }

      Builder addOutputGroups(Set<String> outputGroups) {
        this.outputGroups.addAll(outputGroups);
        return this;
      }

      Builder addTargets(Set<String> targets) {
        this.targets.addAll(targets);
        return this;
      }

      boolean isValid(Map<String, String> configIdToMnemonic) {
        return namedSet != null && configId != null && configIdToMnemonic.get(configId) != null;
      }

      FileSet build(Map<String, String> configIdToMnemonic, long startTimeMillis) {
        return new FileSet(
            namedSet, configIdToMnemonic.get(configId), startTimeMillis, outputGroups, targets);
      }
    }
  }
}
