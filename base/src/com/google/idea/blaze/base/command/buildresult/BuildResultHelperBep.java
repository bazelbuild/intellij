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

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.model.primitives.Label;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
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
  private final Predicate<String> fileFilter;

  BuildResultHelperBep(Predicate<String> fileFilter) {
    this.fileFilter = fileFilter;
    outputFile = BuildEventProtocolUtils.createTempOutputFile();
  }

  @Override
  public List<String> getBuildFlags() {
    return BuildEventProtocolUtils.getBuildFlags(outputFile);
  }

  @Override
  public ImmutableList<OutputArtifact> getBuildArtifacts() throws GetArtifactsException {
    return readResult(
        input -> BuildEventProtocolOutputReader.parseAllOutputFilenames(input, fileFilter));
  }

  @Override
  public ImmutableList<OutputArtifact> getBuildArtifactsForTarget(Label target)
      throws GetArtifactsException {
    return readResult(
        input -> BuildEventProtocolOutputReader.parseArtifactsForTarget(input, target, fileFilter));
  }

  @Override
  public ImmutableList<OutputArtifact> getArtifactsForOutputGroups(Collection<String> outputGroups)
      throws GetArtifactsException {
    return readResult(
        input ->
            BuildEventProtocolOutputReader.parseAllOutputGroupFilenames(
                input, outputGroups, fileFilter));
  }

  private <V> V readResult(BepReader<V> readAction) throws GetArtifactsException {
    try (InputStream inputStream = new BufferedInputStream(new FileInputStream(outputFile))) {
      return readAction.read(inputStream);
    } catch (IOException e) {
      logger.error(e);
      throw new GetArtifactsException(e.getMessage());
    }
  }

  @Override
  public void close() {
    if (!outputFile.delete()) {
      logger.warn("Could not delete BEP output file: " + outputFile);
    }
  }

  private interface BepReader<V> {
    V read(InputStream inputStream) throws IOException;
  }

  static class Provider implements BuildResultHelperProvider {

    @Override
    public Optional<BuildResultHelper> createForFiles(
        Project project, Predicate<String> fileFilter) {
      return Optional.of(new BuildResultHelperBep(fileFilter));
    }

    @Override
    public Optional<BuildResultHelper> createForFilesForSync(
        Project project, BlazeInfo blazeInfo, Predicate<String> fileFilter) {
      return Optional.of(new BuildResultHelperBep(fileFilter));
    }
  }
}
