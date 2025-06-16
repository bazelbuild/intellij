/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Interner;
import com.google.idea.blaze.base.command.buildresult.bepparser.BepParser;
import com.google.idea.blaze.base.command.buildresult.bepparser.BuildEventStreamProvider;
import com.google.idea.blaze.base.command.buildresult.bepparser.ParsedBepOutput;
import com.google.idea.blaze.base.run.testlogs.BlazeTestResults;
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs;
import java.io.File;

/**
 * A utility class that knows how to collect data from {@link BuildEventStreamProvider} in a use case specific way.
 */
public final class BuildResultParser {

  private static final String DEFAULT_OUTPUT_GROUP_NAME = "default";

  private BuildResultParser() { }

  /**
   * Parses the BEP stream and returns the corresponding {@link ParsedBepOutput}. May only be
   * called once on a given stream.
   *
   * <p>As BEP retrieval can be memory-intensive for large projects, implementations of
   * getBuildOutput may restrict parallelism for cases in which many builds are executed in parallel
   * (e.g. remote builds).
   */
  public static ParsedBepOutput getBuildOutput(
    BuildEventStreamProvider bepStream, Interner<String> stringInterner)
    throws GetArtifactsException {
    try {
      return BepParser.parseBepArtifacts(bepStream, stringInterner);
    }
    catch (BuildEventStreamProvider.BuildEventStreamException e) {
      BuildResultHelper.logger.error(e);
      throw new GetArtifactsException(String.format(
        "Failed to parse bep for build id: %s: %s", bepStream.getId(), e.getMessage()));
    }
  }

  /**
   * Parses the BEP stream and returns the corresponding {@link ParsedBepOutput}. May only be
   * called once on a given stream.
   *
   * <p>As BEP retrieval can be memory-intensive for large projects, implementations of
   * getBuildOutput may restrict parallelism for cases in which many builds are executed in parallel
   * (e.g. remote builds).
   */
  public static ParsedBepOutput.Legacy getBuildOutputForLegacySync(
    BuildEventStreamProvider bepStream, Interner<String> stringInterner)
    throws GetArtifactsException {
    try {
      return BepParser.parseBepArtifactsForLegacySync(bepStream, stringInterner);
    }
    catch (BuildEventStreamProvider.BuildEventStreamException e) {
      BuildResultHelper.logger.error(e);
      throw new GetArtifactsException(String.format(
        "Failed to parse bep for build id: %s: %s", bepStream.getId(), e.getMessage()));
    }
  }

  /**
   * Parses BEP stream and returns the corresponding {@link BlazeTestResults}. May
   * only be called once on a given stream.
   */
  public static BlazeTestResults getTestResults(BuildEventStreamProvider bepStream) throws GetArtifactsException {
    try {
      return BuildEventProtocolOutputReader.parseTestResults(bepStream);
    }
    catch (BuildEventStreamProvider.BuildEventStreamException e) {
      BuildResultHelper.logger.warn(e);
      throw new GetArtifactsException(
        String.format("Failed to parse bep for build id: %s", bepStream.getId()), e);
    }
  }

  /**
   * Parses the BEP stream and collects all local executable artifacts of the provided label in the default output
   * group. LocalFileArtifact.getLocalFiles is not used by this implementation to avoid the local artifact cache.
   *
   * <p>Returns a non-empty list of all artifacts or throws a GetArtifactsException.
   */
  public static ImmutableList<File> getExecutableArtifacts(
      BuildEventStreamProvider bepStream,
      Interner<String> stringInterner,
      String label)
      throws GetArtifactsException {
    final var parsedBepOutput = getBuildOutput(bepStream, stringInterner);

    final var result = BuildResult.fromExitCode(parsedBepOutput.buildResult());
    if (result.status != BuildResult.Status.SUCCESS) {
      throw new GetArtifactsException(
          String.format("Failed to parse bep for build id: %s", bepStream.getId()));
    }

    // manually find local artifacts in favour of LocalFileArtifact.getLocalFiles, to avoid the artifacts cache
    // the artifacts cache atm does not preserve the executable flag for files (and there might be other issues)
    final var artifacts =  BlazeBuildOutputs.fromParsedBepOutput(parsedBepOutput)
        .getOutputGroupTargetArtifacts(DEFAULT_OUTPUT_GROUP_NAME, label)
        .stream()
        .filter(LocalFileArtifact.class::isInstance)
        .map(LocalFileArtifact.class::cast)
        .map(LocalFileArtifact::getFile)
        .filter(File::canExecute)
        .collect(ImmutableList.toImmutableList());

    if (artifacts.isEmpty()) {
      throw new GetArtifactsException(
          String.format("No output artifacts found for build id: %s", bepStream.getId()));
    }

    return artifacts;
  }
}
