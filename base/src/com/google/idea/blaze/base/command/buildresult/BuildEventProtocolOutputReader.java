/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.run.testlogs.BlazeTestResult;
import com.google.idea.blaze.base.run.testlogs.BlazeTestResult.TestStatus;
import com.google.idea.blaze.base.run.testlogs.BlazeTestResults;
import com.intellij.util.io.URLUtil;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/** Utility methods for reading Blaze's build event procotol output, in proto form. */
public final class BuildEventProtocolOutputReader {

  private BuildEventProtocolOutputReader() {}

  /**
   * Returns all output artifacts listed in the BEP output that satisfy the specified predicate.
   *
   * @throws IOException if the BEP output file is incorrectly formatted
   */
  public static ImmutableList<OutputArtifact> parseAllOutputs(
      InputStream inputStream, Predicate<String> fileFilter) throws IOException {
    ParsedBepOutput output = ParsedBepOutput.parseBepArtifacts(inputStream);
    return output.getAllOutputArtifacts(fileFilter).asList();
  }

  /**
   * Returns all artifacts associated with the given target that satisfy the specified predicate.
   *
   * @throws IOException if the BEP output file is incorrectly formatted
   */
  public static ImmutableList<OutputArtifact> parseArtifactsForTarget(
      InputStream inputStream, Label label, Predicate<String> fileFilter) throws IOException {
    ParsedBepOutput output = ParsedBepOutput.parseBepArtifacts(inputStream);
    return output.getArtifactsForTarget(label, fileFilter).asList();
  }

  /**
   * Returns all output artifacts belonging to the given output group(s).
   *
   * @throws IOException if the BEP output file is incorrectly formatted
   */
  public static ImmutableList<OutputArtifact> parseAllArtifactsInOutputGroups(
      InputStream inputStream, Collection<String> outputGroups, Predicate<String> fileFilter)
      throws IOException {
    ParsedBepOutput output = ParsedBepOutput.parseBepArtifacts(inputStream);
    return output.getArtifactsForOutputGroups(outputGroups, fileFilter).asList();
  }

  /**
   * Returns all test results from a BEP-formatted {@link InputStream}.
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
        ? Kind.fromRuleName(kind.substring(0, kind.length() - " rule".length()))
        : null;
  }

  private static BlazeTestResult parseTestResult(
      String label, @Nullable Kind kind, BuildEventStreamProtos.TestResult testResult) {
    ImmutableSet<OutputArtifact> files =
        testResult.getTestActionOutputList().stream()
            .map(file -> parseTestFile(file, path -> path.endsWith(".xml")))
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

  /** TODO(b/118636150): don't assume test outputs are local files. */
  @Nullable
  private static OutputArtifact parseTestFile(
      BuildEventStreamProtos.File file, Predicate<String> fileFilter) {
    String uri = file.getUri();
    if (uri == null || !uri.startsWith(URLUtil.FILE_PROTOCOL)) {
      return null;
    }
    try {
      File f = new File(new URI(uri));
      return fileFilter.test(f.getPath()) ? new LocalFileOutputArtifact(f) : null;
    } catch (URISyntaxException | IllegalArgumentException e) {
      return null;
    }
  }
}
