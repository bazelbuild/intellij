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

import com.google.idea.blaze.base.command.buildresult.BuildEventStreamProvider.BuildEventStreamException;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Build event protocol implementation to get build results.
 *
 * <p>The build even protocol (BEP for short) is a proto-based protocol used by bazel to communicate
 * build events.
 */
class BuildResultHelperBep implements BuildResultHelper {

  private static final Logger logger = Logger.getInstance(BuildResultHelperBep.class);
  private final File outputFile;

  BuildResultHelperBep() {
    outputFile = BuildEventProtocolUtils.createTempOutputFile();
  }

  @Override
  public List<String> getBuildFlags() {
    return BuildEventProtocolUtils.getBuildFlags(outputFile);
  }

  @Override
  public ParsedBepOutput getBuildOutput(Optional<String> completedBuildId)
      throws GetArtifactsException {
    try (InputStream inputStream = new BufferedInputStream(new FileInputStream(outputFile))) {
      return ParsedBepOutput.parseBepArtifacts(inputStream);
    } catch (IOException | BuildEventStreamException e) {
      logger.error(e);
      throw new GetArtifactsException(e.getMessage());
    }
  }

  @Override
  public BuildFlags getBlazeFlags(
      Optional<String> completedBuildId,
      Predicate<String> startupFlagsFilter,
      Predicate<String> cmdlineFlagsFilter)
      throws GetFlagssException {
    try (InputStream inputStream = new BufferedInputStream(new FileInputStream(outputFile))) {
      return BuildFlags.parseBep(inputStream, startupFlagsFilter, cmdlineFlagsFilter);
    } catch (IOException | BuildEventStreamException e) {
      throw new GetFlagssException(e.getMessage());
    }
  }

  @Override
  public void close() {
    if (!outputFile.delete()) {
      logger.warn("Could not delete BEP output file: " + outputFile);
    }
  }

  static class Provider implements BuildResultHelperProvider {

    @Override
    public Optional<BuildResultHelper> doCreate(Project project) {
      return Optional.of(new BuildResultHelperBep());
    }

    @Override
    public Optional<BuildResultHelper> doCreateForSync(Project project, BlazeInfo blazeInfo) {
      return Optional.of(new BuildResultHelperBep());
    }
  }
}
