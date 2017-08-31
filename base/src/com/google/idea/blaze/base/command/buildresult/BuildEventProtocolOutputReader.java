/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.command.buildresult;

import static com.google.idea.common.guava.GuavaHelper.toImmutableList;
import static com.google.idea.common.guava.GuavaHelper.toImmutableSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.run.testlogs.BlazeTestResult;
import com.google.idea.blaze.base.run.testlogs.BlazeTestResult.TestStatus;
import com.google.idea.blaze.base.run.testlogs.BlazeTestResults;
import com.google.idea.blaze.base.util.UrlUtil;
import com.google.repackaged.devtools.build.lib.buildeventstream.BuildEventStreamProtos;
import com.google.repackaged.devtools.build.lib.buildeventstream.BuildEventStreamProtos.BuildEventId.NamedSetOfFilesId;
import com.google.repackaged.devtools.build.lib.buildeventstream.BuildEventStreamProtos.OutputGroup;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/** Utility methods for reading Blaze's build event procotol output, in proto form. */
public final class BuildEventProtocolOutputReader {

  private BuildEventProtocolOutputReader() {}

  /**
   * Reads all test results from a BEP-formatted {@link InputStream}.
   *
   * @throws IOException if the BEP {@link InputStream} is incorrectly formatted
   */
  public static BlazeTestResults parseTestResults(InputStream inputStream) throws IOException {
    Map<String, Kind> labelToTargetKind = new HashMap<>();
    ImmutableList.Builder<BlazeTestResult> results = ImmutableList.builder();
    BuildEventStreamProtos.BuildEvent event;
    while ((event = BuildEventStreamProtos.BuildEvent.parseDelimitedFrom(inputStream)) != null) {
      switch (event.getId().getIdCase()) {
        case TARGET_COMPLETED:
          String label = event.getId().getTargetCompleted().getLabel();
          Kind kind = parseTargetKind(event.getCompleted().getTargetKind());
          if (kind != null) {
            labelToTargetKind.put(label, kind);
          }
          continue;
        case TARGET_CONFIGURED:
          label = event.getId().getTargetConfigured().getLabel();
          kind = parseTargetKind(event.getConfigured().getTargetKind());
          if (kind != null) {
            labelToTargetKind.put(label, kind);
          }
          continue;
        case TEST_RESULT:
          label = event.getId().getTestResult().getLabel();
          results.add(parseTestResult(label, labelToTargetKind.get(label), event.getTestResult()));
          continue;
        default: // continue
      }
    }
    return BlazeTestResults.fromFlatList(results.build());
  }

  /** Convert BEP 'target_kind' to our internal format */
  @Nullable
  private static Kind parseTargetKind(String kind) {
    return kind.endsWith(" rule")
        ? Kind.fromString(kind.substring(0, kind.length() - " rule".length()))
        : null;
  }

  private static BlazeTestResult parseTestResult(
      String label, @Nullable Kind kind, BuildEventStreamProtos.TestResult testResult) {
    ImmutableSet<File> files =
        testResult
            .getTestActionOutputList()
            .stream()
            .map(file -> parseFile(file, path -> path.endsWith(".xml")))
            .filter(Objects::nonNull)
            .collect(toImmutableSet());
    return BlazeTestResult.create(
        Label.create(label), kind, convertTestStatus(testResult.getStatus()), files);
  }

  private static TestStatus convertTestStatus(BuildEventStreamProtos.TestStatus protoStatus) {
    if (protoStatus == BuildEventStreamProtos.TestStatus.UNRECOGNIZED) {
      // for forward-compatibility
      return TestStatus.NO_STATUS;
    }
    return TestStatus.valueOf(protoStatus.name());
  }

  /**
   * Reads all output files listed in the BEP output that satisfy the specified predicate.
   *
   * @throws IOException if the BEP output file is incorrectly formatted
   */
  static ImmutableList<File> parseAllOutputFilenames(
      InputStream inputStream, Predicate<String> fileFilter) throws IOException {
    ImmutableList.Builder<File> files = ImmutableList.builder();
    BuildEventStreamProtos.BuildEvent event;
    while ((event = BuildEventStreamProtos.BuildEvent.parseDelimitedFrom(inputStream)) != null) {
      files.addAll(parseFilenames(event, fileFilter));
    }
    return files.build();
  }

  /**
   * Reads all artifacts associated with the given target that satisfy the specified predicate.
   *
   * @throws IOException if the BEP output file is incorrectly formatted
   */
  static ImmutableList<File> parseArtifactsForTarget(
      InputStream inputStream, Label label, Predicate<String> fileFilter) throws IOException {
    Map<String, List<BuildEventStreamProtos.File>> fileSets = new HashMap<>();
    List<String> fileSetsForLabel = new ArrayList<>();
    BuildEventStreamProtos.BuildEvent event;
    while ((event = BuildEventStreamProtos.BuildEvent.parseDelimitedFrom(inputStream)) != null) {
      if (event.getId().hasNamedSet() && event.hasNamedSetOfFiles()) {
        fileSets.put(
            event.getId().getNamedSet().getId(), event.getNamedSetOfFiles().getFilesList());
      } else if (isTargetCompletedEvent(event, label)) {
        fileSetsForLabel.addAll(getTargetFileSets(event));
      }
    }
    return fileSetsForLabel
        .stream()
        .map(fileSets::get)
        .flatMap(List::stream)
        .map(file -> parseFile(file, fileFilter))
        .collect(toImmutableList());
  }

  private static boolean isTargetCompletedEvent(
      BuildEventStreamProtos.BuildEvent event, Label label) {
    return event.getId().hasTargetCompleted()
        && event.hasCompleted()
        && label.toString().equals(event.getId().getTargetCompleted().getLabel());
  }

  /** Returns all file set IDs associated with the given target completed event. */
  private static ImmutableList<String> getTargetFileSets(BuildEventStreamProtos.BuildEvent event) {
    if (!event.hasCompleted()) {
      return ImmutableList.of();
    }
    return event
        .getCompleted()
        .getOutputGroupList()
        .stream()
        .map(OutputGroup::getFileSetsList)
        .flatMap(List::stream)
        .map(NamedSetOfFilesId::getId)
        .collect(toImmutableList());
  }

  /**
   * If this is a NamedSetOfFiles event, reads all associated output files. Otherwise returns an
   * empty list.
   */
  private static ImmutableList<File> parseFilenames(
      BuildEventStreamProtos.BuildEvent event, Predicate<String> fileFilter) {
    if (!event.hasNamedSetOfFiles()) {
      return ImmutableList.of();
    }
    return event
        .getNamedSetOfFiles()
        .getFilesList()
        .stream()
        .map(f -> parseFile(f, fileFilter))
        .filter(Objects::nonNull)
        .collect(toImmutableList());
  }

  @Nullable
  private static File parseFile(BuildEventStreamProtos.File file, Predicate<String> fileFilter) {
    String filePath = UrlUtil.urlToFilePath(file.getUri());
    return filePath != null && fileFilter.test(filePath) ? new File(filePath) : null;
  }
}
